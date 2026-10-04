package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.marj4n.smooth_fix.benchmark.AdvancedBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts combat only after vanilla/modded LivingEntity.damage accepted the hit. */
@Mixin(LivingEntity.class)
public abstract class BenchmarkDamageCounterMixin {
    @Inject(method = "damage", at = @At("RETURN"))
    private void smoothfix$advancedBenchmarkAcceptedHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            AdvancedBenchmark.onAcceptedDamage((LivingEntity) (Object) this, source, amount);
        }
    }
}
