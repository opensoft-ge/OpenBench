package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleSupplier;

public final class Engine {
    @FunctionalInterface
    public interface Author {
        Object generate(
                Map<String, Object> spec,
                Object previous,
                List<String> feedback,
                double timeout,
                long allowance)
                throws Exception;
    }

    @FunctionalInterface
    public interface Grader {
        Object grade(Map<String, Object> candidate, double timeout) throws Exception;
    }

    private final DoubleSupplier clock;

    public Engine() {
        this(() -> System.nanoTime() / 1e9);
    }

    public Engine(DoubleSupplier clock) {
        this.clock = clock;
    }

    public static Object builtin(
            Map<String, Object> spec,
            Object previous,
            List<String> feedback,
            double timeout,
            long allowance) {
        return map("candidate", Design.generate(spec), "cost_microunits", 0);
    }

    double remaining(double deadline) throws TimeoutException {
        double value = deadline - clock.getAsDouble();
        if (value <= 0) throw new TimeoutException("Run deadline exhausted");
        return value;
    }

    public static List<String> checkMeasurement(Object value, String expectedDigest) {
        if (!(value instanceof Map<?, ?> report) || !expectedDigest.equals(report.get("digest")))
            throw new IllegalArgumentException("Measurement does not identify this task tree");
        if (!(report.get("checks") instanceof Map<?, ?> checks)
                || !checks.keySet()
                        .equals(Set.of("empty_rejected", "oracle_accepted", "mutation_rejected")))
            throw new IllegalArgumentException("Measurement must include all required controls");
        if (checks.values().stream().anyMatch(v -> !(v instanceof Boolean)))
            throw new IllegalArgumentException("Every control must be a measured boolean");
        return checks.entrySet().stream()
                .filter(entry -> !((Boolean) entry.getValue()))
                .map(entry -> "Check failed: " + entry.getKey())
                .toList();
    }

    public Map<String, Object> run(
            Map<String, Object> spec,
            Path root,
            Author author,
            Grader grader,
            int attempts,
            double seconds,
            long budget)
            throws Exception {
        if (attempts < 1
                || attempts > 100
                || !Double.isFinite(seconds)
                || seconds <= 0
                || budget < 0)
            throw new IllegalArgumentException(
                    "Use 1 to 100 attempts, positive finite seconds and a nonnegative budget");
        var expected =
                Design.specification(
                        spec.get("domain"),
                        spec.get("profile"),
                        object(spec.get("level")).get("name"),
                        spec.get("seed"));
        if (!canonical(expected).equals(canonical(spec)))
            throw new IllegalArgumentException(
                    "Specification must match the canonical level contract");
        Files.createDirectories(root.toAbsolutePath().getParent());
        Files.createDirectory(root);
        var records = new ArrayList<Map<String, Object>>();
        var result =
                map(
                        "status",
                        "exhausted",
                        "attempts",
                        records,
                        "spent_microunits",
                        0,
                        "spec",
                        snapshot(expected));
        double deadline = clock.getAsDouble() + seconds;
        long spent = 0;
        Object previous = null;
        List<String> findings = List.of();
        Map<String, Object> record = new LinkedHashMap<>();
        try {
            for (int iteration = 1; iteration <= attempts; iteration++) {
                double timeout = remaining(deadline);
                Path path = root.resolve(String.format(Locale.ROOT, "attempt-%03d", iteration));
                Files.createDirectory(path);
                record = map("iteration", iteration);
                records.add(record);
                var response =
                        object(
                                author.generate(
                                        object(snapshot(expected)),
                                        snapshot(previous),
                                        new ArrayList<>(findings),
                                        timeout,
                                        budget - spent));
                if (!response.keySet().equals(Set.of("candidate", "cost_microunits")))
                    throw new IllegalArgumentException(
                            "Author response requires candidate and cost_microunits");
                long cost = integer(response.get("cost_microunits"));
                if (cost < 0) throw new IllegalArgumentException("Author cost must be nonnegative");
                spent = Math.addExact(spent, cost);
                Object candidate = response.get("candidate");
                write(path.resolve("candidate.json"), candidate);
                record.put("candidate_sha256", digest(candidate));
                if (spent > budget) {
                    result.put("status", "budget_exhausted");
                    break;
                }
                remaining(deadline);
                findings = Design.validate(candidate, expected);
                record.put("findings", new ArrayList<>(findings));
                if (findings.isEmpty()) {
                    var files = Bundle.render(object(candidate));
                    Bundle.writeTree(path.resolve("task"), files);
                    Object report = grader.grade(object(snapshot(candidate)), remaining(deadline));
                    write(path.resolve("grade.json"), report);
                    findings = checkMeasurement(report, Bundle.treeDigest(files));
                    remaining(deadline);
                    record.put("findings", new ArrayList<>(findings));
                    if (findings.isEmpty()) {
                        Bundle.writeTree(root.resolve("accepted"), files);
                        result.put("status", "accepted");
                        result.put("tree_sha256", Bundle.treeDigest(files));
                        break;
                    }
                }
                previous = snapshot(candidate);
            }
        } catch (TimeoutException error) {
            result.put("status", "timeout");
        } catch (Exception error) {
            record.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            result.put("status", "error");
        }
        result.put("spent_microunits", spent);
        write(root.resolve("result.json"), result);
        return result;
    }
}
