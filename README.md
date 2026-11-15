# OpenBench

An original terminal-task factory that brings scenario selection, task construction, validation, bounded repair attempts, container grading, packaging and authoring evaluation into one repository and CLI.

See the [architecture](docs/architecture.md) and [capability ledger](docs/compatibility.md) for implemented features and remaining work.

## Run

Java 25 is required. The application uses Spring Boot 4.1.2 and the Maven 3.9.16 wrapper. Docker is required for runtime grading; no paid model account is needed for the built-in generator.

```bash
./mvnw clean verify
alias openbench='java -jar "$PWD/target/openbench-0.2.0-SNAPSHOT.jar"'
openbench catalog
mkdir -p artifacts
openbench generate --level level-16 --seed 42 --output artifacts/design.json
openbench check artifacts/design.json
openbench run --level level-16 --profile checkpoint-mosaic --seed 42 \
  --output artifacts/run-001 --attempts 3 --seconds 600
openbench pack artifacts/run-001 artifacts/task-001.tar.gz
```

Each output path must be new. Existing runs and archives are never overwritten. `generate` produces a data-only design; `check` validates its construction contract. Only `run` performs container grading and creates an accepted task. `pack` verifies the accepted tree hash before creating an archive. It removes filename/time metadata from gzip and returns `archive_sha256` alongside `tree_sha256`; identical accepted files produce identical archive bytes in the same Java/compressor runtime. Exit codes: 0 success/accepted, 1 rejected/exhausted/timeout/budget exhausted, 2 infrastructure or input error.

The CLI image supports catalog, generation, structural checks, packaging and calibration:

```bash
docker build -t openbench:local .
docker run --rm openbench:local catalog
```

Run the container-grading command from the host installation with Docker available. The CLI image does not contain a Docker daemon or require access to a Docker socket.

## Domains, profiles and levels

Original domains: **lunar-shadow-reservation**, **acoustic-tile-custody**, **mycelium-vault-rehydration**. Profiles: **chronicle-fold** (JSON), **partition-weave** (JSONL), **checkpoint-mosaic** (SQLite). The current renderer uses these as synthetic contexts and input layouts for a common dependency-transformation task family.

Levels use only `level-N`: **level-1 through level-100**, from basic/standard to nightmare. Each next level requires more dependency depth, nodes and test cases. Fan-in and operator diversity also increase at defined thresholds. See the [level anchors and progression rules](docs/difficulty.md).

These are progressively stricter **construction contracts**, not measured claims that each level defeats a frontier model. The built-in generator is a deterministic integration baseline. It does not establish frontier-level task difficulty. Real model authoring trials, independent quality review and solver calibration are the next work needed to establish that claim.

## Model-backed authoring adapter

A trusted adapter command can call any model service and return a candidate design. OpenBench starts it with a JSON request on stdin and expects JSON on stdout:

```bash
openbench run --level level-16 --output artifacts/model-run --budget 1000000 \
  --generator java -jar /absolute/path/to/your-model-adapter.jar
```

Request fields: `spec`, `previous`, `feedback`, `allowance_microunits`. Response: `{"candidate": {...}, "cost_microunits": 123}`. A candidate contains exactly `spec`, `nodes`, `cases`; inspect an output from `generate` for the concrete schema. The spec is immutable. Repairs receive the previous candidate and deterministic findings. The adapter must enforce its supplied spending allowance and its own provider limits; OpenBench accounts for reported cost and stops acceptance when over budget. Credentials belong in the adapter's environment, not task files. Commands are explicitly trusted operator code; model-generated source is never executed by the factory.

The bundled system does not call a model provider on its own or pretend that the deterministic feedback is an independent LLM critic. No frontier authoring or solving results have been measured in this iteration.

## Artifacts and grading

A run records immutable `attempt-NNN/candidate.json`, the rendered `task/`, measured `grade.json`, and `result.json`. An accepted task has its own immutable directory and tree digest. The package includes separate `environment/` and `tests/` build contexts. Only `environment/` belongs in the agent image; expected values stay in the verifier context. The private Java reference evaluator is included only in the verifier context. Never expose `tests/`, the factory JAR or the full package to the participant. Candidates, run metadata and model credentials are not packaged.

The grader builds both images, temporarily mounts the independent evaluator read-only into the agent image for a trusted control run that writes and rereads the declared answer artifact, and requires the separate verifier to accept its answer and reject empty and corrupted answers. Every trial gets a distinct immutable artifact directory. Runtime containers are unprivileged with no network, a read-only root, dropped capabilities, bounded memory/CPU/process count and a deadline. Cleanup may take up to 20 additional seconds. The task format is **openbench-2**. The new level contract and Java seed sequence are versioned explicitly; regenerate designs for this version rather than relabeling older artifacts. Existing immutable artifacts can be retained for archival review.

## Verify and evaluate

```bash
sh scripts/verify.sh
sh scripts/smoke-docker.sh
```

The first command runs JUnit and measures every production Java class with JaCoCo, requiring exact **100% executable-line coverage** without exclusions. Method and branch counters are reported separately in the validation notes. Reports are in `target/site/jacoco/`. The second exercises all three profiles at level-16 using real Docker, independent reference evaluation, negative controls and packaging. CI runs both.

`openbench calibrate trials.json` groups measured authoring outcomes by model and level and reports acceptance rates, costs and 95% Wilson intervals. Each input record has `model`, `level`, boolean `accepted` and integer `cost_microunits`. It summarizes supplied evidence; it cannot authenticate externally supplied trials or prove monotonic difficulty. See [validation](docs/validation.md) for this iteration's actual measurements.

Licensed under the [Apache License 2.0](LICENSE); see [notices](THIRD_PARTY_NOTICES.md). Cloud scheduling, native provider adapters, semantic critics, novelty detection, solver trial orchestration, resume, portable format adapters and calibrated frontier difficulty remain explicit ledger gaps.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, validation and contribution terms.

## Product experience

See the [professional appearance and dark-mode requirements](docs/product-experience.md), including current implementation status and browser-release acceptance criteria.
