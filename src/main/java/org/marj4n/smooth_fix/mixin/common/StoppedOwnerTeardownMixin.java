package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class StoppedOwnerTeardownMixin {
    @Inject(
            method = "runServer",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/MinecraftServer;shutdown()V",
                            shift = At.Shift.AFTER),
            require = 1)
    private void smoothfix$afterNativeShutdown(CallbackInfo ci) {
        org.marj4n.smooth_fix.performance.LifecycleRetentionCleanup.afterServerShutdown(
                (MinecraftServer) (Object) this);
    }
}
