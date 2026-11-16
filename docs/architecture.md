# OpenBench architecture

Java 25 and Spring Boot 4.1.2 provide one executable non-web JAR. The CLI coordinates one application for task specifications, generation, validation, bounded attempts, grading, packaging and calibration.

`Engine.java` separates the run allowance, attempt journal and measurement gate. The allowance bounds attempts, elapsed time and reported cost. The journal creates each run and attempt exclusively and persists the candidate before deciding whether it exceeds the cost budget. The measurement gate requires the exact rendered-tree digest and all three boolean controls before acceptance.

Author and grader callbacks receive JSON snapshots. Callback mutation cannot alter the canonical specification, previously persisted attempts or the candidate supplied to a retry. Timeouts, cost exhaustion, invalid measurements and infrastructure errors remain distinct outcomes.

`Bundle.java` accepts only a tree whose digest matches an accepted run. It creates the destination exclusively, normalizes archive metadata and returns the final archive's checksum. `Bundle.java` separates solver and verifier build contexts. `Runner.java` enforces bounded container execution and cleanup; private answers remain outside the solver context.

`Design.java` define the current construction contract, while `Calibration.java` summarizes supplied outcome evidence. Construction requirements do not by themselves establish empirical solver difficulty.


The participant context contains only public graph/case inputs, a Java 25 JDK and the SQLite JDBC dependency. The factory/reference JAR and expected answers are in the private verifier context. The trusted reference control temporarily mounts that JAR read-only, writes `/artifacts/answer.json`, rereads it and passes its values to separate verifier trials. Participant runs must not receive that private mount. SQLite extracts its native library to a bounded executable `/tmp`; the root filesystem remains read-only and containers have no network or capabilities.

The task format is `openbench-2`; levels are 1–100. Maven/JUnit tests cover the Java CLI, persistence, budgets, tampering, real subprocess deadlines and test doubles for Docker error/cleanup paths. `scripts/smoke-docker.sh` separately proves all three real container grading journeys and archive creation. No paid model services are called.

## AI product direction

Requirement added 2026-11-16: OpenBench must become fully AI enabled, agentic and conversational across its core journeys. The [AI capability and acceptance guide](ai-capabilities.md) defines the required work and evidence. This direction does not change the implementation scope or historical validation results recorded above.
