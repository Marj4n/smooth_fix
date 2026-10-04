# Smooth Fix 1.0.5 — Advanced7 source

Targeted compatibility, stability and performance patches for Smooth Odyssey on
Fabric Minecraft 1.20.1. Public version: **1.0.5-1.20.1**. Internal build marker:
`1.0.5-perf-r2-advanced7`.

This revision is supplied as complete source only. It has **not been compiled or
runtime-tested here**, as requested. No new FPS improvement has been measured.
See [the current review](REVIEW_ADVANCED6_20261004.md),
[validation status](VALIDATION.json) and [the stress-test guide](STRESS_TEST_GUIDE.txt).
All bundled source messages and documentation use English.

## Changes in this revision

- Skip redundant BMC 1.3.0 weapon checks on armor/main-hand slot reads, retaining
  the original offhand rule and combat behavior.
- Suppress EC Core 1.0.3.3's optional shader-addon suggestion through a real
  `enhancedCelestialsShaderPromptSuppression` toggle. Remove the addon JAR/manifest
  entry using the supplied pack installer; a runtime config cannot unload a JAR.
- Keep Nether/End headings stable and penalize recently visited navigation cells;
  preserve the stage origin across respawn and stop lookahead at the next
  waypoint so a safe corner is not rejected. Movement coverage remains strict.
- Use `diagnosticTargetFps` for frame budgets (120 PC, 30 mobile), with explicit
  target-budget counters and the retained independent 8.33 ms timeline counters.
- Update Gradle's `filteringCharset` property assignment.
- Retain all Advanced6 Accessories, EMI, HUD, tooltip, death continuation and
  strict client/server recovery changes. The supplied Advanced6 run passed
  inventory, EMI and both-side recovery.

The separate PC/mobile packs contain reduced visual settings with render distance
12. Those changes are applied by the installer, not silently by the mod.
Launcher RAM, ordinary mob AI and spawn limits are preserved. The source patch
must be compiled locally; the pack scripts reject older Smooth Fix binaries.

## Existing patches retained

- Skip EMI Compat's obsolete Spell Engine branch while leaving its other
  integrations and Spell Engine's own native EMI plugin available.
- Substitute worker-local cosmetic RNG only during temporary Tumbleweed creation
  on EMI Loot workers, avoiding off-thread client-world RNG access.
- Guard Obscure API's null food component and EMI's empty repair ingredients.
- Scope known Shut Up MCD 1.2.0 and Enhanced Block Entities Reloaded 0.13.2 client
  mixin metadata to the client using the early Fabric metadata bridge. Original
  files, mod entrypoints and dependencies remain intact. Unknown metadata is left
  untouched. The bridge was audited against Fabric Loader 0.19.5.
- Retain previous valid rotation on invalid entity yaw/pitch and collect bounded
  diagnostic caller counts without repeated identical error spam.
- Replace the audited non-escaping ModelPart quaternion expression with scalar
  math, skipping changed/already optimized upstream code. Normal animation,
  transforms and rendering are retained; rounding can differ slightly.
- Limit the final audited vanilla/ThreadTweak main ForkJoinPool construction to
  configurable parallelism (default 2; zero retains upstream sizing). Factory,
  exception handler, async mode and tasks are retained. Sodium/C2ME pools are not
  resized. This can trade slower generation for less CPU competition.
- Bewitchment sigil queries inspect registered candidate sigils while preserving
  the audited bounds, predicate and nearest-position order, with a fallback.
- Saints & Dragons Skyfall uses an index of loaded relevant dragons for its
  audited entity scan.
- TC Layer caches a fixed group-prefix regex and avoids stream-pipeline overhead
  in its audited eager immutable equipment snapshot. Values are not cached.

Each optional patch is applied only when its target mod is available. Independent
boolean toggles live in `config/smooth_fix.json`; missing fields receive defaults.
Restart both peers after changes. The mod does not alter the mod list, render or
simulation distance, launcher RAM, ordinary mob AI or spawn limits.

## Build locally

Use **JDK 21** to run the supplied Gradle 9.6.1 / Loom 1.17.21 wrapper:

```powershell
.\gradlew clean build
```

Linux/macOS:

```sh
./gradlew clean build
```

Main output: `build/libs/smooth_fix-1.0.5-1.20.1.jar`. The mod remains Java 17
bytecode; other pack mods can require Java 21. Install the same main JAR on client
and server, replacing the previous Smooth Fix JAR, then restart both. Keep one
Smooth Fix JAR per instance. Host caches and compiled mod JARs are excluded.
The Gradle wrapper JAR is included because it is required by the wrapper.

Optional fixture sources under `verification/` are separate from the main source
set. A verification-only JAR is an internal test tool, not a modpack dependency.
Historical fixture results do not validate this unbuilt source revision.

## Run and compare

Use a disposable copy of the actual modpack save, with operator permission and
one player connected:

```text
/smoothfix stress advanced start world-copy
/smoothfix stress advanced status
/smoothfix stress stop
```

The command does not create a backup. TNT, mining, Withers and loot change the save
copy. The player journal restores supported player state, not arbitrary world or
mod-global state. The suite has 21 core stages and optional mod structures. Village
search expands in 10,000-block bands; optional automatic structure searches have
an independent default 90-second budget. The English HUD explains each stage.

The ordinary arena suite remains available through `/smoothfix stress start`.
Both controllers retain one client report per run and explicit failed/skipped
outcomes. Death/rescue retains measurement history and resumes the workload within
its recovery limit. See `STRESS_TEST_GUIDE.txt` for commands, flow and limits.

Manual profiling:

```text
/smoothfix profile 120
/smoothfixc profile 120
/smoothfix report
/smoothfixc report
```

Profiles are written under `config/smooth_fix/reports/` in the corresponding
instance. Compare copies of the same initial save with identical settings, route,
seed and mod list. 120 FPS requires about **8.33 ms per frame**; inspect p95/p99,
maximum and over-budget counts. Render-thread work before present excludes FPS
waits but does not measure GPU time. Stack estimates are not CPU percentages.
Concurrent GC counter time is not a stop-the-world pause timeline. Heap and buffer
pool totals are not total process Working Set.

Stable PC 120 FPS / mobile 30 FPS remain targets to measure on the user's PC, not a result established
by these source changes. The supplied Blood Moon run averaged approximately 98 FPS.
