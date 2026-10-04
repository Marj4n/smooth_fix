from pathlib import Path
import json, sys
root=Path(__file__).resolve().parents[1]
def text(p): return (root/p).read_text()
checks={
 'advanced_command': 'literal("advanced")' in text('src/main/java/org/marj4n/smooth_fix/benchmark/ServerBenchmark.java'),
 'advanced_server_installed': 'AdvancedBenchmark.install()' in text('src/main/java/org/marj4n/smooth_fix/SmoothFix.java'),
 'advanced_client_installed': 'AdvancedClientBenchmark.install()' in text('src/main/java/org/marj4n/smooth_fix/diagnostics/ClientDiagnostics.java'),
 'selected_slot_s2c': 'UpdateSelectedSlotS2CPacket' in text('src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkRecovery.java'),
 'full_inventory_nbt': 'writeNbt(new NbtList())' in text('src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkRecovery.java') and 'readNbt(root.getList("Inventory", 10))' in text('src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkRecovery.java'),
 'close_handled_screen': 'closeHandledScreen()' in text('src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkRecovery.java'),
 'pal_flying': '"FLYING"' in text('src/main/java/org/marj4n/smooth_fix/benchmark/BenchmarkPlayerAbilities.java'),
 'damage_counter_mixin': 'BenchmarkDamageCounterMixin' in text('src/main/resources/smooth_fix.common.mixins.json') and 'cir.getReturnValue()' in text('src/main/java/org/marj4n/smooth_fix/mixin/common/BenchmarkDamageCounterMixin.java'),
 'emi_search_thread': 'searchThread' in text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedClientBenchmark.java'),
 'emi_recipe_screen': 'dev.emi.emi.screen.RecipeScreen' in text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedClientBenchmark.java'),
 'container_quick_move': 'SlotActionType.QUICK_MOVE' in text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedClientBenchmark.java'),
 'inventory_swap': 'SlotActionType.SWAP' in text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedClientBenchmark.java'),
 'selected_slot_c2s': 'UpdateSelectedSlotC2SPacket' in text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedClientBenchmark.java'),
 'chunk_before_height': text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedBenchmark.java').find('WorldChunk chunk = world.getChunk(cx, cz)') < text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedBenchmark.java').find('world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)'),
 'post_restore_compare': 'recoveryVerification' in text('src/main/java/org/marj4n/smooth_fix/benchmark/AdvancedBenchmark.java'),
 'guide_updated': 'Belum mengukur struktur world asli' not in text('PANDUAN_STRESS_TEST.txt') and '/smoothfix stress advanced' in text('PANDUAN_STRESS_TEST.txt'),
 'build_marker': '1.0.5-perf-r2-advanced1' in text('src/main/java/org/marj4n/smooth_fix/SmoothFix.java'),
}
print(json.dumps(checks,indent=2))
failed=[k for k,v in checks.items() if not v]
if failed:
 print('FAIL:', ', '.join(failed), file=sys.stderr); sys.exit(1)
print('PASS advanced source contract checks:', len(checks), '/', len(checks))
