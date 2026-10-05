package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.entity.Entity;
import org.marj4n.smooth_fix.performance.FootstepTargetCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "eu.ha3.presencefootsteps.sound.SoundEngine", remap = false)
public abstract class FootstepTargetCacheMixin {
    @Unique private boolean smoothFix$usingCachedTargets;

    @Inject(method = "getTargets", at = @At("HEAD"), cancellable = true, require = 0)
    private void smoothFix$reuseTargets(Entity camera, CallbackInfoReturnable<Stream<? extends Entity>> callback) {
        List<? extends Entity> cached = FootstepTargetCache.get(this, camera);
        smoothFix$usingCachedTargets = cached != null;
        if (cached != null) callback.setReturnValue(cached.stream()
                .filter(entity -> !entity.isRemoved() && entity.getWorld() == camera.getWorld()));
    }

    @Inject(method = "getTargets", at = @At("RETURN"), cancellable = true, require = 0)
    private void smoothFix$rememberTargets(Entity camera, CallbackInfoReturnable<Stream<? extends Entity>> callback) {
        if (smoothFix$usingCachedTargets) return;
        List<? extends Entity> targets = callback.getReturnValue().toList();
        FootstepTargetCache.put(this, camera, targets);
        callback.setReturnValue(targets.stream());
    }
}
