# Development configuration and reproducible stress checks

Public version and approved1.0.5 release stay unchanged. Install only in a copied disposable instance; never put a verification-only JAR in a normal pack. Retain the original mods/config/options/world snapshot.

## New Smooth Fix keys (existing config/smooth_fix.json)

| Key | Default | Scope |
|---|---:|---|
| backgroundReportIo | true | Own benchmark JSON snapshots only; stage writes asynchronous, initial/final/recovery durable. |
| adaptiveReportScheduling | false | Experimental bounded frame-pressure deferral; no effect until enabled. |
| reportQueueCapacity | 4 | Pending distinct document paths, clamped1..16. One in-flight job separately. |
| reportQueueMiB | 32 | Estimated retained queue snapshots, clamped1..128MiB; not a JVM heap limit. |
| reportMaximumDeferralMillis | 250 | Nonurgent pressure deferral bound, clamped0..1000ms; critical writes bypass. |
| accessoriesLegacyRecoveryFix | true | Audited Accessories beta48 removed-slot inventory capacity fix; preserves actual/cosmetic orphan items. |
| hideRedundantAccessoryButtons | true | Removes the exact overlapping Accessories native field widget and CuriosButton only; side tabs/keybinds preserved. |
| hideInventoryAttributesButton | true | Exact Zenith Attributes Renewed1.0.3 native enableAttributesGui UI flag; attributes/gameplay unchanged. |

Oversized valid reports drain older snapshots and write synchronously on owner thread. This preserves full diagnostics with bounded queue retention; it can stall the caller. Unsupported mutable/cyclic input is rejected, not silently stringified. I/O failures are logged and durable checkpoints throw. Background serialization never reads Minecraft entities/worlds. Shutdown has a5s deadline. Existing C2ME/Sodium pools and game settings are untouched.

## Existing benchmark, not a second controller

Use the existing `/smoothfix stress advanced start world-copy` in an operator-enabled disposable world. Configuration remains `config/smooth_fix/advanced_benchmark.json`. The existing default is21 stages (not20): panorama, warm route, cold route, revisit, village, chest, Lootr, inventory/tooltips, EMI search/recipe,32-mob combat,64 effects, bloodmoon,10 withers,16TNT, breaking, weather, teleport, Nether, End, save. Optional mod structures can add stages. Native world mutation belongs only in the disposable copy. Preserve pending/skipped/timeout outcomes; never relabel a skipped stage as a pass.

For honest A/B use the same Java/heap/mods/configs/shader/options, RD12, saved-world snapshot and route/seed. Only Smooth Fix JAR differs; Classes must stay constant. Run warmups and alternating repeated pairs. Cold routes require fresh terrain in independently reset identical worlds. Capture cap/vsync, focus/minimization, DynamicFPS, shader compilation, hardware, recording load, GC and thermal conditions. Do not infer120FPS capability from a60FPS-capped run.

The actual advanced controller waits for Minecraft focus and aborts a measured stage when focus is lost. Hidden background clients cannot satisfy that acceptance gate. Do not spoof focus or invent a completed full-suite result. A passive `/smoothfixc profile 30` is a limited stationary sample, not equivalent to full traversal/UI/combat benchmark. Frame1%/0.1% lows use interval distributions and become reservoir approximations after8192 samples. GPU timestamps, exact allocation totals and RSS are not fabricated when unavailable.

## Correctness checks

Test baseline then candidate from the same save: own/wild/foreign summon targeting; Warden heartbeat/roar/sonic sound; mute/unmute, reload, ownership loss; combat/NoAI/order state unchanged. Compare actual accessory menu refs `(slotName,index,isCosmetic)`, correct-item insertion/removal/shift-click, real and cosmetic inventories, save/reload. Ring indices0/1 and Aether/RPG namespaces are distinct capacity, not deletable duplicates. Zero-capacity legacy Homunculus bindings remain migration tombstones and never usable slots. Retired Homunculus items must retain counts and all NBT, including cosmetic and overflow stacks, with no second recovery duplication. Verify dimension transition/disconnect cache invalidation and repeated save/reopen with stable entity UUID counts. Two actual clients are required to claim multiplayer visual verification.
