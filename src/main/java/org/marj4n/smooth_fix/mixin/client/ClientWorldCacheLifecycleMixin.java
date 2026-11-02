package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 1.20.1 setWorld is the ownership boundary for these client-world caches. */
@Mixin(MinecraftClient.class)
public abstract class ClientWorldCacheLifecycleMixin {
    // joinWorld/disconnect assign MinecraftClient.world before calling private setWorld.
    // Capture the previous owner at their real entry boundary, not after reassignment.
    @Inject(method="joinWorld",at=@At("HEAD"),require=1)
    private void smoothfix$releaseJoiningWorld(ClientWorld next,CallbackInfo ci){
        var previous=((MinecraftClient)(Object)this).world;
        if(previous!=next){org.marj4n.smooth_fix.performance.LifecycleRetentionCleanup.disconnectedClientWorld(previous);org.marj4n.smooth_fix.performance.FabricRenderContextOwner.request((MinecraftClient)(Object)this,previous);}
    }
    @Inject(method="disconnect(Lnet/minecraft/client/gui/screen/Screen;)V",at=@At("HEAD"),require=1)
    private void smoothfix$releaseDisconnectingWorld(net.minecraft.client.gui.screen.Screen screen,CallbackInfo ci){
        org.marj4n.smooth_fix.performance.LifecycleRetentionCleanup.disconnectedClientWorld(((MinecraftClient)(Object)this).world);
        org.marj4n.smooth_fix.performance.FabricRenderContextOwner.request((MinecraftClient)(Object)this,((MinecraftClient)(Object)this).world);
    }
    @Inject(method="setWorld",at=@At("HEAD"),require=1)
    private void smoothfix$releasePreviousWorld(ClientWorld next,CallbackInfo ci){
        org.marj4n.smooth_fix.performance.RelevantDragonEntities.clear();
        org.marj4n.smooth_fix.performance.FootstepTargetCache.clear();
    }
}
