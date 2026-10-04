# Historical benchmark review — October 4, 2026

This is a translated historical review of run
`17594b0d-8b98-49e9-a300-3f6d3a906dc6`, user build `1.0.5-perf-r2-hotfix2`.
One server JSON and ten client JSON reports were supplied. All ten stages completed
in 714.6 seconds; playerRestored was true and native Enhanced Celestials Blood Moon
was reported active. It is not a test of the current Advanced6 source.

## Recorded results

Settings: 120 FPS cap, VSync off, render distance 10, simulation distance 5,
1920×1009 framebuffer, Java 21.0.12. Maximum client/server heap: 4/2 GiB.
These are not total process Working Set limits. No identical-scene before/after
run was available to quantify benefits of the earlier patches.

| Stage | Server mean / p99 (ms) | Server max (ms) | Client effective mean FPS | Client interval p99 (ms) | Pre-present work >8.33 ms | Loaded entities start -> end |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 4.49 / 8.04 | 17.20 | 118.0 | 9.35 | 0.16% | 1 → 1 |
| village_32 | 6.62 / 11.75 | 23.20 | 117.8 | 9.36 | 0.23% | 33 → 33 |
| mobs_16 | 9.68 / 22.90 | 41.59 | 117.6 | 9.69 | 1.93% | 47 → 89 |
| mobs_32 | 11.91 / 22.06 | 320.09 | 116.9 | 10.56 | 5.56% | 64 → 111 |
| mobs_64 | 15.26 / 29.28 | 47.35 | 114.7 | 12.50 | 12.81% | 88 → 169 |
| positive_effects_64 | 16.07 / 28.81 | 111.06 | 114.6 | 12.62 | 11.62% | 93 → 169 |
| blood_moon | 19.82 / 36.85 | 43.82 | 113.7 | 13.49 | 12.25% | 88 → 159 |
| wither_10 | 6.93 / 14.04 | 100.83 | 117.9 | 9.35 | 0.38% | 19 → 41 |
| teleport | 3.93 / 8.15 | 12.29 | 118.0 | 9.34 | 0.20% | 1 → 1 |
| exploration | 10.20 / 23.38 | 35.84 | 117.6 | 9.70 | 1.63% | 9 → 105 |

Effective FPS is 1000 / mean frame interval and includes cap/presentation waits.
Small interval overages do not establish render work exceeding budget. Pre-present
work is render-thread wall time, not GPU time or pure hardware CPU time.
Stack counts are not CPU percentages; p99 interval is not mean 1% low FPS.

## Findings

1. All ten transitions completed, including Blood Moon. These reports did not
   establish a benchmark abort or application crash after completion.
2. Blood Moon mean pre-present work was 5.58 ms, p99 13.16 ms; about 12.25% of
   work frames exceeded 8.33 ms. The 64-mob stage reached about 12.81%. A near-cap
   average did not establish stable 120 FPS.
3. mobs_32 peaked at 320.09 ms on the server. Sampled paths included chunk
   serialization, chunk storage save, persistent-state save, codecs/NBT/GZIP and
   file writes. This implicated saving without separating serialization, storage
   or antivirus cost. No Old Generation collection was observed in that stage.
4. Exploration recorded one Old Generation collection totaling 439 ms, while
   callback tick maximum was only 35.84 ms. No timeline connected the GC event
   to a particular frame. Callback-only timing omitted some pacing outside ticks.
5. Of 631 slow client samples, 112 contained MinimapRenderer.render and 44
   contained ImmutableDelegatingMap.entrySet, counted once per stack. OpenGL and
   Xaero/Iris/Sodium hooks appeared, but did not prove a single root cause.
6. TC Layer beta.14 built temporary stream mapping/collector state per entrySet.
   The reviewed revision replaced only the audited expression with traversal and
   the same JDK collector, preserving eager snapshots, order, mapping, immutable
   entries and null rejection. Inventory values remained live on each call.
7. Total loaded entities differed from requested mobs (88 -> 169 in the 64-mob
   stage). This alone did not establish a leak. Revised reports added per-type
   counts and live owned actor/opponent counts at measurement boundaries.

## Per-stage collector counter deltas

| Stage | Client Young / Concurrent / Old (ms) | Server Young / Concurrent / Old (ms) |
|---|---:|---:|
| baseline | 67 / 19 / 0 | 9 / 9 / 0 |
| village_32 | 107 / 30 / 0 | 12 / 20 / 0 |
| mobs_16 | 102 / 23 / 0 | 37 / 18 / 0 |
| mobs_32 | 173 / 24 / 0 | 68 / 24 / 0 |
| mobs_64 | 198 / 19 / 0 | 64 / 23 / 0 |
| positive_effects_64 | 186 / 18 / 0 | 93 / 24 / 0 |
| blood_moon | 199 / 25 / 0 | 130 / 42 / 0 |
| wither_10 | 160 / 16 / 0 | 41 / 19 / 0 |
| teleport | 125 / 14 / 0 | 59 / 16 / 0 |
| exploration | 111 / 31 / 0 | 731 / 182 / 439 |

Concurrent collector time is not a stop-the-world pause duration. Do not add all
collector deltas as freeze time. Client heap fluctuated; no client Full GC was
reported across these ten stages. The data did not establish a memory leak/OOM.

## Historical revision and verification

Internal revision 1.0.5-perf-r2-review1 retained public version 1.0.5-1.20.1.
It added the guarded TC Layer snapshot expression, server start-to-start pacing,
runnable delayed-boundary stacks after 100 ms, per-recording GC deltas, world
boundary snapshots and explicit arena limitations. Start-to-start intervals include
normal approximately 50 ms sleep; they are not tick-work durations.

Historical validation reported a Gradle 9.6.1 / Loom 1.17.21 / JDK 21 build with
Java 17 output; TC Layer bytecode verification/idempotence/changed-expression skip;
10,000 Bewitchment cases, 10,007 regex cases and 100,000 timing samples; 1,000
transformed upstream snapshot cases on isolated client/server; and cadence/GC
synthetic checks (439 ms added delay -> 489 ms interval; 150 -> 589 ms GC -> 439 ms
delta). A simulated-peer fixture completed all ten stages, save/recovery and
Blood Moon activation with accelerated timestamps. These were not graphical FPS
measurements. The helper allocation microbenchmark (24 elements, 100,000 calls,
three trials) reported 2,216–2,265 bytes/call upstream and 2,032 after the patch:
approximately 8–10% less for that operation, not a modpack FPS gain.

## Scope at that time

Most stages used a flat arena. Village meant villagers/workstations rather than
village buildings; Wither explosions could not destroy its bedrock floor.
Exploration used a noise generator in a custom dimension, flying at Y=180 at about
8 blocks/second. Revisited chunks were already generated. Dimension-specific mod
hooks could differ from the actual overworld. The advanced actual-world suite was
not yet implemented in that historical revision; later versions added it.

The historical recommendation was simultaneous manual server/client profiles on
copies of the same actual save, with separate village/structure, new-chunk and
inventory/EMI routes. It did not claim save/GC/OpenGL stalls resolved. No content,
visual quality, normal AI, movement validation, launcher RAM or mod list was reduced.
Current instructions and unbuilt status are in STRESS_TEST_GUIDE.txt and VALIDATION.json.
