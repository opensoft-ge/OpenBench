package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

class DesignTest {
    @TempDir Path root;

    static Map<String, Object> spec() {
        return spec("level-1", "chronicle-fold");
    }

    static Map<String, Object> spec(String level, String profile) {
        return Design.specification("lunar-shadow-reservation", profile, level, 42);
    }

    @Test
    void ladderReproducibilityAndIndependentEvaluator() throws Exception {
        Map<String, Object> previous = null;
        for (int rank = 1; rank <= 100; rank++) {
            var spec = spec("level-" + rank, "chronicle-fold");
            var level = object(spec.get("level"));
            if (previous != null)
                for (String field : List.of("min_nodes", "min_cases", "min_depth"))
                    assertTrue(integer(level.get(field)) > integer(previous.get(field)));
            var candidate = Design.generate(spec);
            assertEquals(List.of(), Design.validate(candidate, spec));
            assertEquals(candidate, Design.generate(spec));
            previous = level;
        }
        for (String profile : Design.PROFILES.keySet()) {
            var candidate = Design.generate(spec("level-16", profile));
            var files = Bundle.render(candidate);
            Path task = root.resolve(profile);
            Bundle.writeTree(task, files);
            assertEquals(Bundle.treeDigest(files), Bundle.treeDigest(Bundle.readTree(task)));
            assertEquals(Design.solve(candidate), Reference.evaluate(task.resolve("environment")));
            assertTrue(
                    files.keySet().stream()
                            .filter(n -> n.startsWith("environment/"))
                            .noneMatch(n -> n.contains("expected") || n.contains("openbench.jar")));
            assertTrue(Design.gradeAnswer(candidate, Design.solve(candidate)));
            assertFalse(Design.gradeAnswer(candidate, map()));
            Path answer = task.resolve("answer.json"),
                    expected = task.resolve("tests/expected.json");
            write(answer, Design.solve(candidate));
            assertEquals(1, Reference.verify(expected, answer));
            for (String content : List.of("{}", "null", "true", "NaN", "not-json")) {
                Files.writeString(answer, content);
                assertEquals(0, Reference.verify(expected, answer));
            }
            assertEquals(0, Reference.verify(expected, task.resolve("absent")));
        }
        assertNotEquals(
                digest(Design.generate(spec())),
                digest(Design.generate(spec("level-2", "chronicle-fold"))));
    }

    @Test
    void missingPackagedDriverFailsClosed() throws Exception {
        Path archive = root.resolve("empty.jar");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {}
        String original = System.getProperty("openbench.runtimeJar");
        try {
            System.setProperty("openbench.runtimeJar", archive.toString());
            assertThrows(java.io.IOException.class, () -> Bundle.render(Design.generate(spec())));
        } finally {
            System.setProperty("openbench.runtimeJar", original);
        }
    }

    @Test
    void handComputedAnswerAndNumericTypes() {
        var candidate = Design.generate(spec());
        candidate.put("cases", List.of(map("id", "known", "input", 7)));
        candidate.put(
                "nodes",
                List.of(
                        map(
                                "id",
                                "a",
                                "depends",
                                List.of("input"),
                                "op",
                                "sum",
                                "factor",
                                2,
                                "offset",
                                1),
                        map(
                                "id",
                                "b",
                                "depends",
                                List.of("a", "input"),
                                "op",
                                "max",
                                "factor",
                                1,
                                "offset",
                                0),
                        map(
                                "id",
                                "c",
                                "depends",
                                List.of("a", "input"),
                                "op",
                                "min",
                                "factor",
                                1,
                                "offset",
                                0),
                        map(
                                "id",
                                "d",
                                "depends",
                                List.of("a", "input"),
                                "op",
                                "xor",
                                "factor",
                                1,
                                "offset",
                                0)));
        assertEquals(
                map("known", map("a", 15L, "b", 15L, "c", 7L, "d", 8L)), Design.solve(candidate));
        var answer = Design.solve(candidate);
        object(answer.get("known")).put("a", 15.0);
        assertFalse(Design.gradeAnswer(candidate, answer));
        assertThrows(IllegalArgumentException.class, () -> canonical(Double.NaN));
    }

    @Test
    void malformedSpecificationsAndDesigns() {
        for (Object value :
                Arrays.asList(
                        null,
                        "level-0",
                        "level-01",
                        "Level-1",
                        "level--1",
                        "level-101",
                        "level-99999"))
            assertThrows(IllegalArgumentException.class, () -> Design.level(value));
        for (Object value : List.of(List.of(), "unknown")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Design.specification(value, "chronicle-fold", "level-1", 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Design.specification("lunar-shadow-reservation", value, "level-1", 1));
        }
        for (Object value : List.of(true, -1, 0x100000000L))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            Design.specification(
                                    "lunar-shadow-reservation",
                                    "chronicle-fold",
                                    "level-1",
                                    value));
        var spec = spec("level-16", "chronicle-fold");
        var original = Design.generate(spec);
        List<Consumer<Map<String, Object>>> mutations = new ArrayList<>();
        mutations.add(c -> c.put("spec", map()));
        mutations.add(c -> c.put("nodes", List.of()));
        mutations.add(c -> c.put("nodes", map()));
        mutations.add(c -> c.put("cases", List.of()));
        mutations.add(c -> c.put("cases", map()));
        mutations.add(c -> objects(c.get("nodes")).set(0, map()));
        for (Object value : List.of(List.of(), "input", "bad-id", "é"))
            mutations.add(c -> objects(c.get("nodes")).getFirst().put("id", value));
        for (Object value :
                List.of(
                        map(),
                        List.of(),
                        List.of("future"),
                        List.of(List.of()),
                        List.of("input", "input")))
            mutations.add(c -> objects(c.get("nodes")).getFirst().put("depends", value));
        mutations.add(c -> objects(c.get("nodes")).getFirst().put("op", List.of()));
        for (Object value : List.of(true, -1, 1009))
            mutations.add(c -> objects(c.get("nodes")).getFirst().put("offset", value));
        for (Object value : List.of(false, 0, 101))
            mutations.add(c -> objects(c.get("nodes")).getFirst().put("factor", value));
        mutations.add(c -> objects(c.get("cases")).set(0, map()));
        for (Object value : List.of(List.of(), "", "case-1"))
            mutations.add(c -> objects(c.get("cases")).getFirst().put("id", value));
        for (Object value : List.of(true, -1))
            mutations.add(c -> objects(c.get("cases")).getFirst().put("input", value));
        assertFalse(Design.validate(null, spec).isEmpty());
        for (var mutation : mutations) {
            var candidate = object(snapshot(original));
            mutation.accept(candidate);
            assertFalse(Design.validate(candidate, spec).isEmpty());
        }
        for (var node : objects(original.get("nodes"))) node.put("depends", List.of("input"));
        assertEquals(
                List.of(
                        "Increase dependency depth, fan-in or operator diversity to meet the"
                                + " level"),
                Design.validate(original, spec));
    }

    @Test
    void rejectUnsafeTaskTrees() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> Bundle.readTree(root.resolve("absent")));
        Path real = Files.createDirectory(root.resolve("real"));
        Files.createSymbolicLink(root.resolve("link"), real);
        assertThrows(IllegalArgumentException.class, () -> Bundle.readTree(root.resolve("link")));
        Files.createSymbolicLink(real.resolve("bad"), root.resolve("absent"));
        assertThrows(IllegalArgumentException.class, () -> Bundle.readTree(real));
        assertThrows(
                IllegalArgumentException.class,
                () -> Bundle.writeTree(root.resolve("escaped"), Map.of("../bad", new byte[0])));
        assertFalse(Files.exists(root.resolve("bad")));
    }
}
