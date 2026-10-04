package org.marj4n.smooth_fix.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
import org.objectweb.asm.tree.ClassNode;
import org.marj4n.smooth_fix.performance.ModelRotationBytecode;
import org.marj4n.smooth_fix.performance.WorkerPoolBytecode;
import org.marj4n.smooth_fix.performance.BewitchmentSearchBytecode;
import org.marj4n.smooth_fix.performance.DragonScanBytecode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps every compatibility mixin conditional.
 *
 * Smooth Fix is allowed to stay installed even when one or more target mods are absent.
 * This also means an upstream mod can be removed without leaving a hard class dependency.
 */
public final class SmoothFixMixinPlugin implements IMixinConfigPlugin {
    private final FabricLoader loader = FabricLoader.getInstance();

    @Override
    public void onLoad(String mixinPackage) {
        // Force config creation/loading before shouldApplyMixin starts being queried.
        SmoothFixConfig.get();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        SmoothFixConfig config = SmoothFixConfig.get();

        if (mixinClassName.endsWith("MobEquipmentMarkerMixin")) return config.betterMobCombatEquipmentSlotFix && auditedVersion("bettermobcombat", "1.3.0");
        if (mixinClassName.endsWith("EnhancedCelestialsShaderPromptMixin")) return config.enhancedCelestialsShaderPromptSuppression && auditedVersion("enhancedcelestials2core", "1.0.3.3");

        if (mixinClassName.endsWith("AccessoryRenderMarkerMixin")) return config.accessoriesEmptyBufferFlushFix && auditedVersion("accessories", "1.0.0-beta.48+1.20.1");
        if (mixinClassName.endsWith("EmiTagCacheMarkerMixin") || mixinClassName.endsWith("EmiTooltipQueryMarkerMixin")) return config.emiSearchSafetyFix && auditedVersion("emi", "1.1.24+1.20.1+fabric");

        if (mixinClassName.endsWith("EmiBenchmarkSearchMixin") || mixinClassName.endsWith("EmiBenchmarkWorkerMixin")) return config.diagnostics && loaded("emi");
        if (mixinClassName.endsWith("ServerChunkFutureAccess") || mixinClassName.endsWith("ChunkBenchmarkTimingMixin") || mixinClassName.endsWith("FeatureBenchmarkTimingMixin")
                || mixinClassName.endsWith("ExplosionBenchmarkMixin") || mixinClassName.endsWith("BenchmarkDropOwnershipMixin")
                || mixinClassName.endsWith("HandledScreenPositionAccess") || mixinClassName.endsWith("BenchmarkScreenRenderMixin")
                || mixinClassName.endsWith("BenchmarkMouseAccess") || mixinClassName.endsWith("BenchmarkTooltipMixin") || mixinClassName.endsWith("BenchmarkInputMixin") || mixinClassName.endsWith("BenchmarkAutoJumpMixin")) return config.diagnostics;

        if (mixinClassName.endsWith("BewitchmentSigilMarkerMixin")) return config.bewitchmentSigilSearchFix && loaded("bewitchment");
        if (mixinClassName.endsWith("TrinketGroupRegexMixin")) return config.trinketsRegexAllocationFix && loaded("tclayer");
        if (mixinClassName.endsWith("TrinketSnapshotMarkerMixin")) return config.trinketsSnapshotAllocationFix && loaded("tclayer");
        if (mixinClassName.endsWith("DragonScanMarkerMixin")) return config.saintsDragonEntityScanFix && loaded("saintsdragons");
        if (mixinClassName.endsWith("ModelPartAllocationMixin")) return config.modelRotationAllocationFix;
        if (mixinClassName.endsWith("ClientFrameTimingMixin")) return config.diagnostics;
        if (mixinClassName.endsWith("BackgroundWorkerBudgetMixin")) return config.backgroundWorkerLimit > 0;
        if (mixinClassName.endsWith("ConfirmedTeleportAnchorMixin")) {
            return config.confirmedTeleportAnchorFix && loader.getEnvironmentType() == EnvType.SERVER;
        }

        if (mixinClassName.endsWith("EntityRotationGuardMixin")) {
            return config.invalidEntityRotationGuard && loader.getEnvironmentType() == EnvType.SERVER;
        }

        if (mixinClassName.endsWith("EmiCompatPluginMixin")) {
            boolean apply = config.emiCompatSpellEngine
                    && loaded("emi")
                    && loaded("emicompat")
                    && loaded("spell_engine");
            logDecision("EMI Compat / Spell Engine", apply);
            return apply;
        }

        if (mixinClassName.endsWith("TumbleweedClientInitMixin")) {
            boolean apply = config.emiLootTumbleweed
                    && loaded("emi_loot")
                    && loaded("tumbleweed");
            logDecision("EMI Loot / Tumbleweed", apply);
            return apply;
        }

        if (mixinClassName.endsWith("ObscureTooltipFoodMixin")) {
            boolean apply = config.obscureTooltipNullFood
                    && loaded("obscure_api");
            logDecision("Obscure API tooltip null-food guard", apply);
            return apply;
        }

        if (mixinClassName.endsWith("EmiRepairIngredientMixin")) {
            boolean apply = config.emiRepairEmptyIngredient
                    && loaded("emi");
            logDecision("EMI zero-stack repair ingredient guard", apply);
            return apply;
        }

        return true;
    }

    private boolean loaded(String modId) {
        return loader.isModLoaded(modId);
    }

    private boolean auditedVersion(String modId, String version) {
        return loader.getModContainer(modId).map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals(version)).orElse(false);
    }

    private static void logDecision(String patchName, boolean enabled) {
        if (enabled) {
            SmoothFix.logPatchOnce(
                    "mixin:" + patchName,
                    patchName + " patch enabled"
            );
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith("MobEquipmentMarkerMixin")) {
            String slot = loader.getMappingResolver().mapClassName("intermediary", "net.minecraft.class_1304").replace('.', '/');
            SmoothFix.LOGGER.info("Better Mob Combat equipment slot guard: {} audited handler(s) optimized",
                    org.marj4n.smooth_fix.performance.MobEquipmentBytecode.apply(targetClass, slot));
        }
        if (mixinClassName.endsWith("AccessoryRenderMarkerMixin")) {
            String owner = loader.getMappingResolver().mapClassName("intermediary", "net.minecraft.class_4597$class_4598").replace('.', '/');
            String draw = loader.getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_4597$class_4598", "method_22993", "()V");
            SmoothFix.LOGGER.info("Accessories empty-slot buffer guard: {} upstream expression(s) replaced",
                    org.marj4n.smooth_fix.performance.AccessoryRenderBytecode.apply(targetClass, owner, draw));
        }
        if (mixinClassName.endsWith("EmiTagCacheMarkerMixin") || mixinClassName.endsWith("EmiTooltipQueryMarkerMixin")) {
            SmoothFix.LOGGER.info("EMI search safety: {} upstream expression(s) guarded",
                    org.marj4n.smooth_fix.performance.EmiSearchSafetyBytecode.apply(targetClass));
        }
        if (mixinClassName.endsWith("TrinketSnapshotMarkerMixin")) {
            SmoothFix.LOGGER.info("Trinkets eager inventory snapshot: {} verified expression(s) optimized",
                    org.marj4n.smooth_fix.performance.TrinketSnapshotBytecode.apply(targetClass));
        }
        if (mixinClassName.endsWith("BewitchmentSigilMarkerMixin")) {
            int changed = BewitchmentSearchBytecode.apply(targetClass);
            SmoothFix.LOGGER.info("Bewitchment sigil search: {} verified expression(s) optimized", changed);
        }
        if (mixinClassName.endsWith("DragonScanMarkerMixin")) {
            String owner=loader.getMappingResolver().mapClassName("intermediary","net.minecraft.class_638").replace('.','/');
            String name=loader.getMappingResolver().mapMethodName("intermediary","net.minecraft.class_638","method_18112","()Ljava/lang/Iterable;");
            SmoothFix.LOGGER.info("Saints Dragons loaded-entity index: {} verified expression(s) optimized",DragonScanBytecode.apply(targetClass,owner,name));
        }
        if (mixinClassName.endsWith("BackgroundWorkerBudgetMixin")) {
            int changed = WorkerPoolBytecode.apply(targetClass);
            SmoothFix.LOGGER.info("Background worker budget: {} construction expression(s) patched after upstream mixins",changed);
        }

        if (mixinClassName.endsWith("ModelPartAllocationMixin")) {
            String matrixOwner = loader.getMappingResolver().mapClassName("intermediary", "net.minecraft.class_4587").replace('.', '/');
            String multiplyName = loader.getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_4587",
                    "method_22907", "(Lorg/joml/Quaternionf;)V");
            int changed = ModelRotationBytecode.apply(targetClass, matrixOwner, multiplyName);
            SmoothFix.LOGGER.info("Model rotation allocation fix: {} expression(s) replaced; unchanged if upstream already optimized", changed);
        }
    }
}
