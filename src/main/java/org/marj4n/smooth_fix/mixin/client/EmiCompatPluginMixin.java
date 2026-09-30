package org.marj4n.smooth_fix.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.SmoothFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * EMI Compat 1.1.3 ships an old Spell Engine compatibility module.
 *
 * On Spell Engine 1.10.x that module calls the removed
 * net.spell_engine.internals.SpellRegistry API and throws NoSuchMethodError / NoClassDefFoundError
 * while EMI is rebuilding recipes.
 *
 * Modern Spell Engine already registers its own native EMI plugin, so the safest compatibility
 * fix is to make EMI Compat skip only its legacy Spell Engine branch while leaving all of its
 * other compatibility integrations untouched.
 */
@Pseudo
@Mixin(targets = "com.mervyn.emicompat.EmiCompatPlugin", remap = false)
public abstract class EmiCompatPluginMixin {

    @Redirect(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/loader/api/FabricLoader;isModLoaded(Ljava/lang/String;)Z",
                    remap = false
            ),
            require = 0
    )
    private boolean smoothFix$skipLegacySpellEngineCompat(FabricLoader loader, String modId) {
        if ("spell_engine".equals(modId)) {
            SmoothFix.logPatchOnce(
                    "emi_compat_spell_engine",
                    "Skipped EMI Compat 1.1.3 legacy Spell Engine integration; modern Spell Engine provides its own EMI plugin."
            );
            return false;
        }

        return loader.isModLoaded(modId);
    }
}
