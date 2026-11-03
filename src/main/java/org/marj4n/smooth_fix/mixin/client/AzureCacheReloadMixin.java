package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public abstract class AzureCacheReloadMixin {
    @Inject(
            method = "reloadResources()Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"),
            require = 1)
    private void smoothfix$afterSuccessfulReload(
            CallbackInfoReturnable<CompletableFuture<Void>> result) {
        org.marj4n.smooth_fix.performance.AzureObsoleteAnimationKeys.afterReload(
                (MinecraftClient) (Object) this, result.getReturnValue());
    }
}
