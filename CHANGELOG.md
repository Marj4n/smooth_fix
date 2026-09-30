# Changelog

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
