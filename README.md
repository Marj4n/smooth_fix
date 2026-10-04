# Smooth Fix

Targeted compatibility, stability and measured performance work for Smooth Odyssey on Fabric 1.20.1.

## 1.0.5 benchmark review revision

Current build: `1.0.5-perf-r2-review1`; public version remains **1.0.5-1.20.1**.
Review of one completed graphical user run (server + ten client reports) found
render/accessory overhead and a gap in server timing coverage; see
`REVIEW_BENCHMARK_20261004.md` for the evidence and limits.

- TC Layer beta.14: the verified `ImmutableDelegatingMap.entrySet` expression
  builds the same eager immutable snapshot with the original JDK collector,
  without constructing a stream pipeline/collector on every call. Mapping,
  iteration order, null rejection and immutable entries remain intact. No cached
  inventory values or lazy views. The patch skips changed upstream expressions;
  `trinketsSnapshotAllocationFix` (default true) can disable it independently.
- Server profiles add `tickStartIntervals` to capture wall-clock pacing outside
  START/END callbacks. Normal sleep is included (~50ms), so this is not tick CPU
  work; small overages can be scheduler jitter. Boundary stack samples target
  runnable server work outside callbacks after a >100ms delay.
- Both sides report per-recording GC counter deltas. Concurrent collector time
  is not a stop-the-world pause timeline.
- Each benchmark stage records loaded chunk counters, entity counts by type,
  live requested actors/opponents and player position at measurement boundaries.
  These snapshots do not measure newly generated chunks or stage peaks.
- Reports explicitly identify flat-arena limitations and untested structures,
  Lootr/chests, inventory and EMI latency. Advanced real-world benchmarks are
  still pending. Arena results cannot certify whole-modpack gameplay performance.

Replace the JAR on both sides and restart. Existing perf_r2 PC/server import
ZIPs contain the older JAR; replace it with this one after importing.

## 1.0.5 benchmark transition hotfix

Public version remains 1.0.5-1.20.1; log/report build is `1.0.5-perf-r2-hotfix2`.
The stage control packet now precedes vanilla respawn/teleport packets, so the
client closes the previous recording and resets readiness before dimension changes.
Automatic aborts from an old phase cannot cancel the new phase; an explicit
`/smoothfixc stopstress` still stops the run regardless of a concurrent transition.
Interruption reports include phase, expected/actual world, screen class and focus.
Initial loading grace and post-readiness validation are retained.
Replace the JAR on both sides and restart. The PC/server import ZIPs labelled
perf_r2 predate this hotfix: replace their bundled Smooth Fix JAR when using them.

## 1.0.5 benchmark loading hotfix

Public version remains 1.0.5-1.20.1; logs/reports identify `1.0.5-perf-r2-hotfix1`.
The client waits for the correct world, a closed loading/command screen and a
focused game before sending readiness. Only then does the 10-second warm-up
begin. Initial loading can take up to 120 seconds and does not trigger the
30-second post-readiness heartbeat watchdog. Opening a screen or losing focus
after readiness still ends the run to avoid invalid measurements.
Replace the Smooth Fix JAR on both sides and restart. Performance patches unchanged.

## 1.0.5 performance revision r2

Public version remains **1.0.5-1.20.1**; startup logs identify `1.0.5-perf-r2`.
Replace the Smooth Fix JAR on both client and server, then restart both.

- Bewitchment 1.20-10: checks registered potential sigils instead of scanning the
  entire 33×33×33 cube on each audited positive-effect check. Distance, predicate
  and vanilla nearest-position ordering are preserved; oversized lists fall back.
- Saints & Dragons 0.9.85: its audited Skyfall client scan keeps an index of loaded
  relevant dragons rather than traversing all entities each frame.
- Accessories/Trinkets Compat Layer: caches the fixed regex pattern used to remove
  group prefixes, preserving Java's exact matching/replacement behavior.
- Profilers keep exact count/mean/max/budget totals over the full recording.
  Percentiles use up to 8,192 samples; long sessions use a uniform reservoir.
  The bounded stack collector can retain hotspots that appear late in the run.

### Automatic benchmark

Run these in-game with OP permission level 2. Both sides require this build,
`diagnostics: true`, and enabled bundled datapacks. Singleplayer also works.

```text
/smoothfix stress start
/smoothfix stress status
/smoothfix stress stop
/smoothfix stress wither 10
/smoothfix stress soak 30
/smoothfix stress feature minecraft:zombie 32
/smoothfix stress list
```

`start` measures baseline, 32 villagers, 16/32/64 husks with golems, positive
status effects, Enhanced Celestials 2 Blood Moon, 10 Withers, teleport and terrain
exploration. Each stage uses 10 seconds of warm-up and 60 seconds of measurement:
roughly 12 minutes plus loading. Blood Moon is skipped when its mods are missing;
adapter errors are explicitly marked, never reported as a successful Blood Moon.

Movement, camera and nearby attacks use a deterministic script. Keep the game
focused; opening a screen or switching away aborts to avoid invalid FPS results.
This controller is not a general AI agent that activates every mod's abilities.
For individual mod mobs use `feature <registered_entity_id> <count>` (1–32;
Withers capped at 10). Special spells, skills, machines and player transformations
still require manual activation while profiling. `soak` repeats the suite for
5–60 minutes, ending the last stage at the requested duration.

The three reserved `smooth_fix:benchmark*` dimensions isolate actors, block
changes and the lunar event from your ordinary dimensions. These dimensions are
within the same save, not a separate world save. Use a copy of your world for
full modpack experiments: mod-global statistics, advancements or other side
effects are not rolled back. Arena actors have real AI and are replenished every
20 ticks. Run limits include 256 loaded entities, heartbeat/loading timeouts and
sustained server heap pressure. Ordinary TPS/movement validation stays enabled.

Stop restores the player's dimension, position, camera, game mode and movement
abilities, and cleans actors. A journal written before the first teleport permits
recovery at next login after disconnect or restart. An unavailable original
dimension retains the recovery file and logs the error rather than deleting it.

Client stage reports: `config/smooth_fix/reports/benchmark_client_*.json`.
Server stage results and checkpoints: `<world>/smooth_fix/benchmarks/<runId>.json`.
The server report includes summaries received from the client; full client stack
samples remain in the local client reports. FPS intervals include presentation
and cap waits; CPU work excludes presentation. GPU time is not measured.
For 120 FPS, compare frame times with **8.33 ms**; for 20 TPS compare server ticks
with **50 ms**. Compare the same route, settings, heap, warmed chunks and other
players' activity. No claim of steady 120 FPS follows from the isolated tests.

The new optional patch fields (default `true`) are `bewitchmentSigilSearchFix`,
`saintsDragonEntityScanFix`, and `trinketsRegexAllocationFix`. Existing configs
receive defaults for missing fields; normal play does not run a profiler or test.

## 1.0.5 performance revision r1

The public mod version and JAR filename remain 1.0.5. Startup logs and reports
identify this revision as `1.0.5-perf-r1`. Replace the Smooth Fix JAR on both sides;
do not overwrite an existing world or unrelated configs. Old config files load
the missing new fields with their defaults.

The client replaces only the audited, non-escaping `new Quaternionf().rotationZYX`
expression in vanilla ModelPart rotation with scalar math. Translation, scale,
normal transforms, and other instructions/injections are retained. A changed
expression is skipped, including a Sodium implementation that already removed
this allocation. Invalid non-finite model angles fall back to the original JOML operations.
No pose cache, global model cache, quality reduction, shader disable, or animation
skipping is introduced. Floating-point rounding differs
slightly; 100,000 randomized position/normal cases pass within 0.00003 absolute
tolerance (maximum observed 0.0000038147), with bit-identical translation columns.
Production client class tests also compare 1,000 model rotations with pivots and
nonuniform scales against vanilla. Eliminating a bytecode allocation does not
quantify FPS benefit, especially if the JIT already eliminates the object.

The final vanilla/ThreadTweak ForkJoinPool construction is substituted after
upstream mixin merging, preserving its factory, handler and async mode. The
original export's ThreadTweak Reforged 1.0.0 replacement factory is covered and
verified on both production client and server, with threadCount.main=7 input. `backgroundWorkerLimit` defaults to 2 for the user's
i3-12100F running client and server together. All tasks still execute. This
does not resize Sodium or C2ME pools, alter ticks/AI/recipes, or change distances.
Less parallel generation can reduce runnable CPU competition but can also slow
chunk generation: this is a candidate to compare, not a proven full-pack win.
Set the limit to 0 and restart to restore upstream sizing for an A/B comparison.
ForkJoinPool may create compensation workers while blocking; its parallelism
is not an absolute thread-count or resident-memory limit.

On the dedicated server, the previous-tick movement origin is updated only in
the successful vanilla teleport-confirmation branch. Wrong IDs and ordinary
movement never rebase it. The production fixture tests a large server teleport,
invalid confirmation, valid confirmation, ordinary movement, and an excessive
movement rejection. The patch neither hides lag warnings nor relaxes speed,
collision, flight, or packet-count rules. Mod teleport interactions still need
testing with the full pack.

The client profile is opt-in: `/smoothfixc profile 120` (or 10-120 seconds).
While it runs, visit the village where FPS drops. `/smoothfixc stopprofile` saves
early. It now records whole-session totals with at most 8,192 percentile samples
and 256 distinct 24-frame stacks; the recording arrays and sampler thread do not exist during
ordinary gameplay. Work time stops before swapBuffers, excluding presentation
and FPS-cap waits; intervals include them. GPU execution time is not measured.
Profiling incurs sampling overhead and is not a benchmark without that caveat.
The client class fixture checks that both frame hooks apply; it does not exercise
a graphical world or run the complete client recorder.

Run `smoothfix profile 120` in the server console at the same time. The server
sampler now polls at 10 ms for ticks over 50 ms and includes initial GC/memory
measurements. Normal passive server tick history remains 1,200 durations.
Compare `client_profile_*.json` and `server_profile_*.json` plus Task Manager PID
memory, with the same warmed scene, camera, distance, heap and graphics settings.
Measurements retain the build and configured performance options.

There is no heap increase. Always-120-FPS, steady 20 TPS, unchanged total process
RAM, and full modpack compatibility are not verified by isolated math/startup
tests. Continue from those profiles instead of assuming every lag warning has
the same cause.

## 1.0.5: same mods and content

Install the new Smooth Fix JAR on both client and dedicated server, replacing only
the previous Smooth Fix JAR. Keep all other mod JARs. Previously moved EBE/Shut Up
MCD JARs can be returned to the server's mods folder after this update is installed.

An early Fabric language adapter corrects the in-memory environment of the known
client mixin configs from Shut Up MCD 1.2.0 and Enhanced Block Entities Reloaded
0.13.2. Their original files, mod environments, dependencies, and entrypoints are
preserved. All client hooks still run on the client; rendering/tooltip hooks are
scoped to the client rather than running on a dedicated server. This requires an
early hook: a normal main/preLaunch initializer runs after mixin registration.

The metadata bridge is scoped to Fabric's V1ModMetadata fields and the exact mod
versions audited above. It was inspected against Fabric Loader 0.19.5. Unknown
metadata implementations or target versions are left untouched and logged.

The rotation guard retains the previous valid yaw/pitch on NaN or infinity, just
as vanilla 1.20.1 already does. It logs a first caller trace per entity type and
keeps bounded counters. It does not fix an upstream mod's invalid calculation;
it identifies its caller without repeatedly spamming identical errors. Valid
rotations, mob AI, skill behavior, movement validation, and content are unchanged.

### Built-in diagnostics (no additional profiler mod required)

In the SERVER console, without a slash:

```text
smoothfix report
smoothfix profile 120
```

Play until the lag occurs. The sampler stops automatically after 120 seconds and
logs the report path. `smoothfix stopprofile` stops it early. In-game server commands
use a leading `/` and require permission level 2 (OP).

On the CLIENT, while in a world or connected to a server:

```text
/smoothfixc report
```

Reports are written to `config/smooth_fix/reports/` in the corresponding instance.
Send the server_profile and client report JSON files, plus Task Manager memory for
the client PID from that report at the same time. No launcher arguments, login
tokens, forced GC, or heap dumps are collected.

The report separates heap, non-heap, buffer pools, and GC history. These fields
are NOT a complete native memory measurement and must not be summed to estimate
Task Manager usage. The report deliberately leaves total resident process memory
unmeasured. Xmx limits heap rather than total process memory.

The tick window holds at most 1200 durations. The profile samples the server
thread every 10 ms only while its current tick has exceeded 50 ms, keeps at most
256 distinct stacks of 24 frames, and runs only when requested (10-120 seconds).
Samples can reveal long waits as well as CPU work; counts are not proof of CPU
cost. Short or rare spikes can be missed. Profiling adds measurement overhead;
it is not enabled continuously.

No view-distance, C2ME cache/worker, heap allocation, resource pack, recipe, world,
or mod list settings are automatically changed. Total client RAM 4 GB and server
RAM 2 GB remain performance targets to measure with the complete modpack, not
guarantees provided by this stability patch.

### Build

Use JDK 21 to run the supplied Gradle 9.6.1 / Loom 1.17.21 build:

```powershell
.\gradlew clean build
```

The generated mod remains Java 17 bytecode. The Smooth Odyssey pack may require
Java 21 because of other mods. The single main source set is explicitly grouped
as `smooth_fix` in Loom so the early adapter can see both classes and resources
in development launches.

Validation details are recorded in `VALIDATION.json`. Isolated startup tests do
not establish performance with the full pack, Android renderer compatibility,
Windows total memory, or client visual behavior.

## v1.0.0 patches

### EMI Compat 1.1.3 + Spell Engine 1.10.x
EMI Compat still tries to load its legacy Spell Engine compatibility implementation, which references removed Spell Engine APIs. Modern Spell Engine already ships its own native EMI plugin. Smooth Fix makes EMI Compat skip only that obsolete Spell Engine branch; its other integrations are untouched.

### EMI Loot 0.7.9 + Tumbleweed 0.5.5
EMI Loot creates temporary entities on `EMI Loot Worker` threads. Tumbleweed's client constructor accesses the client world's random source, which C2ME correctly rejects off-thread. Smooth Fix substitutes a worker-local RNG only for that cosmetic Tumbleweed initialization while running on an EMI Loot worker. Normal in-world Tumbleweed behavior is unchanged.

### Obscure API 16 tooltip guard
Obscure API's food-icon tooltip path assumes every stack reaching it has a non-null food component. EMI search indexing can send non-food stacks through the same path. Smooth Fix skips only that food-icon subroutine when the component is null, leaving the rest of the tooltip pipeline intact.

## Config
The first launch creates:

`config/smooth_fix.json`

```json
{
  "emiCompatSpellEngine": true,
  "emiLootTumbleweed": true,
  "obscureTooltipNullFood": true,
  "emiRepairEmptyIngredient": true,
  "dedicatedServerClientMixinGuard": true,
  "invalidEntityRotationGuard": true,
  "diagnostics": true,
  "modelRotationAllocationFix": true,
  "confirmedTeleportAnchorFix": true,
  "backgroundWorkerLimit": 2
}
```

All target mods are optional. Mixins are applied only when their target mods are installed.

## Target stack used for the v1.0.0 audit
- Minecraft 1.20.1
- Fabric Loader 0.19.5
- Fabric API 0.92.12+1.20.1
- EMI 1.1.24
- EMI Compat 1.1.3
- Spell Engine 1.10.7
- EMI Loot 0.7.9
- Tumbleweed 0.5.5
- Obscure API 16

Sortilege 9.0, EMIffect 2.1.5 and Distraction Free Recipes 1.2.1 were inspected as part of the same EMI stack, but the supplied failures did not justify an additional patch in v1.0.0.

## Build layout note (1.0.1)

All Java sources now live under `src/main/java`.
The client mixin config remains marked as client-only in `fabric.mod.json`, so the EMI-related
patches are still applied only on the client. This avoids the broken split source-set compile
classpath that previously made `SmoothFixClient` unable to resolve `org.marj4n.smooth_fix.SmoothFix`.


## 1.0.4: EMI repair ingredient guard

Some modded tool/armor repair ingredients can report themselves as non-empty while returning zero matching item stacks. EMI 1.1.24 indexes the first stack while creating synthetic anvil repair recipes, which causes `ArrayIndexOutOfBoundsException`. Smooth Fix treats that inconsistent ingredient as empty only during EMI's synthetic repair scan, so the bad recipe is skipped safely.
