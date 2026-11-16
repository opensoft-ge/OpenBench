# One hundred construction levels

The supported contract is exactly `level-1` through `level-100`. `level-1` is basic/standard; `level-100` is the intended nightmare endpoint. Leading zeroes, zero and ranks above 100 are rejected. These labels describe intended construction complexity, not measured solver performance.

| Level | Minimum nodes/depth | Minimum cases | Minimum fan-in | Minimum operators |
|---|---:|---:|---:|---:|
| level-1 | 2 | 3 | 1 | 1 |
| level-2 | 3 | 5 | 1 | 1 |
| level-4 | 5 | 9 | 1 | 2 |
| level-5 | 6 | 11 | 2 | 2 |
| level-16 | 17 | 33 | 4 | 4 |
| level-50 | 51 | 101 | 8 | 4 |
| level-100 | 101 | 201 | 8 | 4 |

For rank N: nodes and depth are N+1; cases are 2N+1; minimum fan-in is min(floor((N−1)/4)+1, 8); operator diversity is min(floor((N−1)/3)+1, 4). Each adjacent level adds one dependency step and two cases. Fan-in and operator diversity introduce additional interactions at defined thresholds. Tests generate and validate every rank and prove strictly increasing nodes/depth/cases.

Every level requires ordered acyclic dependencies, unique identifiers, bounded integers, exact answers, a passing independent reference and rejection of empty/corrupted answers. Operators are sum, maximum, minimum and bitwise XOR followed by a declared affine modular transform. JSON, JSONL and SQLite profiles expose the same public rules through different input representations.

The format is `openbench-2`. It versions the new rank mapping and Java deterministic seed sequence. Old artifacts must not be silently relabeled: retain them as archives and generate new designs under the new contract. Identical specs produce identical designs within this format.

All domains are original scenarios, but the current renderer still uses one synthetic graph task family. Construction depth and size alone do not establish a hundred empirically distinct difficulty bands. Controlled solver trials, domain reasoning, semantic quality review and uncertainty estimates remain required before claiming measured nightmare difficulty or frontier-model failure rates. Author acceptance and solver success are different experiments.

## AI product direction

Requirement added 2026-11-16: OpenBench must become fully AI enabled, agentic and conversational across its core journeys. The [AI capability and acceptance guide](ai-capabilities.md) defines the required work and evidence. This direction does not change the implementation scope or historical validation results recorded above.
