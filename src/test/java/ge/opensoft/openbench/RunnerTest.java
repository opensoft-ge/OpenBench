package ge.opensoft.openbench;

import static ge.opensoft.openbench.DesignTest.spec;
import static ge.opensoft.openbench.Json.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;

class RunnerTest {
    static Runner.Completed completed(int code, String output) {
        return new Runner.Completed(code, output.getBytes(StandardCharsets.UTF_8), new byte[0]);
    }

    static Runner fake(String mode, List<List<String>> commands) {
        return new Runner(
                (args, input, timeout) -> {
                    commands.add(List.copyOf(args));
                    if (args.contains("build")) return completed(mode.equals("build") ? 1 : 0, "");
                    if (args.contains("run")) {
                        if (mode.equals("timeout")) throw new TimeoutException();
                        if (args.contains("reference"))
                            return completed(
                                    mode.equals("reference") ? 1 : 0,
                                    canonical(Design.solve(Design.generate(spec()))));
                        if (mode.equals("crash")) return completed(4, "bad");
                        boolean good =
                                args.get(args.indexOf("--name") + 1).contains("oracle_accepted");
                        if (mode.equals("mismatch")) good = !good;
                        return completed(good ? 0 : 1, good ? "1" : "0");
                    }
                    return completed(0, "");
                },
                () -> 0);
    }

    @Test
    void commandsAndContainerCleanup() throws Exception {
        var commands = new ArrayList<List<String>>();
        var runner = fake("good", commands);
        var result = runner.dockerGrade(Design.generate(spec()), 10);
        assertEquals(Set.of(true), new HashSet<>(object(result.get("checks")).values()));
        assertEquals(6, commands.stream().filter(c -> c.contains("--network=none")).count());
        assertEquals(4, commands.stream().filter(c -> c.contains("--cap-drop=ALL")).count());
        assertEquals(
                4,
                commands.stream()
                        .filter(c -> c.contains("--mount"))
                        .map(c -> c.get(c.indexOf("--mount") + 1))
                        .distinct()
                        .count());
        assertEquals(
                4,
                commands.stream()
                        .filter(c ->
                                c.size() > 2
                                        && c.subList(0, 3).equals(List.of("docker", "rm", "-f")))
                        .count());
        assertEquals(List.of("docker", "image", "rm"), commands.getLast().subList(0, 3));
        for (String mode : List.of("build", "reference", "timeout", "mismatch", "crash")) {
            commands.clear();
            Runner faulty = fake(mode, commands);
            if (mode.equals("mismatch"))
                assertEquals(
                        Set.of(false),
                        new HashSet<>(
                                object(
                                                faulty.dockerGrade(Design.generate(spec()), 10)
                                                        .get("checks"))
                                        .values()));
            else
                assertThrows(
                        Exception.class, () -> faulty.dockerGrade(Design.generate(spec()), 10));
            assertEquals(List.of("docker", "image", "rm"), commands.getLast().subList(0, 3));
        }
        var expired = new Runner((args, input, timeout) -> completed(0, ""), () -> 5);
        assertThrows(TimeoutException.class, () -> expired.dockerGrade(Design.generate(spec()), 0));
    }

    @Test
    void trustedProcessProtocolAndDeadline() throws Exception {
        var result =
                Runner.process(
                        List.of("/bin/sh", "-c", "cat; printf diagnostic >&2"),
                        "payload".getBytes(StandardCharsets.UTF_8),
                        10);
        assertEquals(0, result.code());
        assertEquals("payload", new String(result.stdout(), StandardCharsets.UTF_8));
        assertEquals("diagnostic", new String(result.stderr(), StandardCharsets.UTF_8));
        assertThrows(
                TimeoutException.class,
                () -> Runner.process(List.of("/bin/sh", "-c", "sleep 3"), new byte[0], 0.05));
        assertThrows(
                TimeoutException.class,
                () -> Runner.process(List.of("/bin/sh", "-c", "true"), new byte[0], 0));
        var runner = new Runner();
        assertTrue(runner.remaining(System.nanoTime() / 1e9 + 10) > 0);
        assertThrows(TimeoutException.class, () -> runner.remaining(0));
        var author =
                runner.commandGenerator(
                        List.of(
                                "/bin/sh",
                                "-c",
                                "cat >/dev/null; printf"
                                        + " '{\"candidate\":{},\"cost_microunits\":1}'"));
        assertEquals(
                map("candidate", map(), "cost_microunits", 1),
                author.generate(spec(), null, List.of(), 10, 100));
        for (List<String> command : Arrays.asList(null, List.<String>of(), List.of("")))
            assertThrows(IllegalArgumentException.class, () -> runner.commandGenerator(command));
        assertThrows(
                Exception.class,
                () ->
                        runner.commandGenerator(List.of("/bin/sh", "-c", "exit 2"))
                                .generate(spec(), null, List.of(), 10, 0));
    }
}
