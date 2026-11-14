package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.gzip.*;
import org.springframework.boot.system.ApplicationHome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipFile;

public final class Bundle {
    private Bundle() {}

    public static Map<String, byte[]> render(Map<String, Object> candidate) throws Exception {
        var spec = object(candidate.get("spec"));
        String profile = (String) spec.get("profile");
        Path runtime =
                Path.of(
                        System.getProperty(
                                "openbench.runtimeJar",
                                Objects.toString(
                                        new ApplicationHome(Application.class).getSource())));
        byte[] jar = Files.readAllBytes(runtime);
        var files = new TreeMap<String, byte[]>();
        try (var archive = new ZipFile(runtime.toFile())) {
            var dependency =
                    archive.stream()
                            .filter(entry ->
                                    entry.getName().startsWith("BOOT-INF/lib/sqlite-jdbc-")
                                            && entry.getName().endsWith(".jar"))
                            .findFirst()
                            .orElseThrow(
                                    () -> new IOException("Packaged SQLite driver is missing"));
            try (var stream = archive.getInputStream(dependency)) {
                files.put("environment/sqlite-jdbc.jar", stream.readAllBytes());
            }
        }
        files.put("tests/openbench.jar", jar);
        files.put(
                "environment/graph.json",
                bytes(
                        map(
                                "nodes",
                                candidate.get("nodes"),
                                "modulus",
                                object(spec.get("level")).get("modulus"))));
        files.put(
                "environment/Dockerfile",
                ("FROM eclipse-temurin:25-jdk\n"
                                + "WORKDIR /workspace\n"
                                + "COPY . .\n"
                                + "RUN mkdir /artifacts && chown 65534:65534 /artifacts\n"
                                + "USER 65534:65534\n"
                                + "CMD [\"sleep\", \"infinity\"]\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "tests/Dockerfile",
                ("FROM eclipse-temurin:25-jre\n"
                                + "WORKDIR /verifier\n"
                                + "COPY . .\n"
                                + "USER 65534:65534\n"
                                + "ENTRYPOINT [\"java\", \"-jar\", \"openbench.jar\", \"verify\","
                                + " \"expected.json\", \"/artifacts/answer.json\"]\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put("tests/expected.json", bytes(Design.solve(candidate)));
        String source;
        if (profile.equals("chronicle-fold")) {
            files.put("environment/cases.json", bytes(candidate.get("cases")));
            source = "cases.json contains the case array.";
        } else if (profile.equals("partition-weave")) {
            files.put(
                    "environment/cases.jsonl",
                    (String.join(
                                            "\n",
                                            objects(candidate.get("cases")).stream()
                                                    .map(Json::canonical)
                                                    .toList())
                                    + "\n")
                            .getBytes(StandardCharsets.UTF_8));
            source = "cases.jsonl contains one case object per line.";
        } else {
            Path temporary = Files.createTempFile("openbench-cases-", ".sqlite");
            try {
                try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary);
                        var statement = connection.createStatement()) {
                    statement.execute(
                            "CREATE TABLE cases (id TEXT PRIMARY KEY, input INTEGER NOT NULL)");
                    try (var insert =
                            connection.prepareStatement("INSERT INTO cases VALUES (?,?)")) {
                        for (var sample : objects(candidate.get("cases"))) {
                            insert.setString(1, (String) sample.get("id"));
                            insert.setLong(2, integer(sample.get("input")));
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                files.put("environment/cases.sqlite", Files.readAllBytes(temporary));
            } finally {
                Files.deleteIfExists(temporary);
            }
            source = "cases.sqlite contains a cases(id TEXT, input INTEGER) table.";
        }
        files.put(
                "instruction.md",
                ("Reconstruct the "
                                + spec.get("domain")
                                + " transformation results. "
                                + source
                                + "\n"
                                + "For each case, bind input to its integer input value. Read"
                                + " graph.json nodes in order. For each node take the values of its"
                                + " depends identifiers, apply op (sum, maximum, minimum or bitwise"
                                + " XOR), then compute (value * factor + offset) modulo modulus."
                                + " All arithmetic uses integers. Write /artifacts/answer.json as"
                                + " an object mapping every case ID to an object mapping every node"
                                + " ID to its value. Include every case and node exactly once, with"
                                + " no extra keys. The verifier compares values, independent of key"
                                + " ordering. You may use any tools available in the agent image."
                                + " The case data and all transformation rules are public.\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "task.toml",
                ("version = \"openbench-2\"\n"
                                + "artifacts = [\"/artifacts/answer.json\"]\n"
                                + "[metadata]\n"
                                + "domain = \""
                                + spec.get("domain")
                                + "\"\nprofile = \""
                                + profile
                                + "\"\nlevel = \""
                                + object(spec.get("level")).get("name")
                                + "\"\n"
                                + "difficulty_status = \"uncalibrated\"\n"
                                + "[agent]\n"
                                + "timeout_sec = 300\n"
                                + "[verifier]\n"
                                + "timeout_sec = 30\n")
                        .getBytes(StandardCharsets.UTF_8));
        return files;
    }

    public static String treeDigest(Map<String, byte[]> files) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var entry : new TreeMap<>(files).entrySet()) {
            digest.update(bytes(entry.getKey()));
            digest.update((byte) 0);
            digest.update(bytes(entry.getValue().length));
            digest.update((byte) 0);
            digest.update(entry.getValue());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void writeTree(Path root, Map<String, byte[]> files) throws IOException {
        Files.createDirectories(root.toAbsolutePath().getParent());
        Files.createDirectory(root);
        for (var entry : files.entrySet()) {
            Path relative = Path.of(entry.getKey());
            if (relative.isAbsolute()
                    || !relative.normalize().equals(relative)
                    || relative.startsWith(".."))
                throw new IllegalArgumentException("Task paths must stay inside the task tree");
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
    }

    public static Map<String, byte[]> readTree(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root))
            throw new IllegalArgumentException("Expected a real task directory");
        var files = new TreeMap<String, byte[]>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path))
                    throw new IllegalArgumentException("Task links are forbidden");
                if (Files.isRegularFile(path))
                    files.put(
                            root.relativize(path).toString().replace(File.separatorChar, '/'),
                            Files.readAllBytes(path));
            }
        }
        return files;
    }

    public static Map<String, Object> pack(Path root, Path destination) throws Exception {
        var manifest = object(read(root.resolve("result.json")));
        var files = readTree(root.resolve("accepted"));
        String fingerprint = treeDigest(files);
        if (!"accepted".equals(manifest.get("status"))
                || !fingerprint.equals(manifest.get("tree_sha256")))
            throw new IllegalArgumentException("Only the unchanged accepted task can be packaged");
        var parameters = new GzipParameters();
        parameters.setModificationTime(0);
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW);
                var compressed = new GzipCompressorOutputStream(output, parameters);
                var archive = new TarArchiveOutputStream(compressed)) {
            archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (var entry : files.entrySet()) {
                var member = new TarArchiveEntry(entry.getKey());
                member.setSize(entry.getValue().length);
                member.setMode(0644);
                member.setModTime(0);
                archive.putArchiveEntry(member);
                archive.write(entry.getValue());
                archive.closeArchiveEntry();
            }
        }
        return map(
                "path",
                destination.toString(),
                "tree_sha256",
                fingerprint,
                "archive_sha256",
                digest(Files.readAllBytes(destination)));
    }

    static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
