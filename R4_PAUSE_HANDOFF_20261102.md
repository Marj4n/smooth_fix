# R4 pause handoff — 2026-11-02

**PAUSED BY USER. Do not resume benchmarks, MAT, profiling or development without a new user request. Not production ready.**

## Repositories and Git

Smooth Fix: `D:\Development\minecraft\smooth_fix`, existing branch `work/horizon-performance-20261101`, pre-checkpoint HEAD `0292f93eeee04307f2610f3e18885f21dc82ca1f`. No upstream. Remote https://github.com/Marj4n/smooth_fix has only main and refactor/marj4n-mod-layout-20261023 at this audit; corresponding R4 branch does not exist. Preserve the local checkpoint commit; DO NOT create a remote branch, merge main, or force-push.

Compat: `D:\Development\minecraft\smooth_spellblade_compat`, existing branch `main`, pre-checkpoint HEAD `ff6064db5aa8f24ed04522e325e5692cc88e2b08`. One weak-fallback source fix is committed separately. Final local/remote SHAs are recorded in the pause Git result manifest after commit/push.

## Last accepted results

Foreground full pack physical working set 8194.77 → 7980.75 MiB, saving 214.02 MiB (2.61%). Candidate 7.7937 GiB; 6 GiB target NOT ACHIEVED, remaining 1836.75 MiB. Private commit 15154.92 → 10587.21 MiB. End-action used heap 5444.06 → 4364.90 MiB is not post-GC live heap. FPS 184.40 → 190.12, 1% low 60.54 → 66.86, MSPT 8.51 → 8.36. Route frame P99 slightly worse 11.43 → 11.66 ms. Combined core17 ABBA results; do not attribute to source19 or an isolated closure.

G1 40/70 vs 10/20: two independent JVMs, background n1/condition. Candidate mean working set increased 100.03 MiB; heap committed decreased 6144 → 5557.8 MiB; mean MSPT 16.28 → 17.40 and tick P99 29.02 → 36.81 ms. Candidate REJECTED FOR PROMOTION. Never activate candidate G1 ratios on resumption by default.

## Fixes and flags

Kilt and Sortilege stream-closure implementations and scoped native evidence preserved. Bootstrap agent is explicit -javaagent, not auto-enabled. Sortilege and ModernFix/Kilt bridge configuration defaults OFF. Existing source config contains the full accepted/experimental defaults; do not replace QC snapshot with inferred defaults.

Horseman fast path is experimental/default OFF, with scoped geometry and native64 correctness. No reliable allocation improvement. BetterCaves noise patch, Kilt render-context patch, immutable model-variant cache, adaptive report scheduling and particle optimization remain OFF. Azure obsolete-key retirement remains opt-in/OFF. Retention adapters are exact-version guarded opt-ins; preserve each cohort's explicit flag manifests.

Source19 flags `smoothfix.experimental.closedPingWheelWorldMemoCleanup` and `smoothfix.experimental.closedSimplySwordsWorldMemoCleanup` both default OFF. Three repeated same-world disconnects plus alternate world, dimensions, native resource/recipe reload and saved exits passed for these two references only. Whole ClientWorld release did not pass; no working-set gain established.

Compat `LegacyRuntimeContext` uses a weak unscoped fallback and preserves strong nested ThreadLocal scopes. 23 contract assertions and native scoped validation passed. Stopped IntegratedServer witness 1 → 0; physical RAM benefit unestablished.

## Remaining defects and limits

Fresh post-fix MAT: ClientWorld 1, IntegratedServer 0; bounded reverse strong search depth12, 138537 nodes, no node-cap truncation. Remaining paths: Railways cameraStorage, EMI Loot preview mobFuture, Iris uniform supplier, Xaero radar entity and Presence Footsteps. Static aliases are not independent leaks; no blind clearing. Railways constructor rebuilds storage but current callbacks dereference it without null checks; lifecycle ordering remains unresolved.

Strict worldgen parity FAIL. Artifacts campsite has source-confirmed unseeded shuffle; other typed mismatches unresolved. 100-mob renderer workload FAIL, 300 NOT TESTED. Latest background diagnostic r4-mvDiag2 completed native recovery, replay finalization and natural exit. Eight recorded removal calls traverse Let Me Despawn's `yeetusCheckus` from checkDespawn; this does not turn the rendered workload into a pass. Raw diagnostic retained privately. No follow-up launched.

10-Wither scoped native coverage passed. Native64 original AI/ticking passed. Full particle/damage/coexistence and two-client multiplayer incomplete. Replay container validity passed; actual playback NOT TESTED. Native reload recipe IDs pass, not full recipe-semantic equivalence. Source19 foreground FPS not tested.

## Preserved artifacts and evidence

Local workspace: `[local preserved R4 workspace; see local pause manifest]`. Latest Smooth Fix JAR/source: `development-build19/` (JAR SHA256 d47be075d5ce5aa6865ad811c5f369170c2a40d91098264e61892ba87c7de7eb; source ZIP 12dc48d6d14022a39badf0beeb8f24a39ecddb1a26b4f1b3cd019aa55157bb31).

Compat JAR: `spellblade-runtime-context-r4/frozen/` (a23aaa0e941eac97f34c2bb75f900dc2948d41735183a9b023b2e0c5412bacb9), source `development-source.zip`, contracts/native reports alongside.

QC drivers: `runtime-fixture/src/`, `driver22-frozen` for lifecycle and `driver24-frozen` for removal callpaths; frozen source ZIPs and manifests preserved. Existing repo `verification/` contains source-controlled verification fixtures. Code being checkpointed matches frozen source19, verified in `verification/pause-source-inventory.json`.

Summaries: `statistics/`, `reports/`, `research/memoRoot1-closed-client-world-owners.json`, `THIRD_NON_BLOCKING_CHECKPOINT.md`, `KNOWN_RISKS_AND_UNTESTED.md`, `dashboard.html`. Raw sessions and failed attempts in `sessions/`; fresh heap `private-diagnostics/r4-memoRoot1/`. D-drive private heap/JFR evidence under isolated QC `qc-private-r3` and `qc-private-r4`. Raw evidence remains local and excluded from Git. Only reproducible derived indexes may be removed with a manifest; never remove the sole raw evidence of an unresolved defect.

## Resume only after a new user request

1. Read this handoff, latest pause result manifests, STATE and newest scoped acceptance reports; do not restart R1/R2/R3.
2. Verify clean process inventory, current Git branches and frozen artifact hashes. No branch creation or production deployment is implied.
3. QC instance ONLY: `D:\Games\.minecraft\versions\smooth-ody-bridge-qc`. Production `D:\Games\.minecraft\versions\smooth-ody` remains protected.
4. Java `C:\Program Files\Java\jdk-21.0.12\bin`, Xms512M/Xmx6G, ActiveProcessorCount4, G1 default ratios. Use exact cohort inputs/flags and fresh unique world/session labels; no overwrites.
5. Full-client gate: >=7 GiB available physical and >=14 GiB commit headroom. MAT offline gate >=7/9 GiB. One heavy job at a time; never close user apps to force headroom. Background first, GUI only when essential. No forced-focus FPS claims.
6. First remaining investigation: assess exact remaining ClientWorld lifecycle owners from fresh heap; preserve required active caches. Review the eight removal callpaths before redesigning the observer workload. Strict parity, particles and multiplayer remain open.
7. `R4_PAUSED.json` is the explicit no-auto-continuation marker. Remove only on a new resume instruction. No scheduled R4 task or live worker remains at final verification; no R5/release permitted by this handoff.

## Changed files at pause (before this handoff was added)

- `CONFIG_AND_STRESS_DEV.md`
- `HORIZON_TRACEABILITY_DEV.md`
- `PERFORMANCE_MEMORY_DEV.md`
- `README_DEV.md`
- `STRESS_TEST_GUIDE.txt`
- `TEST_RESULTS_HORIZON_DEV.md`
- `VALIDATION_HORIZON_DEV.json`
- `build.gradle`
- `src/main/java/org/marj4n/smooth_fix/SmoothFix.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedBenchmark.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedClient.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedPlan.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedStageInfo.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkAbilities.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkBaselineGate.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkClientReport.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkFinalization.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkProtocol.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkRecovery.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkRenderFocus.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkReportTransfer.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkSession.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/BloodMoonAdapter.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/CanonicalNbtHash.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/ClientBenchmark.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/ClientBenchmarkReports.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/GroundCollisionTerrain.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/NativeSpawnReadiness.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/NetworkInventoryProjection.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/RecoverySnapshot.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/StatusEffectRecovery.java`
- `src/main/java/org/marj4n/smooth_fix/benchmark/ThinPlantOcclusion.java`
- `src/main/java/org/marj4n/smooth_fix/bootstrap/agent/BootstrapLeakAgent.java`
- `src/main/java/org/marj4n/smooth_fix/bootstrap/agent/ConsumedClassStream.java`
- `src/main/java/org/marj4n/smooth_fix/config/SmoothFixConfig.java`
- `src/main/java/org/marj4n/smooth_fix/diagnostics/ClientDiagnostics.java`
- `src/main/java/org/marj4n/smooth_fix/diagnostics/ClientFrameProfiler.java`
- `src/main/java/org/marj4n/smooth_fix/diagnostics/ServerDiagnostics.java`
- `src/main/java/org/marj4n/smooth_fix/diagnostics/TimingSamples.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/SmoothFixMixinPlugin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/AzureCacheReloadMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/BenchmarkOwnedEntityRenderMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/BlockModelVariantMarkerMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/ClientFrameTimingMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/ClientWorldCacheLifecycleMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/InventoryAttributeButtonMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/client/KiltRenderContextMarkerMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/AccessoriesLegacyRecoveryMarkerMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/BenchmarkActorConversionMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/BetterCavesAllocationMarkerMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/HorsemanBoundsMarkerMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/KiltRegistryCacheMarkerMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/SortilegeResourceClosureMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/StoppedOwnerEarlyStartMixin.java`
- `src/main/java/org/marj4n/smooth_fix/mixin/common/StoppedOwnerTeardownMixin.java`
- `src/main/java/org/marj4n/smooth_fix/performance/AccessoriesLegacyReadBytecode.java`
- `src/main/java/org/marj4n/smooth_fix/performance/AtomicFilePublication.java`
- `src/main/java/org/marj4n/smooth_fix/performance/AzureObsoleteAnimationKeys.java`
- `src/main/java/org/marj4n/smooth_fix/performance/BoundedReportQueue.java`
- `src/main/java/org/marj4n/smooth_fix/performance/ClosedClientMemoOwners.java`
- `src/main/java/org/marj4n/smooth_fix/performance/FabricRenderContextOwner.java`
- `src/main/java/org/marj4n/smooth_fix/performance/HorsemanBoundsAudit.java`
- `src/main/java/org/marj4n/smooth_fix/performance/HorsemanBoundsBytecode.java`
- `src/main/java/org/marj4n/smooth_fix/performance/InventoryAccessoryButtons.java`
- `src/main/java/org/marj4n/smooth_fix/performance/KiltRegistryCacheAudit.java`
- `src/main/java/org/marj4n/smooth_fix/performance/KiltRegistryCacheBytecode.java`
- `src/main/java/org/marj4n/smooth_fix/performance/KiltRegistryCacheCounters.java`
- `src/main/java/org/marj4n/smooth_fix/performance/KiltRenderContextBytecode.java`
- `src/main/java/org/marj4n/smooth_fix/performance/LegacyAccessoryRecovery.java`
- `src/main/java/org/marj4n/smooth_fix/performance/LifecycleRetentionCleanup.java`
- `src/main/java/org/marj4n/smooth_fix/performance/ModelVariantBytecode.java`
- `src/main/java/org/marj4n/smooth_fix/performance/ModelVariantCache.java`
- `src/main/java/org/marj4n/smooth_fix/performance/NoiseArrayAllocation.java`
- `src/main/java/org/marj4n/smooth_fix/performance/NoiseArrayBytecode.java`
- `src/main/java/org/marj4n/smooth_fix/performance/RelevantDragonEntities.java`
- `src/main/java/org/marj4n/smooth_fix/performance/ReportIo.java`
- `src/main/java/org/marj4n/smooth_fix/performance/SortilegeResourceAudit.java`
- `src/main/java/org/marj4n/smooth_fix/performance/SortilegeResourceCounters.java`
- `src/main/java/org/marj4n/smooth_fix/performance/StoppedOwnerAdapters.java`
- `src/main/resources/smooth_fix.client.mixins.json`
- `src/main/resources/smooth_fix.common.mixins.json`
- `verification/AccessoryRecoveryBytecodeCheck.java`
- `verification/OwnedReportQueueCheck.java`
- `verification/ReportIoMicrobenchmark.java`
- `verification/runtime/verification/AdvancedClientCheck.java`
- `verification/runtime/verification/AdvancedRuntimeCheck.java`
- `verification/runtime/verification/AtomicFilePublicationCheck.java`
- `verification/runtime/verification/BatchReportCheck.java`
- `verification/runtime/verification/BenchmarkBaselineGateCheck.java`
- `verification/runtime/verification/BenchmarkRuntimeCheck.java`
- `verification/runtime/verification/CanonicalNbtHashCheck.java`
- `verification/runtime/verification/ClientRuntimeCheck.java`
- `verification/runtime/verification/ContinuationCheck.java`
- `verification/runtime/verification/FabricPackageAccessCheckLauncher.java`
- `verification/runtime/verification/KiltRegistryCacheBytecodeCheck.java`
- `verification/runtime/verification/NativeSpawnReadinessCheck.java`
- `verification/runtime/verification/NetworkInventoryProjectionCheck.java`
- `verification/runtime/verification/SnapshotCheck.java`
- `verification/runtime/verification/SortilegeResourceClosureCheck.java`
- `verification/runtime/verification/StatusEffectRecoveryCheck.java`
- `verification/runtime/verification/ThinPlantOcclusionCheck.java`
- `verification/runtime/verification/VerificationReflection.java`
