# Java 25 validation — 13 November 2026

`./mvnw clean verify` passes **13 JUnit tests**, with zero failures, errors or skipped tests. Java 25 and Spring Boot 4.1.2 are pinned; Maven 3.9.16 is supplied by the checksum-pinned wrapper. These measurements describe the Java migration and supersede earlier stack-specific counts.

| Production Java metric | Covered / total |
|---|---:|
| Executable lines | 667/667 |
| Methods | 74/74 |
| Branch outcomes | 311/329 |

Every production Java class is instrumented, including the application entry point and startup/configuration paths. JaCoCo 0.8.16 enforces an exact executable-line ratio of 1.0 without exclusions, ignore annotations or rounded-percentage gates. All methods are exercised. Branch coverage is reported honestly above and is not 100%; this migration does not claim full branch coverage.

| Source file | Executable lines | Methods |
|---|---:|---:|
| `Calibration.java` | 41/41 | 7/7 |
| `Bundle.java` | 127/127 | 8/8 |
| `Cli.java` | 100/100 | 4/4 |
| `Reference.java` | 46/46 | 2/2 |
| `Json.java` | 34/34 | 14/14 |
| `Runner.java` | 96/96 | 15/15 |
| `Application.java` | 6/6 | 4/4 |
| `Engine.java` | 92/92 | 10/10 |
| `Design.java` | 125/125 | 10/10 |

Acceptance covers all 100 level contracts, deterministic designs, hand-computed answers, independent evaluation, JSON/JSONL/SQLite inputs, malformed candidates, exact answer types, retries, callback isolation, attempt/time/cost limits, subprocess deadlines, cleanup, immutable artifacts, digest tampering, overwrite refusal and deterministic archive bytes. Unit Docker doubles test failure paths; they are not container acceptance evidence.

`sh scripts/smoke-docker.sh` separately passes all three real Docker profiles at level-16, including independent reference acceptance, empty/mutated answer rejection and checksum-verified archive packaging. The participant image contains no factory/reference JAR or expected answers; the trusted reference control uses a temporary read-only private mount. The Java CLI image builds and its catalog runs successfully as UID 10001. No model provider was contacted and empirical difficulty remains uncalibrated.

Reports: `target/site/jacoco/index.html`, `target/site/jacoco/jacoco.xml`, and `target/surefire-reports/`. `clean` removes stale reports before instrumentation. Passing tests and execution coverage establish the documented implemented subset; they do not prove complete vendor parity, missing inventory coverage, production load capacity or enterprise readiness.

## AI product direction

Requirement added 2026-11-16: OpenBench must become fully AI enabled, agentic and conversational across its core journeys. The [AI capability and acceptance guide](ai-capabilities.md) defines the required work and evidence. This direction does not change the implementation scope or historical validation results recorded above.
