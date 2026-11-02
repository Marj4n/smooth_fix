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
        if(mixinClassName.endsWith("HorsemanBoundsMarkerMixin"))return config.entityOptimization && Boolean.getBoolean("smoothfix.experimental.horsemanNonHorseBoundsFastPath") && org.marj4n.smooth_fix.performance.HorsemanBoundsAudit.supported();

        if(mixinClassName.endsWith("StoppedOwnerTeardownMixin")||mixinClassName.endsWith("StoppedOwnerEarlyStartMixin"))return java.util.stream.Stream.of("stoppedWorldMapCleanup","stoppedPacketHandlerCleanup","stoppedAdvancementListenerCleanup","stoppedSimpleNetworkingServerCleanup","stoppedPortingLibServerCleanup","stoppedFabricFakePlayerCleanup","stoppedCitadelServerCleanup","stoppedXaeroServerCleanup","stoppedFabricInventoryWrapperCleanup","stoppedDisenchantingServerCleanup","stoppedImmersiveMelodiesServerCleanup","stoppedNeruinaLevelCleanup","stoppedBclibConnectorCleanup","stoppedFabricFakeConnectionCleanup","stoppedXaeroConfigServerCleanup","stoppedPuffishDisposalCleanup","stoppedBiolithPlacementStateCleanup","stoppedBetterEndOreFunctionCleanup","stoppedCreateDamageSourceCleanup","stoppedQuestKillTaskCacheCleanup").anyMatch(key->Boolean.getBoolean("smoothfix.experimental."+key));
        if(mixinClassName.endsWith("AzureCacheReloadMixin"))return Boolean.getBoolean("smoothfix.experimental.azureObsoleteAnimationKeys") && FabricLoader.getInstance().isModLoaded("azurelib");
        if(mixinClassName.endsWith("KiltRegistryCacheMarkerMixin"))return config.chunkEfficiency && org.marj4n.smooth_fix.performance.KiltRegistryCacheAudit.requested(config.kiltModernFixRegistryCacheBridge) && org.marj4n.smooth_fix.performance.KiltRegistryCacheAudit.supported();
        if(mixinClassName.endsWith("SortilegeResourceClosureMixin"))return config.memoryOptimization && org.marj4n.smooth_fix.performance.SortilegeResourceAudit.requested(config.sortilegeReloadStreamClosureFix) && org.marj4n.smooth_fix.performance.SortilegeResourceAudit.supported();
        if(mixinClassName.endsWith("BlockModelVariantMarkerMixin"))return config.chunkEfficiency && config.memoryOptimization && config.immutableModelVariantCache;
        if(mixinClassName.endsWith("KiltRenderContextMarkerMixin"))return config.chunkEfficiency && config.memoryOptimization && config.kiltRenderContextAllocationFix && auditedVersion("kilt","1.0.0+1.20.1");
        if(!config.entityOptimization && (mixinClassName.endsWith("DragonScanMarkerMixin") || mixinClassName.endsWith("DragonSoundScanMarkerMixin") || mixinClassName.endsWith("MobEquipmentMarkerMixin") || mixinClassName.endsWith("FootstepTargetCacheMixin")))return false;
        if(mixinClassName.endsWith("BetterCavesAllocationMarkerMixin")) return config.chunkEfficiency && config.memoryOptimization && config.betterCavesNoiseAllocationFix && auditedVersion("bettercaves","1.20.1-Fabric-2.0.7");
        if (mixinClassName.endsWith("ResourceBarTemperatureMarkerMixin")) return config.resourceBarTemperatureLookupFix && auditedVersion("dynamic_resource_bars", "0.8.1");
        if (mixinClassName.endsWith("FootstepTargetCacheMixin")) return config.presenceFootstepTargetCacheFix && auditedVersion("presencefootsteps", "1.10.1+1.20.1");
        if (mixinClassName.endsWith("DragonSoundScanMarkerMixin")) return config.saintsDragonSoundScanFix && auditedVersion("saintsdragons", "0.9.85");

        if (mixinClassName.endsWith("MobEquipmentMarkerMixin")) return config.betterMobCombatEquipmentSlotFix && auditedVersion("bettermobcombat", "1.3.0");
        if (mixinClassName.endsWith("EnhancedCelestialsShaderPromptMixin")) return config.enhancedCelestialsShaderPromptSuppression && auditedVersion("enhancedcelestials2core", "1.0.3.3");

        if (mixinClassName.endsWith("AccessoryRenderMarkerMixin")) return config.accessoriesEmptyBufferFlushFix && auditedVersion("accessories", "1.0.0-beta.48+1.20.1");
        if (mixinClassName.endsWith("EmiTagCacheMarkerMixin") || mixinClassName.endsWith("EmiTooltipQueryMarkerMixin")) return config.emiSearchSafetyFix && auditedVersion("emi", "1.1.24+1.20.1+fabric");

        if (mixinClassName.endsWith("EmiBenchmarkSearchMixin") || mixinClassName.endsWith("EmiBenchmarkWorkerMixin")) return config.diagnostics && config.profilingTelemetry && loaded("emi");
        if (mixinClassName.endsWith("ServerChunkFutureAccess") || mixinClassName.endsWith("ChunkBenchmarkTimingMixin") || mixinClassName.endsWith("FeatureBenchmarkTimingMixin")
                || mixinClassName.endsWith("ExplosionBenchmarkMixin") || mixinClassName.endsWith("BenchmarkDropOwnershipMixin") || mixinClassName.endsWith("BenchmarkActorConversionMixin")
                || mixinClassName.endsWith("HandledScreenPositionAccess") || mixinClassName.endsWith("BenchmarkScreenRenderMixin")
                || mixinClassName.endsWith("BenchmarkOwnedEntityRenderMixin") || mixinClassName.endsWith("BenchmarkMouseAccess") || mixinClassName.endsWith("BenchmarkTooltipMixin") || mixinClassName.endsWith("BenchmarkInputMixin") || mixinClassName.endsWith("BenchmarkAutoJumpMixin")) return config.diagnostics && config.profilingTelemetry;

        if (mixinClassName.endsWith("BewitchmentSigilMarkerMixin")) return config.bewitchmentSigilSearchFix && loaded("bewitchment");
        if (mixinClassName.endsWith("TrinketGroupRegexMixin")) return config.trinketsRegexAllocationFix && loaded("tclayer");
        if (mixinClassName.endsWith("TrinketSnapshotMarkerMixin")) return config.trinketsSnapshotAllocationFix && loaded("tclayer");
        if (mixinClassName.endsWith("DragonScanMarkerMixin")) return config.saintsDragonEntityScanFix && loaded("saintsdragons");
        if (mixinClassName.endsWith("ModelPartAllocationMixin")) return config.modelRotationAllocationFix;
        if (mixinClassName.endsWith("ClientFrameTimingMixin")) return config.diagnostics && config.profilingTelemetry;
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

        if(mixinClassName.endsWith("InventoryAttributeButtonMixin")) {
            return config.hideInventoryAttributesButton && auditedVersion("zenith_attributes","1.0.3");
        }
        if(mixinClassName.endsWith("AccessoriesLegacyRecoveryMarkerMixin"))return config.accessoriesLegacyRecoveryFix && auditedVersion("accessories","1.0.0-beta.48+1.20.1");
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
        if(mixinClassName.endsWith("HorsemanBoundsMarkerMixin")) {
            var resolver=loader.getMappingResolver();
            String horse=resolver.mapClassName("intermediary","net.minecraft.class_1496").replace('.','/'),boat=resolver.mapClassName("intermediary","net.minecraft.class_1690").replace('.','/'),player=resolver.mapClassName("intermediary","net.minecraft.class_1657").replace('.','/'),box=resolver.mapClassName("intermediary","net.minecraft.class_238").replace('.','/');
            int changed=org.marj4n.smooth_fix.performance.HorsemanBoundsBytecode.apply(targetClass,horse,boat,player,box);
            org.marj4n.smooth_fix.performance.HorsemanBoundsAudit.applied(changed);
            SmoothFix.LOGGER.info("Horseman audited non-horse bounds fast path: {} callback(s) changed; original horse/boat/config behavior retained",changed);
        }
        if(mixinClassName.endsWith("KiltRegistryCacheMarkerMixin")) {
            var resolver=loader.getMappingResolver();
            String state=resolver.mapClassName("intermediary","net.minecraft.class_2680").replace('.','/');
            String init=resolver.mapMethodName("intermediary","net.minecraft.class_2680","method_26200","()V");
            boolean applied=org.marj4n.smooth_fix.performance.KiltRegistryCacheBytecode.apply(targetClass,state,init);
            SmoothFix.LOGGER.info("Kilt/ModernFix registry cache bridge: {}",applied?"audited eager cache loop delegated to native lazy invalidation":"unsupported modified callback; original behavior retained");
        }
        if(mixinClassName.endsWith("BlockModelVariantMarkerMixin")) {
            var resolver=loader.getMappingResolver();
            String id=resolver.mapMethodName("intermediary","net.minecraft.class_773","method_3336","(Lnet/minecraft/class_2960;Lnet/minecraft/class_2680;)Lnet/minecraft/class_1091;");
            String serializer=resolver.mapMethodName("intermediary","net.minecraft.class_773","method_3338","(Ljava/util/Map;)Ljava/lang/String;");
            boolean applied=org.marj4n.smooth_fix.performance.ModelVariantBytecode.apply(targetClass,id,serializer);
            SmoothFix.LOGGER.info("Immutable block-model variant cache: {}",applied?"audited serializer call optimized":"unsupported expression; upstream retained");
        }
        if(mixinClassName.endsWith("KiltRenderContextMarkerMixin")) {
            boolean applied=org.marj4n.smooth_fix.performance.KiltRenderContextBytecode.apply(targetClass);
            SmoothFix.LOGGER.info("Kilt weighted-model context allocations: {}",applied?"two audited resets optimized":"unsupported expression; upstream retained");
        }
        if(mixinClassName.endsWith("BetterCavesAllocationMarkerMixin")) {
            boolean applied=org.marj4n.smooth_fix.performance.NoiseArrayBytecode.apply(targetClass);
            SmoothFix.LOGGER.info("Better Caves redundant noise allocations: {}",applied?"two audited sites optimized":"unsupported expression; upstream retained");
        }
        if(mixinClassName.endsWith("AccessoriesLegacyRecoveryMarkerMixin")) {
            var resolver=loader.getMappingResolver();
            String inventory=resolver.mapClassName("intermediary","net.minecraft.class_1277").replace('.', '/');
            String nbt=resolver.mapClassName("intermediary","net.minecraft.class_2499").replace('.', '/');
            String read=resolver.mapMethodName("intermediary","net.minecraft.class_1277","method_7659","(Lnet/minecraft/class_2499;)V");
            int patched=org.marj4n.smooth_fix.performance.AccessoriesLegacyReadBytecode.apply(targetClass,inventory,nbt,read);
            if(patched!=1)throw new IllegalStateException("Audited Accessories legacy recovery expression changed; refusing potentially lossy migration");
            SmoothFix.LOGGER.info("Accessories removed-slot item/cosmetic recovery capacity fixed: {} audited expression",patched);
        }
        if (mixinClassName.endsWith("ResourceBarTemperatureMarkerMixin")) {
            String player = loader.getMappingResolver().mapClassName("intermediary", "net.minecraft.class_1657").replace('.', '/');
            SmoothFix.LOGGER.info("Resource Bars temperature lookup: {} audited method(s) cached",
                    org.marj4n.smooth_fix.performance.ResourceBarTemperatureBytecode.apply(targetClass, player));
        }
        if (mixinClassName.endsWith("DragonSoundScanMarkerMixin")) {
            String client = loader.getMappingResolver().mapClassName("intermediary", "net.minecraft.class_310").replace('.', '/');
            String world = loader.getMappingResolver().mapClassName("intermediary", "net.minecraft.class_638").replace('.', '/');
            String entities = loader.getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_638", "method_18112", "()Ljava/lang/Iterable;");
            SmoothFix.LOGGER.info("Dragon sound entity index ({}): {} audited scan(s) optimized", targetClassName,
                    org.marj4n.smooth_fix.performance.DragonSoundScanBytecode.apply(targetClass, client, world, entities));
        }
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
