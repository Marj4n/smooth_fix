# Changelog

## 1.0.5
- Review revision: reduce repeated TC Layer eager snapshot allocations while preserving the original JDK collector and inventory semantics.
- Add server start-to-start wall-clock intervals and delayed runnable boundary samples alongside callback tick work; report per-recording GC counter deltas.
- Add benchmark chunk/entity/position boundary snapshots and explicit flat-world/coverage limitations. Real-world worldgen, Lootr, inventory and EMI benchmark scenarios remain pending.
- Benchmark transition hotfix: reset the client stage before dimension teleport, ignore automatic aborts from old phases, and preserve intentional stops across transitions.
- Benchmark loading hotfix: wait for arena loading and command screens to close before warm-up; allow up to 120 seconds to become ready; preserve interruption detection after readiness.
- Performance revision r2 (version stays 1.0.5): optimized Bewitchment sigil lookup, Saints & Dragons Skyfall entity lookup, and Trinkets compatibility regex allocations.
- Added automatic `/smoothfix stress` benchmarks for villagers, mob crowds, positive effects, real Enhanced Celestials 2 Blood Moon, 10 Withers, teleport and exploration.
- Added per-stage client/server reports, repeat/feature tests, stop/timeout cleanup and player recovery after disconnect/restart.
- Fixed stale benchmark entity cleanup during chunk loads to avoid mutating entity lists while worlds save.
- Improved full-session timing statistics and retention of late profiler hotspots.
- Performance revision r1 (public version remains 1.0.5): removes temporary quaternion construction from the verified vanilla ModelPart rotation expression, while preserving pivots, scales, normal matrices and surrounding hooks.
- Added a configurable final vanilla background-worker parallelism budget (default 2), for testing CPU contention when the client and server share a PC. Queued tasks are retained; generation throughput can trade off against frame stability.
- Rebases movement validation origins only after a valid server teleport confirmation; ordinary speed and collision checks remain active.
- Added opt-in client frame profiling and improved server sampling to capture ticks exceeding the 50 ms tick budget.
- Fixed dedicated-server loading of client hooks from Shut Up MCD 1.2.0 and Enhanced Block Entities Reloaded 0.13.2 while keeping both mods installed and their client features intact.
- Preserved vanilla rejection of invalid entity rotations and added bounded entity/caller diagnostics instead of repeated error spam.
- Added on-demand client/server memory reports and a bounded, opt-in slow-tick sampler.
- Existing EMI, Spell Engine, Tumbleweed, and Obscure API patches are unchanged.

## 1.0.4
- Added an EMI 1.1.24 repair-recipe safety guard for modded repair ingredients that report non-empty but expose zero matching stacks.
- Prevents `ArrayIndexOutOfBoundsException` from `dev.emi.emi.VanillaPlugin.addRepair` during EMI reload.
- Keeps the invalid synthetic repair recipe out of EMI instead of changing the underlying item's gameplay repair behavior.
- Existing EMI Compat, EMI Loot/Tumbleweed, and Obscure API fixes are unchanged.

## 1.0.3
- Fixed startup crash caused by `SmoothFix` having a private no-arg constructor.
- Fabric's default language adapter instantiates main entrypoints reflectively, so the constructor must be public.
- No compatibility patch behavior changed.

## 1.0.2
- Removed the redundant `SmoothFixClient` entrypoint.
- Removed the unnecessary custom Loom `mods { sourceSet ... }` block.
- Client-only mixins remain client-only through `fabric.mod.json`.
- This avoids the `SmoothFixClient -> SmoothFix` source-set resolution failure entirely.
- Compatibility patch logic is unchanged.

## 1.0.1
- Fixed Fabric/Loom source-set layout that caused `compileClientJava` to fail resolving `SmoothFix`.
- Merged client-only Java sources into the main source set while keeping the mixin config client-gated.
- Kept all three 1.0.0 compatibility patches unchanged.
- Fixed the build script license filename reference.

## 1.0.0
- Initial Smooth Fix compatibility/stability patches.
