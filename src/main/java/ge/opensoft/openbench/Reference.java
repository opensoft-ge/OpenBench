package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import java.io.IOException;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;

public final class Reference {
    private Reference() {}

    public static Map<String, Object> evaluate(Path root) throws Exception {
        var graph = object(read(root.resolve("graph.json")));
        var samples = new ArrayList<Map<String, Object>>();
        if (Files.exists(root.resolve("cases.json")))
            samples.addAll(objects(read(root.resolve("cases.json"))));
        else if (Files.exists(root.resolve("cases.jsonl"))) {
            for (String line : Files.readAllLines(root.resolve("cases.jsonl")))
                samples.add(object(MAPPER.readValue(line, Object.class)));
        } else {
            try (var database =
                            DriverManager.getConnection(
                                    "jdbc:sqlite:file:"
                                            + root.resolve("cases.sqlite").toAbsolutePath()
                                            + "?mode=ro");
                    var statement = database.createStatement();
                    var results = statement.executeQuery("SELECT id,input FROM cases")) {
                while (results.next())
                    samples.add(map("id", results.getString(1), "input", results.getLong(2)));
            }
        }
        var output = new LinkedHashMap<String, Object>();
        long modulus = integer(graph.get("modulus"));
        for (var sample : samples) {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("input", integer(sample.get("input")));
            for (var node : objects(graph.get("nodes"))) {
                long combined =
                        switch ((String) node.get("op")) {
                            case "min" -> Long.MAX_VALUE;
                            case "max" -> Long.MIN_VALUE;
                            default -> 0;
                        };
                for (Object dependency : (List<?>) node.get("depends")) {
                    long operand = values.get(dependency);
                    combined =
                            switch ((String) node.get("op")) {
                                case "sum" -> combined + operand;
                                case "max" -> Math.max(combined, operand);
                                case "min" -> Math.min(combined, operand);
                                default -> combined ^ operand;
                            };
                }
                values.put(
                        (String) node.get("id"),
                        Math.floorMod(
                                combined * integer(node.get("factor"))
                                        + integer(node.get("offset")),
                                modulus));
            }
            values.remove("input");
            output.put((String) sample.get("id"), values);
        }
        return output;
    }

    public static int verify(Path expected, Path answer) throws IOException {
        Object target = read(expected);
        try {
            return canonical(target).equals(canonical(read(answer))) ? 1 : 0;
        } catch (IOException | RuntimeException error) {
            return 0;
        }
    }
}
