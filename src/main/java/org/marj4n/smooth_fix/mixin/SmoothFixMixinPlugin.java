package org.marj4n.smooth_fix.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
import org.objectweb.asm.tree.ClassNode;
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
    }
}
