package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import java.util.*;

public final class Calibration {
    private Calibration() {}

    public static List<Map<String, Object>> summarize(Object value) {
        if (!(value instanceof List<?> trials) || trials.isEmpty())
            throw new IllegalArgumentException("Provide at least one measured authoring trial");
        var groups = new TreeMap<String, TreeMap<Integer, List<Map<String, Object>>>>();
        for (Object entry : trials) {
            var trial = object(entry);
            if (!trial.keySet().equals(Set.of("model", "level", "accepted", "cost_microunits"))
                    || !(trial.get("accepted") instanceof Boolean)
                    || integer(trial.get("cost_microunits")) < 0)
                throw new IllegalArgumentException("Invalid authoring trial");
            String model = text(trial.get("model"));
            int rank = (int) integer(Design.level(trial.get("level")).get("rank"));
            groups.computeIfAbsent(model, key -> new TreeMap<>())
                    .computeIfAbsent(rank, key -> new ArrayList<>())
                    .add(trial);
        }
        var summaries = new ArrayList<Map<String, Object>>();
        groups.forEach((model, levels) ->
                levels.forEach((rank, items) -> {
                    int n = items.size(),
                            passed =
                                    (int)
                                            items.stream()
                                                    .filter(trial ->
                                                            (Boolean) trial.get("accepted"))
                                                    .count();
                    double p = (double) passed / n, z = 1.96;
                    double center = (p + z * z / (2 * n)) / (1 + z * z / n),
                            radius =
                                    z
                                            * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
                                            / (1 + z * z / n);
                    summaries.add(
                            map(
                                    "model",
                                    model,
                                    "level",
                                    "level-" + rank,
                                    "trials",
                                    n,
                                    "accepted",
                                    passed,
                                    "acceptance_rate",
                                    p,
                                    "wilson_95",
                                    List.of(
                                            Math.max(0, center - radius),
                                            Math.min(1, center + radius)),
                                    "cost_microunits",
                                    items.stream()
                                            .mapToLong(item -> integer(item.get("cost_microunits")))
                                            .reduce(0, Math::addExact)));
                }));
        return summaries;
    }
}
