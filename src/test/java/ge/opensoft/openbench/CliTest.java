package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

class CliTest {
    @TempDir Path root;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @Test
    void commandLifecycleAndErrorCodes() throws Exception {
        var cli = new Cli(new Engine(), RunnerTest.fake("good", new ArrayList<>()));
        assertEquals(0, cli.run(new String[] {"catalog"}, output));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("level-100"));
        Path candidate = root.resolve("candidate.json");
        assertEquals(
                0,
                cli.run(
                        new String[] {
                            "generate", "--level", "level-16", "--output", candidate.toString()
                        },
                        output));
        assertEquals(0, cli.run(new String[] {"check", candidate.toString()}, output));
        var changed = object(read(candidate));
        changed.put("nodes", List.of());
        Files.write(candidate, Json.bytes(changed));
        assertEquals(1, cli.run(new String[] {"check", candidate.toString()}, output));
        assertEquals(
                2, cli.run(new String[] {"generate", "--output", candidate.toString()}, output));
        assertEquals(
                0,
                cli.run(
                        new String[] {
                            "run", "--attempts", "1", "--output", root.resolve("run").toString()
                        },
                        output));
        assertEquals(
                0,
                cli.run(
                        new String[] {
                            "pack",
                            root.resolve("run").toString(),
                            root.resolve("run.tar.gz").toString()
                        },
                        output));
        assertEquals(
                0,
                cli.run(
                        new String[] {
                            "reference", root.resolve("run/accepted/environment").toString()
                        },
                        output));
        Path artifact = root.resolve("answer.json");
        assertEquals(
                0,
                cli.run(
                        new String[] {
                            "reference",
                            root.resolve("run/accepted/environment").toString(),
                            artifact.toString()
                        },
                        output));
        assertEquals(read(root.resolve("run/accepted/tests/expected.json")), read(artifact));
        assertEquals(2, cli.run(new String[] {"reference"}, output));
        assertEquals(
                2,
                cli.run(
                        new String[] {
                            "reference",
                            root.resolve("run/accepted/environment").toString(),
                            artifact.toString()
                        },
                        output));
        Path expected = root.resolve("run/accepted/tests/expected.json");
        assertEquals(
                0,
                cli.run(new String[] {"verify", expected.toString(), expected.toString()}, output));
        assertEquals(
                1,
                cli.run(
                        new String[] {
                            "verify", expected.toString(), root.resolve("missing").toString()
                        },
                        output));
        assertEquals(
                1,
                new Cli(new Engine(), RunnerTest.fake("mismatch", new ArrayList<>()))
                        .run(
                                new String[] {
                                    "run",
                                    "--attempts",
                                    "1",
                                    "--output",
                                    root.resolve("exhausted").toString()
                                },
                                output));
        assertEquals(
                2,
                new Cli(new Engine(), RunnerTest.fake("build", new ArrayList<>()))
                        .run(
                                new String[] {
                                    "run",
                                    "--attempts",
                                    "1",
                                    "--output",
                                    root.resolve("error").toString()
                                },
                                output));
        assertEquals(
                2,
                cli.run(
                        new String[] {
                            "run",
                            "--output",
                            root.resolve("external").toString(),
                            "--generator",
                            "echo",
                            "{}"
                        },
                        output));
        Path trials = root.resolve("trials.json");
        write(
                trials,
                List.of(
                        map(
                                "model",
                                "A",
                                "level",
                                "level-1",
                                "accepted",
                                true,
                                "cost_microunits",
                                20)));
        assertEquals(0, cli.run(new String[] {"calibrate", trials.toString()}, output));
        for (String[] args :
                List.of(
                        new String[] {},
                        new String[] {"unknown"},
                        new String[] {"catalog", "extra"},
                        new String[] {"generate", "--output"},
                        new String[] {"generate", "--unknown", "x"},
                        new String[] {"generate", "--output", "a", "--output", "b"},
                        new String[] {
                            "generate",
                            "--level",
                            "level-01",
                            "--output",
                            root.resolve("bad").toString()
                        },
                        new String[] {"run", "--generator"}))
            assertEquals(2, cli.run(args, output));
    }

    @Test
    void calibrationGroupsAndConfidenceIntervals() {
        var trials = new ArrayList<Object>();
        for (int i = 0; i < 10; i++)
            trials.add(
                    map(
                            "model",
                            "A",
                            "level",
                            "level-1",
                            "accepted",
                            i % 2 == 0,
                            "cost_microunits",
                            3));
        var row = Calibration.summarize(trials).getFirst();
        assertEquals(.5, row.get("acceptance_rate"));
        assertEquals(30L, row.get("cost_microunits"));
        var interval = (List<?>) row.get("wilson_95");
        assertTrue((Double) interval.getFirst() > 0 && (Double) interval.getFirst() < .5);
        assertTrue((Double) interval.getLast() > .5 && (Double) interval.getLast() < 1);
        for (Object invalid :
                List.of(
                        List.of(),
                        map(),
                        List.of(map()),
                        List.of(
                                map(
                                        "model",
                                        "",
                                        "level",
                                        "level-1",
                                        "accepted",
                                        true,
                                        "cost_microunits",
                                        0)),
                        List.of(
                                map(
                                        "model",
                                        "A",
                                        "level",
                                        "level-1",
                                        "accepted",
                                        1,
                                        "cost_microunits",
                                        0)),
                        List.of(
                                map(
                                        "model",
                                        "A",
                                        "level",
                                        "level-1",
                                        "accepted",
                                        true,
                                        "cost_microunits",
                                        -1))))
            assertThrows(IllegalArgumentException.class, () -> Calibration.summarize(invalid));
    }

    @Test
    void packagedSpringBootCommandUsesJava25AndReturnsExitCodes() throws Exception {
        var originalExit = Application.exit;
        try {
            Application.exit = code -> assertEquals(0, code);
            Application.main(new String[] {"catalog"});
        } finally {
            Application.exit = originalExit;
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ArrayList<String>();
        command.add(java);
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:") && arg.contains("jacoco"))
                .forEach(command::add);
        command.addAll(List.of("-jar", System.getProperty("openbench.runtimeJar"), "catalog"));
        var result = Runner.process(command, new byte[0], 30);
        assertEquals(0, result.code(), new String(result.stderr(), StandardCharsets.UTF_8));
        assertTrue(
                new String(result.stdout(), StandardCharsets.UTF_8)
                        .contains("level-1 through level-100"));
        command.set(command.size() - 1, "unknown");
        assertEquals(2, Runner.process(command, new byte[0], 30).code());
    }
}
