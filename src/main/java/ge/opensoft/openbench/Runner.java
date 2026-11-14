package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

public class Runner {
    public record Completed(int code, byte[] stdout, byte[] stderr) {}

    @FunctionalInterface
    public interface Commands {
        Completed execute(List<String> arguments, byte[] input, double timeout) throws Exception;
    }

    private final Commands commands;
    private final DoubleSupplier clock;

    public Runner() {
        this(Runner::process, () -> System.nanoTime() / 1e9);
    }

    public Runner(Commands commands, DoubleSupplier clock) {
        this.commands = commands;
        this.clock = clock;
    }

    public static Completed process(List<String> arguments, byte[] input, double timeout)
            throws Exception {
        if (timeout <= 0) throw new TimeoutException("Command deadline exhausted");
        Process process = new ProcessBuilder(arguments).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> stdout = executor.submit(() -> process.getInputStream().readAllBytes());
            Future<byte[]> stderr = executor.submit(() -> process.getErrorStream().readAllBytes());
            Future<?> writer =
                    executor.submit(() -> {
                        try (var stream = process.getOutputStream()) {
                            stream.write(input);
                        }
                        return null;
                    });
            try {
                if (!process.waitFor((long) (timeout * 1e9), TimeUnit.NANOSECONDS))
                    throw new TimeoutException("Command deadline exhausted");
                writer.get();
                return new Completed(process.exitValue(), stdout.get(), stderr.get());
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    public Engine.Author commandGenerator(List<String> arguments) {
        if (arguments == null
                || arguments.isEmpty()
                || arguments.stream().anyMatch(value -> value == null || value.isEmpty()))
            throw new IllegalArgumentException(
                    "Generator command must be a nonempty argument list");
        var command = List.copyOf(arguments);
        return (spec, previous, feedback, timeout, allowance) -> {
            var response =
                    commands.execute(
                            command,
                            bytes(
                                    map(
                                            "spec",
                                            spec,
                                            "previous",
                                            previous,
                                            "feedback",
                                            feedback,
                                            "allowance_microunits",
                                            allowance)),
                            timeout);
            if (response.code != 0)
                throw new IOException("Generator exited with status " + response.code);
            return MAPPER.readValue(response.stdout, Object.class);
        };
    }

    double remaining(double deadline) throws TimeoutException {
        double value = deadline - clock.getAsDouble();
        if (value <= 0) throw new TimeoutException("Run deadline exhausted");
        return value;
    }

    private Completed execute(List<String> command, double deadline) throws Exception {
        return commands.execute(command, new byte[0], remaining(deadline));
    }

    public Map<String, Object> dockerGrade(Map<String, Object> candidate, double timeout)
            throws Exception {
        double deadline = clock.getAsDouble() + timeout;
        var files = Bundle.render(candidate);
        String tag = "openbench-" + UUID.randomUUID().toString().replace("-", "");
        var checks = new LinkedHashMap<String, Object>();
        Path root = Files.createTempDirectory("openbench-");
        try {
            Bundle.writeTree(root.resolve("task"), files);
            Path reference = Files.createDirectory(root.resolve("reference"));
            Files.write(reference.resolve("openbench.jar"), files.get("tests/openbench.jar"));
            try {
                for (String kind : List.of("environment", "tests")) {
                    var built =
                            execute(
                                    List.of(
                                            "docker",
                                            "build",
                                            "--network=none",
                                            "-q",
                                            "-t",
                                            tag + "-" + kind,
                                            root.resolve("task").resolve(kind).toString()),
                                    deadline);
                    if (built.code != 0)
                        throw new IOException(
                                "Docker "
                                        + kind
                                        + " image build failed: "
                                        + new String(built.stderr, StandardCharsets.UTF_8));
                }
                Object oracle;
                String name = tag + "-reference";
                try {
                    var command = container(name);
                    command.add(
                            "--tmpfs=/artifacts:rw,noexec,nosuid,size=16m,uid=65534,gid=65534,mode=700");
                    command.addAll(
                            List.of(
                                    "--mount",
                                    "type=bind,src=" + reference + ",dst=/reference,readonly",
                                    tag + "-environment",
                                    "java",
                                    "-jar",
                                    "/reference/openbench.jar",
                                    "reference",
                                    "/workspace",
                                    "/artifacts/answer.json"));
                    var evaluated = execute(command, deadline);
                    if (evaluated.code != 0)
                        throw new IOException(
                                "Reference evaluator failed: "
                                        + new String(evaluated.stderr, StandardCharsets.UTF_8));
                    oracle = MAPPER.readValue(evaluated.stdout, Object.class);
                } finally {
                    commands.execute(List.of("docker", "rm", "-f", name), new byte[0], 10);
                }
                for (String label :
                        List.of("empty_rejected", "oracle_accepted", "mutation_rejected")) {
                    Path artifacts = root.resolve(label);
                    Files.createDirectory(artifacts);
                    write(
                            artifacts.resolve("answer.json"),
                            label.equals("oracle_accepted")
                                    ? oracle
                                    : label.equals("empty_rejected")
                                            ? map()
                                            : map("unexpected", true));
                    name = tag + "-" + label;
                    try {
                        var command = container(name);
                        command.addAll(
                                List.of(
                                        "--mount",
                                        "type=bind,src=" + artifacts + ",dst=/artifacts,readonly",
                                        tag + "-tests"));
                        var measured = execute(command, deadline);
                        String reward = new String(measured.stdout, StandardCharsets.UTF_8).strip();
                        if (!(measured.code == 0 && reward.equals("1")
                                || measured.code == 1 && reward.equals("0")))
                            throw new IOException(
                                    "Verifier did not produce a valid binary measurement: "
                                            + label);
                        checks.put(
                                label, measured.code == (label.equals("oracle_accepted") ? 0 : 1));
                    } finally {
                        commands.execute(List.of("docker", "rm", "-f", name), new byte[0], 10);
                    }
                }
            } finally {
                commands.execute(
                        List.of(
                                "docker",
                                "image",
                                "rm",
                                "-f",
                                tag + "-environment",
                                tag + "-tests"),
                        new byte[0],
                        10);
            }
        } finally {
            Bundle.deleteTree(root);
        }
        return map("digest", Bundle.treeDigest(files), "checks", checks);
    }

    private static ArrayList<String> container(String name) {
        return new ArrayList<>(
                List.of(
                        "docker",
                        "run",
                        "--rm",
                        "--name",
                        name,
                        "--network=none",
                        "--read-only",
                        "--cap-drop=ALL",
                        "--security-opt=no-new-privileges",
                        "--pids-limit=64",
                        "--memory=256m",
                        "--cpus=1",
                        "--tmpfs=/tmp:rw,exec,nosuid,nodev,size=32m,mode=1777"));
    }
}
