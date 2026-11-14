package ge.opensoft.openbench;

import static ge.opensoft.openbench.DesignTest.spec;
import static ge.opensoft.openbench.Json.*;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

class EngineTest {
    @TempDir Path root;

    static Map<String, Object> grade(Map<String, Object> candidate, double timeout)
            throws Exception {
        return map(
                "digest",
                Bundle.treeDigest(Bundle.render(candidate)),
                "checks",
                map("empty_rejected", true, "oracle_accepted", true, "mutation_rejected", true));
    }

    @Test
    void retryPersistenceIsolationAndDeterministicArchives() throws Exception {
        AtomicInteger count = new AtomicInteger();
        Engine.Author author =
                (s, previous, feedback, timeout, budget) -> {
                    if (count.incrementAndGet() == 2) {
                        assertEquals(map(), previous);
                        assertFalse(feedback.isEmpty());
                    }
                    return map(
                            "candidate",
                            count.get() == 1 ? map() : Design.generate(s),
                            "cost_microunits",
                            3);
                };
        Path run = root.resolve("run");
        var result = new Engine().run(spec(), run, author, EngineTest::grade, 3, 600, 100);
        assertEquals("accepted", result.get("status"));
        assertEquals(6L, result.get("spent_microunits"));
        assertEquals(2, objects(result.get("attempts")).size());
        assertEquals(map(), read(run.resolve("attempt-001/candidate.json")));
        Path first = root.resolve("one.tar.gz"), second = root.resolve("two.tar.gz");
        var packed = Bundle.pack(run, first);
        var again = Bundle.pack(run, second);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        assertEquals(packed.get("archive_sha256"), again.get("archive_sha256"));
        assertEquals(result.get("tree_sha256"), packed.get("tree_sha256"));
        var files = Bundle.readTree(run.resolve("accepted"));
        var names = new ArrayList<String>();
        try (var tar =
                new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(first)))) {
            for (var entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                names.add(entry.getName());
                assertEquals(0, entry.getLastModifiedDate().getTime());
                assertEquals(0644, entry.getMode());
                assertEquals(0, entry.getLongUserId());
                assertEquals(0, entry.getLongGroupId());
                assertArrayEquals(files.get(entry.getName()), tar.readAllBytes());
            }
        }
        assertEquals(new ArrayList<>(files.keySet()), names);
        assertThrows(FileAlreadyExistsException.class, () -> Bundle.pack(run, first));
        Files.writeString(run.resolve("accepted/extra.txt"), "changed");
        assertThrows(
                IllegalArgumentException.class, () -> Bundle.pack(run, root.resolve("bad.tar.gz")));
    }

    @Test
    void graderCannotMutateRetryCandidate() throws Exception {
        var authored = new ArrayList<Object>();
        AtomicInteger count = new AtomicInteger();
        Engine.Author author =
                (s, previous, feedback, timeout, allowance) -> {
                    if (previous != null)
                        assertEquals(canonical(authored.getLast()), canonical(previous));
                    var candidate = Design.generate(s);
                    authored.add(candidate);
                    return map("candidate", candidate, "cost_microunits", 1);
                };
        Engine.Grader grader =
                (candidate, timeout) -> {
                    var measured = grade(candidate, timeout);
                    if (count.incrementAndGet() == 1) {
                        objects(candidate.get("nodes")).clear();
                        object(measured.get("checks")).put("oracle_accepted", false);
                    }
                    return measured;
                };
        var result = new Engine().run(spec(), root.resolve("isolated"), author, grader, 2, 600, 2);
        assertEquals("accepted", result.get("status"));
        assertEquals(2L, result.get("spent_microunits"));
        assertEquals(
                canonical(authored.getFirst()),
                canonical(read(root.resolve("isolated/attempt-001/candidate.json"))));
    }

    @Test
    void failuresBudgetsAndTimeoutsArePersisted() throws Exception {
        Engine.Grader bad =
                (candidate, timeout) -> {
                    var report = grade(candidate, timeout);
                    object(report.get("checks")).put("oracle_accepted", false);
                    return report;
                };
        var exhausted =
                new Engine()
                        .run(spec(), root.resolve("exhausted"), Engine::builtin, bad, 2, 600, 0);
        assertEquals("exhausted", exhausted.get("status"));
        assertFalse(Files.exists(root.resolve("exhausted/accepted")));
        int index = 0;
        for (Object response :
                Arrays.asList(
                        null,
                        map(),
                        map("candidate", map(), "cost_microunits", -1),
                        map("candidate", map(), "cost_microunits", true)))
            assertEquals(
                    "error",
                    new Engine()
                            .run(
                                    spec(),
                                    root.resolve("response-" + (index++)),
                                    (s, p, f, t, b) -> response,
                                    EngineTest::grade,
                                    1,
                                    600,
                                    0)
                            .get("status"));
        for (Object response :
                Arrays.asList(
                        null,
                        map(),
                        map("digest", "wrong", "checks", map()),
                        map(
                                "digest",
                                Bundle.treeDigest(Bundle.render(Design.generate(spec()))),
                                "checks",
                                map()),
                        map(
                                "digest",
                                Bundle.treeDigest(Bundle.render(Design.generate(spec()))),
                                "checks",
                                map(
                                        "empty_rejected",
                                        true,
                                        "oracle_accepted",
                                        1,
                                        "mutation_rejected",
                                        true))))
            assertEquals(
                    "error",
                    new Engine()
                            .run(
                                    spec(),
                                    root.resolve("grade-" + (index++)),
                                    Engine::builtin,
                                    (c, t) -> response,
                                    1,
                                    600,
                                    0)
                            .get("status"));
        var cost =
                new Engine()
                        .run(
                                spec(),
                                root.resolve("budget"),
                                (s, p, f, t, b) ->
                                        map("candidate", Design.generate(s), "cost_microunits", 4),
                                EngineTest::grade,
                                1,
                                600,
                                3);
        assertEquals("budget_exhausted", cost.get("status"));
        assertTrue(Files.exists(root.resolve("budget/attempt-001/candidate.json")));
        for (Exception error : List.of(new TimeoutException(), new IOException("offline")))
            assertEquals(
                    error instanceof TimeoutException ? "timeout" : "error",
                    new Engine()
                            .run(
                                    spec(),
                                    root.resolve("error-" + (index++)),
                                    (s, p, f, t, b) -> {
                                        throw error;
                                    },
                                    EngineTest::grade,
                                    1,
                                    600,
                                    0)
                            .get("status"));
        for (double[] times :
                List.of(
                        new double[] {0, 2},
                        new double[] {0, 0, 2},
                        new double[] {0, 0, 0, 0, 2})) {
            AtomicInteger position = new AtomicInteger();
            var engine = new Engine(() -> times[position.getAndIncrement()]);
            assertEquals(
                    "timeout",
                    engine.run(
                                    spec(),
                                    root.resolve("clock-" + (index++)),
                                    Engine::builtin,
                                    EngineTest::grade,
                                    1,
                                    1,
                                    0)
                            .get("status"));
        }
        for (int attempts : new int[] {0, 101})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new Engine()
                                    .run(
                                            spec(),
                                            root.resolve("invalid"),
                                            Engine::builtin,
                                            EngineTest::grade,
                                            attempts,
                                            1,
                                            0));
        for (double seconds : new double[] {0, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new Engine()
                                    .run(
                                            spec(),
                                            root.resolve("invalid"),
                                            Engine::builtin,
                                            EngineTest::grade,
                                            1,
                                            seconds,
                                            0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Engine()
                                .run(
                                        spec(),
                                        root.resolve("invalid"),
                                        Engine::builtin,
                                        EngineTest::grade,
                                        1,
                                        1,
                                        -1));
        var weak = spec();
        object(weak.get("level")).put("min_cases", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Engine()
                                .run(
                                        weak,
                                        root.resolve("invalid"),
                                        Engine::builtin,
                                        EngineTest::grade,
                                        1,
                                        1,
                                        0));
        assertFalse(Files.exists(root.resolve("invalid")));
    }
}
