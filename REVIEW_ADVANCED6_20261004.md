# Advanced6 source review — October 4, 2026

Public version: **1.0.5-1.20.1**. New source marker: `1.0.5-perf-r2-advanced6`.
This review uses run `b550c005-908a-405c-abd1-def02f1f2189` from the supplied
Advanced5 client/server JSON reports. The new source has not been compiled,
loaded into Minecraft, or measured on the user's PC. No dependencies were downloaded.

## What the supplied run establishes

Both reports completed with failures: **19 passed, 3 failed, 2 skipped** out of
24 stages. Inventory, EMI search and End failed; chest, Lootr and Nether passed.
Optional structure searches expired their separate search budgets. Server recovery
verified every journaled field; client recovery failed Y and inventorySha256.
Both-side recovery was therefore false. The reports do not contain enough
expected/observed client state to explain those two mismatches.

The recorded client settings were cap 120 FPS, VSync off, render distance 10,
simulation distance 5, 1920×1009 framebuffer, fast graphics, decreased particles,
entity distance scaling 1.0 and biome blend radius 0. There were 550 installed
mod entries, Java 21.0.12 and eight available processors. The recording is evidence
for this particular run, not an identical-scene before/after comparison.

| Stage | Effective mean FPS | Interval p95 / p99 / max (ms) | Work mean / p95 (ms) | Outcome |
|---|---:|---:|---:|---|
| real_world_panorama | 111.9 | 11.39 / 15.81 / 45.68 | 6.00 / 11.08 | passed |
| loaded_terrain_route | 112.6 | 10.87 / 13.80 / 65.40 | 6.00 / 10.58 | passed |
| new_terrain_route | 112.2 | 11.59 / 14.63 / 220.46 | 5.51 / 11.19 | passed |
| revisit_same_terrain | 109.2 | 12.71 / 16.23 / 162.37 | 6.50 / 12.36 | passed |
| real_village | 107.6 | 13.11 / 18.90 / 84.75 | 6.85 / 12.74 | passed |
| natural_chest_or_fixture | 111.4 | 10.80 / 16.47 / 270.74 | 6.21 / 10.47 | passed |
| lootr_personal_chest | 115.7 | 9.43 / 14.22 / 32.87 | 5.07 / 8.93 | passed |
| inventory_open_tooltips | 105.6 | 13.08 / 19.75 / 235.08 | 7.87 / 12.58 | failed_client_workload_verification |
| emi_query_latency | 108.6 | 11.91 / 16.41 / 139.83 | 7.64 / 11.51 | failed_client_workload_verification |
| emi_recipe_navigation | 115.3 | 9.31 / 11.59 / 63.05 | 5.58 / 8.86 | passed |
| terrain_combat_32 | 109.6 | 11.97 / 19.24 / 114.02 | 6.33 / 11.63 | passed |
| terrain_effects_64 | 105.3 | 13.45 / 23.31 / 51.45 | 7.31 / 13.13 | passed |
| native_blood_moon | 98.3 | 16.93 / 25.38 / 51.35 | 8.79 / 16.59 | passed |
| terrain_wither_10 | 105.3 | 13.89 / 18.10 / 105.78 | 7.66 / 13.44 | passed |
| terrain_tnt_16 | 105.1 | 13.88 / 17.06 / 31.60 | 8.04 / 13.37 | passed |
| native_block_breaks | 113.0 | 10.59 / 12.70 / 109.08 | 5.57 / 10.22 | passed |
| rain_and_particles | 108.3 | 12.63 / 15.43 / 79.11 | 7.43 / 12.32 | passed |
| real_world_teleports | 114.5 | 10.41 / 13.68 / 35.92 | 5.30 / 10.08 | passed |
| nether_terrain | 113.7 | 10.30 / 13.73 / 208.38 | 5.67 / 9.91 | passed |
| end_terrain | 116.5 | 9.18 / 9.82 / 197.41 | 4.14 / 7.25 | failed_client_workload_verification |
| mod_structure_ad_astra:oil_well | 114.0 | 10.43 / 12.87 / 51.14 | 5.46 / 10.07 | passed |
| mod_structure_alexscaves:abyssal_ruins | 117.8 | 9.10 / 9.36 / 36.91 | 3.73 / 6.29 | skipped_optional_structure_search_budget |
| mod_structure_alexscaves:acid_pit | 117.8 | 9.11 / 9.34 / 71.29 | 3.74 / 6.32 | skipped_optional_structure_search_budget |
| world_save | 114.8 | 9.69 / 13.05 / 61.35 | 5.44 / 9.39 | passed |

Effective mean FPS is 1000 / mean frame interval, including cap/presentation waits.
The p95/p99 are frame-interval percentiles, not 1% low FPS. Pre-present work is
render-thread wall time; it excludes cap/presentation waits and does not measure
GPU time. Preparation/loading is included in the recording. These limits also
apply to the largest stalls outside Blood Moon.

## Blood Moon and performance evidence

Blood Moon was observed active. Its 5,921 frames had a 10.169 ms mean interval
(approximately 98.3 FPS), p95 16.935 ms, p99 25.376 ms and maximum 51.347 ms.
Mean pre-present work was 8.792 ms, p95 16.587 ms and p99 25.030 ms; 2,120 frames
(35.8%) exceeded the 8.333 ms work budget. Stable 120 FPS was not reached.

The server callback window averaged 27.465 ms, p95 50.238 ms, maximum 107.296 ms:
62 of its 1,200 samples exceeded 50 ms, one exceeded 100 ms. Client GC deltas
were Young 286 ms and Concurrent 81 ms; server deltas were Young 158 ms and
Concurrent 50 ms. No Old Generation collection occurred in this stage. GC deltas
are not a pause timeline and do not prove which frame stalled.

Slow render stacks include AccessoriesRenderLayer flushing Iris entity buffers,
entity rendering, particles, GUI hooks, map processing and OpenGL calls. The
bounded sampler captured 922 slow samples, retained at most 256 distinct stacks
and replaced 451 entries. Retained estimates/error bounds must be considered;
adding stack estimates or recognizing a hook is not a CPU-time percentage.
The reports cannot isolate GPU cost or establish that one mod causes every drop.

Local upstream class inspection found Accessories beta.48's initial Immediate.draw
before it reads equipment/cosmetic slots. That flush occurs even when all slots
are empty. The new guard checks live contents and retains this flush whenever
anything is equipped. Only the initial expression is substituted; later UI/effect
flushes, renderer calls, animations, map bookkeeping and hover logic remain.
The helper uses cached API method handles, not cached inventory data. Missing or
incompatible inspection falls back to upstream flushing. This is a candidate
reduction in batching interruptions, not a measured FPS improvement. Its slot
inspection has a cost and must be included in the local A/B result.

The shared HUD also appeared in slow stacks. Ordered text is now prepared only
when cached lines update, retaining the same text, colors and shadows. Advanced
continuation previously serialized the full inventory each alive server tick;
it now compares live stacks and serializes only after a change. These reduce
benchmark-generated work without removing workload or changing ordinary gameplay.

## Concrete failures and source changes

- `#ingot` failed with ConcurrentModificationException in HashMap.computeIfAbsent
  through EmiTagKey.of. The audited EMI cache lookup/construction and reload now
  use the same class monitor, retaining key/value and reload behavior.
- `/.*dragon.*/` failed three times because TooltipQuery.getText received a null
  tooltip list. A guard maps only null to an empty list before the original
  first-line omission. Real non-null tooltip content is retained. Pending/missing
  tooltip text cannot match that search; errors are still recorded if they occur.
- Inventory had 81 observed swaps and 14 observed opening renders, but no tooltip
  observation. The old hook watched one public ordered-text overload. Inspection
  confirms item/optional-data paths call the private component renderer directly.
  The hook now watches that common renderer and excludes empty component lists.
- End traveled only 2.485 horizontal blocks with maximum displacement 2.098 blocks,
  53 edge brakes and 54 plans. It stopped on a half-block-height offset. Continuous
  checks now probe the actual footprint rather than a snapped block center, and
  unsafe waypoints are temporarily blocked. This addresses two controller issues;
  successful End traversal remains unverified until a graphical local run.
- Native respawn reconnects the replacement player to its network handler. Resume
  clears pending inventory/loot/query/render work while retaining the recorder.
- Recovery comparison is throttled to ten checks per second and retains expected
  and observed snapshots on failure. The supplied Y/inventory mismatch is not
  claimed fixed; strict comparison and failure reporting remain.

Both controllers' HUD/chat messages, stage descriptions, source comments and
bundled documentation use English. Optional new toggles are
accessoriesEmptyBufferFlushFix and emiSearchSafetyFix (default true, restart needed).
Reports include both toggles. Public version remains 1.0.5-1.20.1.

## Review and limits

Static review checks resource JSON, registered mixin/class references, local
upstream descriptors/slot APIs, English text and archive contents. These are
not Java compilation, transformed-bytecode verification or runtime tests.
Historical fixture sources remain available for local use, with no new pass claim.
The ZIP contains the full source, resources, Gradle wrapper and documentation;
compiled mod JARs, dependency caches and previous runtime output are excluded.

Build locally with JDK 21 using `.\gradlew clean build`, install the same main
JAR on both peers and restart. Compare copies of the same initial save, with the
same settings, seed, route, stage configuration and mod list. First verify startup,
accessory/cosmetic rendering and all recovery checks, then compare Blood Moon
p95/p99 work and intervals. Disable the new flush toggle for an otherwise identical
comparison if its inspection overhead outweighs the batching benefit.
No content, normal AI, spawning, render distance, RAM or graphics setting is reduced.
120 FPS stability is still a target; it is not guaranteed by this source revision.
