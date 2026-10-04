package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The pack deliberately omits this optional addon, while keeping native lunar events. */
@Pseudo
@Mixin(targets = "dev.corgitaco.enhancedcelestials2core.client.AddonPrompts", remap = false)
public abstract class EnhancedCelestialsShaderPromptMixin {
    @Inject(method = "notifyIfMissingShaderSupport", at = @At("HEAD"), cancellable = true, require = 0)
    private static void smoothfix$skipShaderSuggestion(PlayerEntity player, boolean force, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
