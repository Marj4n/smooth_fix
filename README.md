# Smooth Fix

Targeted compatibility/stability patches for Smooth Odyssey on Fabric 1.20.1.

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
  "obscureTooltipNullFood": true
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
