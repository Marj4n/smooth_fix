package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.client.option.KeyBinding;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-time override avoids repeated setPressed toggling StickyKeyBinding. */
@Mixin(KeyBinding.class)
public abstract class BenchmarkInputMixin {
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void smoothfix$benchmarkInput(CallbackInfoReturnable<Boolean> cir) {
        Boolean pressed = AdvancedClient.controlledKey((KeyBinding)(Object)this);
        if (pressed != null) cir.setReturnValue(pressed);
    }
}
