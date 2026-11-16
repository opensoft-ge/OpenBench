# OpenBench capability ledger

OpenBench combines task configuration, generation, validation, bounded iteration, grading, packaging and calibration in one Java 25 / Spring Boot application.

| ID | Behavioral area | Status | Implemented scope / remaining work |
|---|---|---|---|
| OBN-001 | Delivery scenario configuration | Partial | Three original domains and three profiles; one synthetic task family, not a large domain corpus |
| OBN-002 | Difficulty specification | Implemented contract | level-1–100 with gradual depth, fan-in and operator growth; empirical frontier difficulty remains unmeasured |
| OBN-003 | Seed/task generation | Partial | Deterministic builder and trusted external author adapter with immutable specification snapshots |
| OBN-004 | Validation and feedback | Partial | Structural graph/data contracts and actionable findings; semantic critic/novelty judges missing |
| OBN-005 | Convergence orchestration | Implemented subset | Separate allowance, journal and measurement gate; bounded attempts/time/cost, isolated callback inputs and feedback retries |
| OBN-006 | Runtime grading | Implemented subset | Separate images, independent reference, binary scoring, empty/mutation controls, isolated snapshots |
| OBN-007 | Publication gate | Implemented local packaging | Exact accepted-tree digest, reproducible gzip/tar bytes and archive SHA-256, archive creation without overwrite; no remote publication |
| OBN-008 | Calibration | Partial | Per-model/per-level authoring rate and uncertainty summaries; no live frontier trials or solver calibration |
| OBN-009 | Portable formats | Missing | openbench-2 is supported; additional import/export adapters remain future work |
| OBN-010 | Production operations | Missing | Durable queues, resume, parallel fleet scheduling, provider billing verification and hosted UI |

All production Java is covered by the exact executable-line gate; methods and branches are reported separately. Behavioral assertions cover structural violations, model-response errors, time/cost limits, stale/empty grades, failed controls, retry feedback, immutable snapshots, packaging tampering and all original profiles. Real Docker acceptance is separate from unit process mocks.

A grade is valid only when it names the rendered tree digest and reports every required control as a boolean. Errors and unmeasured results do not become accepted tasks. A package cannot silently differ from the tree that passed. Reference-only code is confined to the private verifier context; model adapter inputs stay outside the package. Expected test values belong in the separate verifier image and must never be mounted into the solver environment.

## Verified behavior — 13 November 2026

Reproducible packaging provides stable artifacts before broader orchestration. Durable resume (OBN-010) and solver calibration (OBN-008) remain future work.

`Bundle.pack` normalizes gzip filename and timestamp while preserving exclusive creation, digest verification, deterministic member order and tar metadata. The engine retains its public pack entry point. The CLI result includes archive_sha256 of the final compressed file. Reproducibility is scoped to the same Java/compressor runtime; different compressor versions are not guaranteed byte-identical.

Package acceptance checks byte equality across destination names and changed source timestamps, independently hashes the archive, reads every member without extraction, verifies normalized metadata, and proves overwrite refusal leaves bytes unchanged. Tamper/rejection checks remain. Callback mutation cannot corrupt attempt records or retry inputs. 46 tests pass: 432/432 lines and 132/132 branches; all named functions are covered.

Current Java tests, exact counters and container acceptance evidence are in [validation](validation.md).

## Required product experience — UX-001

**Planned, before the first browser release.** Implement a professional, responsive interface with complete light/dark themes and a visible, keyboard-accessible persistent toggle. Follow the [product experience acceptance contract](product-experience.md). Dependencies: real product UI and the applicable identity/access foundations; validate both themes through actual workflows, including narrow screens, preference persistence, storage failure and accessible contrast/focus. The current CLI foundation does not provide this browser capability.

## Required AI, agent and conversational delivery — OB-AI-001–005

Owner direction added 2026-11-16: fully AI-enabled core journeys, contextual conversational support and agents that complete permitted work are required product capabilities. Follow the [AI acceptance contract](ai-capabilities.md) and its five backlog dimensions: conversation, assistance, scoped actions, reliable/governed execution and measured outcomes.

**Status:** Required/unverified: this documentation update does not establish implementation or provider qualification. Reconcile each item against actual code and tests before changing its status.

Prioritize a complete supported journey with identity, source permissions, typed APIs and execution controls in place. Record implementation evidence and acceptance results per slice; a summary endpoint, conversation store or documentation change does not complete the parent requirement. Keep these items visible alongside other release blockers.
