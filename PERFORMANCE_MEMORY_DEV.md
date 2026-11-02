# Performance and memory evidence

## Measured owned-report microbenchmark

Actual Java21 run of production BoundedReportQueue versus synchronous pretty-JSON serialization/file write. Fixed synthetic report grows over100 checkpoints to the same final document. One warmup pair then5 alternating paired trials. Each measured trial asserts final JSON equality. This is a diagnostics-only microbenchmark, not a gameplay FPS/TPS benchmark and not an end-to-end recreation of the entire old report class. CPU/disk load from other apps was not controlled.

| Median per100 checkpoints | Synchronous | Bounded snapshot queue |
|---|---:|---:|
| Caller work | 132.806ms | 11.563ms |
| Caller allocated bytes | 58,618,632 | 17,395,104 |
| End-to-end elapsed incl drain | 133.428ms | 14.959ms |
| Physical document writes | 100 | 9 |
| UTF-8 bytes written | 9,141,545 | 896,045 |

Allocation counters are ThreadMXBean caller-thread bytes only. Worker allocation is excluded; do not describe the difference as total heap savings. Intermediate checkpoints coalesce; all final stages and latest outcomes are retained. SSD cache/JIT/scheduling affect this small test; raw trials and earlier measured revision are retained. No CPU thread/game worker scheduling speedup is inferred.

## Memory retaining paths fixed

1. RelevantDragonEntities indexedWorld and typed dragon sets previously retained the previous ClientWorld until another relevant query or disconnect. setWorld now clears these owned references at the transition itself.
2. FootstepTargetCache weak engine keys could still hold strong old World/camera/target references through long-lived engine values. setWorld clears entries; freshness/identity checks remain unchanged.
3. ClientBenchmarkReports no longer retains completed stage maps after disconnect.
4. One cached Gson replaces per-checkpoint builder creation. Pending same-path jobs share a completion future; estimated retained snapshots bounded by configured MiB, files bounded separately. Deep collections reject extreme lengths before allocating a full clone. Oversized valid reports fall back to drained synchronous writing.

These are source-supported retaining-path improvements, not a measured claim of X MB reclaimed. No forced GC, arbitrary System.gc, entity despawn, render-distance reduction, particle reduction or texture simplification. Texture/model content is unchanged by Smooth Fix. Queue error/size/backpressure/disconnect behavior is covered to the extent listed in TEST_RESULTS.md.

## Gameplay profiling limitations

The full advanced benchmark requires actual focused Minecraft and aborts on focus loss. It was not falsely bypassed for background work. Passive stationary profiles, when present, are only exploratory observations: they do not cover traversal, cold chunks, combat, EMI interactions, recording overhead, two clients, or100/300 minions. Keep FPS cap, DynamicFPS/focus, shaders, elapsed sample count and dimensions visible. Do not compute a causal speedup from one unmatched stationary pair. Missing GPU timing/RSS/allocation metrics stay unavailable rather than fabricated.

## Actual RD12 stationary observations

Both30s windows report renderDistance12 at start/end, cap60, focus true, same1280×720 GPU/Java/heap/CPU budget and measured Classes binary. Baseline source commit0292f93 then candidate; one run each, sequentially reopened QC save rather than reset identical snapshots. No traversal/combat/recording test, no repeated randomized A/B. Initial RD16 calibration files remain explicitly non-target.

| Observation | Source baseline | Candidate |
|---|---:|---:|
| Mean interval-derived FPS | 59.12 | 59.26 |
| Mean frame work before present | 6.826ms | 6.851ms |
| Frame intervals | 1773 | 1777 |
| End heap used | 4395.0MiB | 4272.2MiB |

There is **no meaningful demonstrated gameplay FPS improvement** in this cap-limited sample. End heap differs across GC phases and is **not evidence of equivalent retained-heap savings**. Candidate low-FPS metrics are in its JSON; baseline did not expose the new low-FPS statistic, so inverse p99 is not substituted. The final Classes binary differs only by later retired Homunculus HUD/message cleanup; those paths were not exercised in profiling. Exact measured Classes SHA256: d131c828e2f2774726289b4f01bd88d80cd38b8255578cdddffde264bbcc874f.

## Actual RD12 stationary observations

Both30s windows report renderDistance12 at start/end, cap60, focus true, same1280x720 GPU/Java/heap/CPU budget and measured Classes binary. Baseline source commit0292f93 then candidate; one run each, sequentially reopened QC save rather than reset identical snapshots. No traversal/combat/recording test, no repeated randomized A/B. Initial RD16 calibration files remain explicitly non-target.

| Observation | Source baseline | Candidate |
|---|---:|---:|
| Mean interval-derived FPS | 59.12 | 59.26 |
| Mean frame work before present | 6.826ms | 6.851ms |
| Frame intervals | 1773 | 1777 |
| End heap used | 4395.0MiB | 4272.2MiB |

There is **no meaningful demonstrated gameplay FPS improvement** in this cap-limited sample. End heap differs across GC phases and is **not evidence of equivalent retained-heap savings**. Candidate low-FPS metrics are in its JSON; baseline did not expose the new low-FPS statistic, so inverse p99 is not substituted. The final Classes binary differs only by later retired Homunculus HUD/message cleanup; those paths were not exercised in profiling. Exact measured Classes SHA256: d131c828e2f2774726289b4f01bd88d80cd38b8255578cdddffde264bbcc874f.
