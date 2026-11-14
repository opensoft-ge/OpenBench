package ge.opensoft.openbench;

import static ge.opensoft.openbench.Json.*;

import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

@Component
public class Cli {
    private final Engine engine;
    private final Runner runner;

    public Cli() {
        this(new Engine(), new Runner());
    }

    public Cli(Engine engine, Runner runner) {
        this.engine = engine;
        this.runner = runner;
    }

    public int run(String[] args, PrintStream output) {
        try {
            if (args.length == 0)
                throw new IllegalArgumentException(
                        "Commands: catalog, generate, run, check, pack, calibrate, reference,"
                                + " verify");
            Object result;
            int status = 0;
            switch (args[0]) {
                case "catalog" -> {
                    arity(args, 1);
                    result =
                            map(
                                    "domains",
                                    Design.DOMAINS,
                                    "profiles",
                                    Design.PROFILES,
                                    "levels",
                                    "level-1 through level-100",
                                    "endpoints",
                                    map("level-1", "basic / standard", "level-100", "nightmare"),
                                    "difficulty_status",
                                    "uncalibrated");
                }
                case "pack" -> {
                    arity(args, 3);
                    result = Bundle.pack(Path.of(args[1]), Path.of(args[2]));
                }
                case "calibrate" -> {
                    arity(args, 2);
                    result = Calibration.summarize(read(Path.of(args[1])));
                }
                case "reference" -> {
                    if (args.length != 2 && args.length != 3)
                        throw new IllegalArgumentException(
                                "reference requires input directory and optional artifact path");
                    result = Reference.evaluate(Path.of(args[1]));
                    if (args.length == 3) {
                        write(Path.of(args[2]), result);
                        result = read(Path.of(args[2]));
                    }
                }
                case "verify" -> {
                    arity(args, 3);
                    int reward = Reference.verify(Path.of(args[1]), Path.of(args[2]));
                    output.println(reward);
                    return 1 - reward;
                }
                case "check" -> {
                    arity(args, 2);
                    var candidate = object(read(Path.of(args[1])));
                    var spec = object(candidate.get("spec"));
                    var expected =
                            Design.specification(
                                    spec.get("domain"),
                                    spec.get("profile"),
                                    object(spec.get("level")).get("name"),
                                    spec.get("seed"));
                    var findings = Design.validate(candidate, expected);
                    result = map("findings", findings);
                    status = findings.isEmpty() ? 0 : 1;
                }
                case "generate", "run" -> {
                    var options = new LinkedHashMap<String, String>();
                    List<String> generator = List.of();
                    Set<String> allowed =
                            args[0].equals("run")
                                    ? Set.of(
                                            "domain",
                                            "profile",
                                            "level",
                                            "seed",
                                            "output",
                                            "attempts",
                                            "seconds",
                                            "budget")
                                    : Set.of("domain", "profile", "level", "seed", "output");
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--generator") && args[0].equals("run")) {
                            generator = Arrays.asList(args).subList(i + 1, args.length);
                            if (generator.isEmpty())
                                throw new IllegalArgumentException("Provide a generator command");
                            break;
                        }
                        if (!args[i].startsWith("--")
                                || !allowed.contains(args[i].substring(2))
                                || i + 1 >= args.length)
                            throw new IllegalArgumentException(
                                    "Unsupported or incomplete option: " + args[i]);
                        String key = args[i].substring(2);
                        if (options.putIfAbsent(key, args[++i]) != null)
                            throw new IllegalArgumentException("Repeated option: " + key);
                    }
                    Path destination = Path.of(text(options.get("output")));
                    var spec =
                            Design.specification(
                                    options.getOrDefault("domain", "lunar-shadow-reservation"),
                                    options.getOrDefault("profile", "chronicle-fold"),
                                    options.getOrDefault("level", "level-1"),
                                    Long.parseLong(options.getOrDefault("seed", "0")));
                    if (args[0].equals("generate")) {
                        write(destination, Design.generate(spec));
                        result =
                                map(
                                        "path",
                                        destination.toString(),
                                        "status",
                                        "design-only; runtime grade pending");
                    } else {
                        var run =
                                engine.run(
                                        spec,
                                        destination,
                                        generator.isEmpty()
                                                ? Engine::builtin
                                                : runner.commandGenerator(generator),
                                        runner::dockerGrade,
                                        Integer.parseInt(options.getOrDefault("attempts", "3")),
                                        Double.parseDouble(options.getOrDefault("seconds", "600")),
                                        Long.parseLong(options.getOrDefault("budget", "1000000")));
                        result = run;
                        status =
                                run.get("status").equals("accepted")
                                        ? 0
                                        : run.get("status").equals("error") ? 2 : 1;
                    }
                }
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            }
            output.println(canonical(result));
            return status;
        } catch (Exception error) {
            output.println(
                    canonical(
                            map(
                                    "error",
                                    Objects.toString(
                                            error.getMessage(),
                                            error.getClass().getSimpleName()))));
            return 2;
        }
    }

    private static void arity(String[] args, int required) {
        if (args.length != required)
            throw new IllegalArgumentException("Unexpected command arguments");
    }
}
