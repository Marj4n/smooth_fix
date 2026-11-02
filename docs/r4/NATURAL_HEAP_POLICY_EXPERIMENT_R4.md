# Natural G1 heap capacity policy — separate background experiment

New JVM-policy cohort, not pooled with earlier unchanged-flag cohorts. Xms512M/Xmx6G unchanged, no periodic or explicit GC during timing, HorsemanOFF both. A native40/70free ratios vs B10/20, initial values verified from actual JFR. Background native server simulation in a physically bounded31x31arena with fixed observer,64original Villagers, exact per-actor native age/tick witnesses. JFR samples and weights are estimates; top/inclusive groups overlap, execution samples are not CPU milliseconds. n1percondition and host/world-ambient variation limit causal/hardware conclusions. SumOfPauses counts recordedGC events beginning inside the phase, distinct from event duration/collector time. Working set is a sampled background diagnostic, not full foreground gameplay6GB acceptance. No forcedGC/screenshots/GUI/Gradle/MAT during timed actions. DefaultOFF until remaining acceptance.

Status: COMPLETE_SCOPED_NATIVE64_ALLOCATION_PAIR. Native inputs match: True; focus match: True; native baby-count match: True; no recorded data loss: True.

| Metric | A free ratios40/70 | B free ratios10/20 |
|---|---:|---:|
| Mean server tick ms | 16.281533522958146 | 17.396621098265896 |
| P99 server tick ms | 29.0248 | 36.8092 |
| Actual native world ticks | 2461.00 | 2422.00 |
| RecordedGC sum of pauses ms | 1323.51 | 1269.43 |
| Sampled allocation estimated bytes/native tick | 21130176.85 | 20383514.01 |
| Horseman server-stack allocation estimated weight | 0.00 | 0.00 |
| Mean background working set MiB | 8005.82 | 8105.85 |
| Mean background private commit MiB | 10781.87 | 10204.54 |
| Mean sampled Java heap used MiB | 5013.58 | 4863.48 |
| Mean sampled Java heap committed MiB | 6144.00 | 5557.80 |
| Minimum sampled Java heap committed MiB | 6144.00 | 5412.00 |

Do not substitute these background/JFR values for the separate core17 foreground FPS cohort or core8 diagnostic memory cohort. No global speedup or6GB success is claimed automatically. Detailed statistical/source limits are in the structured evidence. Both exact-source native horse/boat paths, original state recovery and saved exits were checked after profiling.

## Additional measured memory and GC distribution

Sampled peaks are not continuous process peaks. Native buffer-pool measurements exclude other native allocations. No foreground frame-pacing claim.

| Metric | A40/70 | B10/20 |
|---|---:|---:|
| medianWorkingSetMiB | 8031.0859375 | 8190.140625 |
| sampledPeakWorkingSetMiB | 8051.1796875 | 8194.23828125 |
| medianPrivateCommitMiB | 10836.04296875 | 10331.650390625 |
| actualTopLevelGcPauseCount | 50 | 70 |
| gcPauseP95Ms | 54.1769 | 39.779 |
| gcPauseP99Ms | 56.4735 | 57.0012 |
| gcPauseMaxMs | 56.4735 | 57.0012 |
| estimatedAllocationMiBPerSecond | 403.075288271045 | 388.7887033614083 |


## Decision

Candidate remains OFF. Mean working set increased100.03MiB and meanMSPT increased~6.85%; committed heap/private commit fell. The first n1-per-condition exploratory pair therefore does not justify promotion or a promising-result ABBA repetition. No foreground FPS/frame-pacing result is claimed. Actual native64 gates, player recovery, reload and saved exits pass. The B cleanup helper initially hit an unrelated heap-dump disk guard in counts-only mode; saved-state recovery completed its counts and natural exit without repeating timed work. Both original worker failures remain preserved.
