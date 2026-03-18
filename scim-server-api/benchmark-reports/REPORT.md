# JMH Benchmark Results

## Environment
- JDK: OpenJDK 17.0.18
- JMH: 1.37
- Platform: macOS ARM64
- Forks: 0
- Warmup: 3 iterations x 1s
- Measurement: 5 iterations x 1s

## Current Implementation

### ScimUserMapper

| Benchmark | Score (ns/op) |
|-----------|--------------:|
| mapMinimalUser | 447.2 |
| mapFullUser | 1108.6 |
| mapFullUserNoGroups | 1104.2 |
| mapBatch100Users | 110817.1 |
| applySimpleScimInput | 81.0 |
| applyFullScimInput | 335.1 |
| clearMutableAttributes | 1912.3 |

### ScimGroupMapper

| Benchmark | Score (ns/op) |
|-----------|--------------:|
| mapEmptyGroup | 379.3 |
| mapGroupWith20Members | 2190.8 |
| mapBatch50Groups | 49236.2 |

### ScimFilterParser

| Benchmark | Score (ns/op) |
|-----------|--------------:|
| parseSimpleEq | 709.0 |
| parseSimpleCo | 676.2 |
| parseSimpleSw | 649.1 |
| parsePresence | 450.5 |
| parseAndFilter | 1556.1 |
| parseOrFilter | 1608.3 |
| parseComplexFilter | 2612.1 |
| parseDeeplyNestedFilter | 4292.6 |
| parseEnterpriseFilter | 1280.5 |
| parseNameSubFilter | 1645.0 |
| parseNotFilter | 986.7 |
| parseGroupFilter | 800.3 |
| parseNullFilter | 2.7 |
| resolveUserSortAttribute | 1.5 |
| resolveGroupSortAttribute | 1.5 |

The current raw benchmark snapshot is stored in `current-results.json`.
