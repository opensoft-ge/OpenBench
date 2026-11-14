package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import java.util.*;

public final class Design {
    private Design() {}

    public static final Map<String, Object> DOMAINS =
            map(
                    "lunar-shadow-reservation",
                    "Allocate observatory power credits across moving shadow intervals.",
                    "acoustic-tile-custody",
                    "Reconcile calibration evidence for modular concert-hall reflectors.",
                    "mycelium-vault-rehydration",
                    "Rebuild humidity allotments for a dormant fungal archive.");
    public static final Map<String, Object> PROFILES =
            map(
                    "chronicle-fold",
                    "JSON batches with declared dependency ordering.",
                    "partition-weave",
                    "JSON line records with cross-partition dependencies.",
                    "checkpoint-mosaic",
                    "SQLite cases with replay checkpoints.");
    private static final List<String> OPS = List.of("sum", "max", "min", "xor");

    public static Map<String, Object> level(Object value) {
        String name = text(value);
        if (!name.matches("level-([1-9]|[1-9][0-9]|100)"))
            throw new IllegalArgumentException("Use level-1 through level-100");
        int rank = Integer.parseInt(name.substring(6));
        return map(
                "name",
                name,
                "rank",
                rank,
                "min_nodes",
                rank + 1,
                "min_depth",
                rank + 1,
                "min_cases",
                2 * rank + 1,
                "min_fanin",
                Math.min((rank - 1) / 4 + 1, 8),
                "min_operators",
                Math.min((rank - 1) / 3 + 1, 4),
                "modulus",
                1009,
                "status",
                "construction-contract; model difficulty uncalibrated");
    }

    public static Map<String, Object> specification(
            Object domain, Object profile, Object level, Object seed) {
        if (!(domain instanceof String)
                || !(profile instanceof String)
                || !DOMAINS.containsKey(domain)
                || !PROFILES.containsKey(profile))
            throw new IllegalArgumentException("Unknown domain or profile");
        long number = integer(seed);
        if (number < 0 || number > 0xffffffffL)
            throw new IllegalArgumentException("seed must be an unsigned 32-bit integer");
        return map("domain", domain, "profile", profile, "level", level(level), "seed", number);
    }

    public static Map<String, Object> generate(Map<String, Object> spec) {
        var random = new Random(integer(spec.get("seed")));
        var level = object(spec.get("level"));
        int nodeCount = (int) integer(level.get("min_nodes")),
                fanin = (int) integer(level.get("min_fanin")),
                operators = (int) integer(level.get("min_operators")),
                modulus = (int) integer(level.get("modulus"));
        var nodes = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < nodeCount; i++) {
            var depends = new ArrayList<String>();
            if (i == 0) depends.add("input");
            else for (int j = Math.max(0, i - fanin); j < i; j++) depends.add("n" + j);
            nodes.add(
                    map(
                            "id",
                            "n" + i,
                            "depends",
                            depends,
                            "op",
                            OPS.get(i % operators),
                            "offset",
                            random.nextInt(1, modulus),
                            "factor",
                            random.nextInt(1, 10)));
        }
        var cases = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < integer(level.get("min_cases")); i++)
            cases.add(map("id", "case-" + i, "input", random.nextInt(modulus)));
        return map("spec", snapshot(spec), "nodes", nodes, "cases", cases);
    }

    public static List<String> validate(Object value, Map<String, Object> expected) {
        if (!(value instanceof Map<?, ?> candidate)
                || !candidate.keySet().equals(Set.of("spec", "nodes", "cases"))
                || !canonical(candidate.get("spec")).equals(canonical(expected)))
            return List.of("Design must contain the unchanged spec, nodes and cases");
        var level = object(expected.get("level"));
        if (!(candidate.get("nodes") instanceof List<?> nodes)
                || nodes.size() < integer(level.get("min_nodes"))
                || nodes.size() > 512)
            return List.of("Node count violates the level or runner limit");
        if (!(candidate.get("cases") instanceof List<?> cases)
                || cases.size() < integer(level.get("min_cases"))
                || cases.size() > 1024)
            return List.of("Case count violates the level or runner limit");
        Map<String, Integer> depth = new HashMap<>();
        depth.put("input", 0);
        Set<Object> operators = new HashSet<>();
        int fanin = 0;
        for (Object node : nodes) {
            if (!(node instanceof Map<?, ?> item)
                    || !item.keySet().equals(Set.of("id", "depends", "op", "offset", "factor")))
                return List.of("Every node requires id, depends, op, offset and factor");
            if (!(item.get("id") instanceof String id)
                    || !id.matches("[A-Za-z_][A-Za-z0-9_]*")
                    || depth.containsKey(id))
                return List.of("Node identifiers must be unique ASCII identifiers");
            if (!(item.get("depends") instanceof List<?> depends)
                    || depends.isEmpty()
                    || depends.stream()
                            .anyMatch(d -> !(d instanceof String) || !depth.containsKey(d))
                    || new HashSet<>(depends).size() != depends.size())
                return List.of("Dependencies must be unique, earlier nodes or input");
            if (!(item.get("op") instanceof String)
                    || !OPS.contains(item.get("op"))
                    || !bounded(item.get("offset"), 0, integer(level.get("modulus")) - 1)
                    || !bounded(item.get("factor"), 1, 100))
                return List.of("Invalid operator, offset or factor");
            depth.put(id, 1 + depends.stream().mapToInt(depth::get).max().orElseThrow());
            operators.add(item.get("op"));
            fanin = Math.max(fanin, depends.size());
        }
        var findings = new ArrayList<String>();
        if (Collections.max(depth.values()) < integer(level.get("min_depth"))
                || fanin < integer(level.get("min_fanin"))
                || operators.size() < integer(level.get("min_operators")))
            findings.add(
                    "Increase dependency depth, fan-in or operator diversity to meet the level");
        Set<String> ids = new HashSet<>();
        for (Object item : cases) {
            if (!(item instanceof Map<?, ?> sample)
                    || !sample.keySet().equals(Set.of("id", "input"))
                    || !(sample.get("id") instanceof String id)
                    || id.isEmpty()
                    || !ids.add(id)
                    || !bounded(sample.get("input"), 0, integer(level.get("modulus")) - 1))
                return List.of("Cases need unique IDs and bounded integer inputs");
        }
        return findings;
    }

    static boolean bounded(Object value, long minimum, long maximum) {
        return (value instanceof Integer || value instanceof Long)
                && integer(value) >= minimum
                && integer(value) <= maximum;
    }

    public static Map<String, Object> solve(Map<String, Object> candidate) {
        var output = new LinkedHashMap<String, Object>();
        long modulus = integer(object(object(candidate.get("spec")).get("level")).get("modulus"));
        for (var sample : objects(candidate.get("cases"))) {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("input", integer(sample.get("input")));
            for (var node : objects(candidate.get("nodes"))) {
                var operands = ((List<?>) node.get("depends")).stream().map(values::get).toList();
                long combined =
                        switch ((String) node.get("op")) {
                            case "sum" -> operands.stream().mapToLong(Long::longValue).sum();
                            case "max" -> Collections.max(operands);
                            case "min" -> Collections.min(operands);
                            default -> operands.stream().reduce(0L, (left, right) -> left ^ right);
                        };
                values.put(
                        (String) node.get("id"),
                        (combined * integer(node.get("factor")) + integer(node.get("offset")))
                                % modulus);
            }
            values.remove("input");
            output.put((String) sample.get("id"), values);
        }
        return output;
    }

    public static boolean gradeAnswer(Map<String, Object> candidate, Object answer) {
        return canonical(answer).equals(canonical(solve(candidate)));
    }
}
