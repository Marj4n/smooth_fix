package org.marj4n.smooth_fix.mixin.client;

import org.joml.Quaternionf;
import org.marj4n.smooth_fix.SmoothFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * EMI Loot 0.7.9 creates temporary mob entities on its own "EMI Loot Worker" threads in order
 * to inspect their loot tables. Tumbleweed 0.5.5 performs client-only visual initialization in
 * its constructor using ClientWorld.random. C2ME correctly rejects that world RNG access from
 * the worker thread.
 *
 * Only when Tumbleweed is being constructed by an EMI Loot worker do we replace those three
 * cosmetic random rotations with a worker-local RNG. Real in-world Tumbleweed construction is
 * left completely unchanged.
 */
@Pseudo
@Mixin(targets = "net.konwboy.tumbleweed.common.EntityTumbleweed", remap = false)
public abstract class TumbleweedClientInitMixin {
    @Shadow public float rotOffsetX;
    @Shadow public float rotOffsetY;
    @Shadow public float rotOffsetZ;
    @Shadow public Quaternionf quat;
    @Shadow public Quaternionf prevQuat;

    @Inject(
            method = "initClient",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void smoothFix$useWorkerLocalRandomForEmiLoot(CallbackInfo ci) {
        String threadName = Thread.currentThread().getName();
        if (!threadName.startsWith("EMI Loot Worker")) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.rotOffsetX = 360.0F * random.nextFloat();
        this.rotOffsetY = 360.0F * random.nextFloat();
        this.rotOffsetZ = 360.0F * random.nextFloat();
        this.quat = new Quaternionf();
        this.prevQuat = new Quaternionf();

        SmoothFix.logPatchOnce(
                "emi_loot_tumbleweed",
                "Replaced Tumbleweed client-world RNG access with worker-local RNG during EMI Loot indexing."
        );

        ci.cancel();
    }
}
