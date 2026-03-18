# scim-server-mgmt JMH Benchmark Results

## Environment
- JDK: OpenJDK 17.0.18
- JMH: 1.37
- Platform: macOS ARM64
- Forks: 0
- Warmup: 3 iterations x 1s
- Measurement: 5 iterations x 1s

## Current Implementation

The current implementation includes the optimized user mapping path and the original group, log, token, workspace, and stats mappings that were retained after benchmarking.

### User Mapping

| Benchmark | Score (ns/op) |
|-----------|--------------:|
| mapMinimalUser | 977.3 |
| mapFullUser | 1590.8 |
| mapBatch100Users | 161976.5 |
| mapUserLookup | 46.2 |
| mapWorkspaceStats | 194.5 |

### Group Mapping

| Benchmark | Score (ns/op) |
|-----------|--------------:|
| mapEmptyGroup | 707.1 |
| mapGroupWith20Members | 1596.6 |
| mapBatch50Groups | 59631.8 |
| mapGroupLookup | 45.3 |

### Misc Mapping

| Benchmark | Score (ns/op) |
|-----------|--------------:|
| mapLog | 231.9 |
| mapBatch50Logs | 11669.2 |
| mapToken | 199.9 |
| mapWorkspace | 345.1 |

The current raw benchmark snapshot is stored in `current-results.json`.
