package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Announce real server-thread ownership before normal Fabric starting callbacks. */
@Mixin(value = MinecraftServer.class, priority = 2000)
public abstract class StoppedOwnerEarlyStartMixin {
    @Inject(method = "runServer", at = @At("HEAD"), require = 1)
    private void smoothfix$announceEarlyOwner(CallbackInfo ci) {
        org.marj4n.smooth_fix.performance.LifecycleRetentionCleanup.announceServerThreadStartup(
                (MinecraftServer) (Object) this);
    }
}
