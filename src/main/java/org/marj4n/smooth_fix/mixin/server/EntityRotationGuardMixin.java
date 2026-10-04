package org.marj4n.smooth_fix.mixin.server;

import net.minecraft.entity.Entity;
import org.marj4n.smooth_fix.diagnostics.InvalidRotationTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain the last valid orientation, as vanilla already does for invalid inputs. */
@Mixin(Entity.class)
public abstract class EntityRotationGuardMixin {
    @Inject(method = "setYaw", at = @At("HEAD"), cancellable = true)
    private void smoothFix$guardYaw(float yaw, CallbackInfo ci) {
        if (!Float.isFinite(yaw)) {
            InvalidRotationTracker.record((Entity) (Object) this, "yaw", yaw);
            ci.cancel();
        }
    }

    @Inject(method = "setPitch", at = @At("HEAD"), cancellable = true)
    private void smoothFix$guardPitch(float pitch, CallbackInfo ci) {
        if (!Float.isFinite(pitch)) {
            InvalidRotationTracker.record((Entity) (Object) this, "pitch", pitch);
            ci.cancel();
        }
    }
}
