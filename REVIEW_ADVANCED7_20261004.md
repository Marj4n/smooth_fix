# Advanced7 batch review — October 4, 2026

Public version: **1.0.5-1.20.1**. Source marker: `1.0.5-perf-r2-advanced7`.
This revision is uncompiled and untested in Minecraft. No new FPS gain is claimed.

## Supplied Advanced6 run

Run `ef5f75d6-20e5-4b20-a06a-7776f83a959f` completed with 20 passes, two failures and two optional skips.
Inventory, EMI search/recipe and both-side recovery passed. Nether and End failed
real movement coverage: respectively 80.56/189.65 blocks walked, but only
7.46/4.71 blocks maximum displacement. Repeated loops do not count as coverage.
The mandatory village search remains 10,000, 20,000, 30,000 blocks and onwards;
optional structures retain a separate timeout. No validation threshold is lowered.

The recorded client used render distance 10, simulation distance 5, cap 120,
fast graphics, decreased particles and a 1920 x 1009 framebuffer.
The new profiles use render distance **12** on both clients. This change adds
world-rendering work compared with the supplied measurement.

Blood Moon averaged about 99 FPS, with p95 16.49 ms and p99 23.56 ms. Mean
pre-present work was 8.53 ms. Server callback work averaged 27.14 ms, p95 51.33 ms;
64 of 1,200 callbacks exceeded 50 ms. A shader-only explanation is unsupported.
Young/concurrent GC time was 276/54 ms; old GC time was zero in this stage.
RAM/heap settings are not changed by the patch installer.

| Stage | Interval-based average FPS | p95 ms | p99 ms | Maximum ms | Client result |
| --- | ---: | ---: | ---: | ---: | --- |
| real_world_panorama | 115.6 | 9.33 | 10.61 | 407.60 | passed |
| loaded_terrain_route | 115.3 | 9.92 | 12.39 | 65.07 | passed |
| new_terrain_route | 111.7 | 11.72 | 14.69 | 85.74 | passed |
| revisit_same_terrain | 115.7 | 9.74 | 11.33 | 41.14 | passed |
| real_village | 108.1 | 12.79 | 17.09 | 62.11 | passed |
| natural_chest_or_fixture | 112.0 | 10.96 | 15.20 | 82.08 | passed |
| lootr_personal_chest | 115.4 | 9.60 | 13.22 | 63.75 | passed |
| inventory_open_tooltips | 104.3 | 13.78 | 19.79 | 156.85 | passed |
| emi_query_latency | 108.5 | 12.23 | 15.45 | 115.97 | passed |
| emi_recipe_navigation | 114.7 | 9.64 | 12.12 | 94.80 | passed |
| terrain_combat_32 | 110.3 | 11.63 | 17.78 | 60.01 | passed |
| terrain_effects_64 | 105.8 | 13.64 | 21.62 | 41.89 | passed |
| native_blood_moon | 99.0 | 16.49 | 23.56 | 51.71 | passed |
| terrain_wither_10 | 104.2 | 14.82 | 17.46 | 40.25 | passed |
| terrain_tnt_16 | 103.5 | 14.95 | 17.41 | 32.89 | passed |
| native_block_breaks | 110.3 | 12.34 | 14.57 | 31.25 | passed |
| rain_and_particles | 102.3 | 15.97 | 20.95 | 37.62 | passed |
| real_world_teleports | 108.9 | 12.90 | 16.53 | 198.51 | passed |
| nether_terrain | 114.9 | 9.68 | 13.50 | 144.90 | failed_workload_verification |
| end_terrain | 116.2 | 9.21 | 11.22 | 215.44 | failed_workload_verification |
| mod_structure_ad_astra:oil_well | 113.1 | 10.99 | 13.62 | 53.36 | passed |
| mod_structure_alexscaves:abyssal_ruins | 117.7 | 9.11 | 9.34 | 89.77 | failed_workload_verification |
| mod_structure_alexscaves:acid_pit | 118.0 | 9.13 | 9.34 | 40.29 | failed_workload_verification |
| world_save | 111.1 | 11.70 | 14.08 | 57.35 | passed |

## Code changes

- Better Mob Combat 1.3.0's merged `pre_getItemBySlot` handler receives an early
  return for every slot except its existing OFFHAND constant. The upstream method
  only changes the offhand return value, yet performs blacklist and two weapon
  registry lookups for armor and main-hand reads. Offhand processing, combat AI,
  equipment contents and spawn counts remain unchanged. The bytecode adapter
  checks the slot comparison, one blacklist call, two weapon calls, one return-value
  call and the exact installed mod version; changed expressions remain untouched.
- Enhanced Celestials Core 1.0.3.3: optional shader-addon suggestion returns
  'no prompt sent' when `enhancedCelestialsShaderPromptSuppression` is enabled.
  This suppresses a prompt; it is not a Fabric switch that unloads a present JAR.
  Core/default lunar events and Blood Moon spawning remain installed and active.
- Nether/End route scoring penalizes recently visited cells and favors movement
  away from the stage origin. The heading persists instead of rotating after each
  seven blocks walked. Visit history is bounded to 512 cells. Respawn replanning
  retains the original navigation origin. Native collisions, swimming, ledge
  braking and real movement validation remain in effect. Lookahead stops at the
  next waypoint rather than extrapolating a straight line past a safe corner.
- Frame recordings use `diagnosticTargetFps` (PC 120, mobile 30), report their
  actual budget and count target-budget overages per second. The existing 8.33 ms
  timeline counters remain separately available. This does not raise FPS.
- Gradle's deprecated `filteringCharset` method syntax becomes a property assignment.
- Previous Accessories, EMI, tooltip, continuation and recovery fixes are retained.

## Pack changes

Only CurseForge project 1621851 / file 8798082 (the optional Enhanced Celestials
shader support addon) is removed from each manifest. The obsolete Smooth Fix
binary is replaced by complete Advanced7 source and local preparation scripts;
no newly built mod binary is bundled. All other original manifest entries and
resource files remain intact. Every profile has 402 retained CurseForge entries.

The visual presets disable shaders, dynamic lighting, ambient occlusion, entity
shadows, particles, animated textures and rendered precipitation; use fast leaves
and weather; and remove FancyMenu theme rounding/blur. They reduce Xaero cave
mapping, radar and map lighting while keeping maps, normal map updates and
waypoints. UI buttons and their actions remain intact. This is a visual tradeoff,
authorized for this batch, rather than a reduction of normal mob AI or spawning.

PC starts in a 1280 x 720 window to reduce pixel rendering. Mobile resolution must
be set in the Android launcher's resolution controls; start at 50%. Neither the
Minecraft config nor the desktop installer can guarantee Android launcher scaling.
Client render distance remains 12; server view distance remains 12. The original
server simulation distance is retained. Existing launcher RAM arguments are retained.

The base exports are the retained PC/server perf_r2 and mobile 0.1.0 exports,
not a new export captured from the user's current instance. For an existing
installation, use the merging installer so additional mods and updated settings
are retained. Do not replace an existing mods directory with the old export.

## Evidence and limits

The BMC 1.3.0 upstream source was read directly from its author's repository:
https://github.com/Thelnfamous1/Better-Mob-Combat/blob/1.20.1/common/src/main/java/me/Thelnfamous1/bettermobcombat/mixin/MobMixin_AttackLogic.java
Blob SHA: 2787cb7e2880f39ee5332ce83bc70bde1bd6135c. The repository's version is 1.3.0.
The local EC Core JAR confirms the optional shader mod ID, prompt target and
`(PlayerEntity, boolean) -> boolean` descriptor. Config field names were read from
the retained packs; no invented third-party 'disable addon' setting is supplied.

Slow-frame stacks include Better Mob Combat, Accessories/Trinkets, FancyMenu,
Xaero and native render/driver work. Bounded stack estimates have error bounds;
they are observations, not CPU percentages or a GPU profiler. Not every observed
stack can safely be patched without inspecting its exact implementation.

PC 120 FPS and mobile 30 FPS are targets requiring a new run on actual hardware.
Do not treat source/archive validation, average FPS or passing gameplay stages as
proof of stable frame pacing. No Gradle build, dependency download, Java compile,
Minecraft run or phone test was performed for this revision.
