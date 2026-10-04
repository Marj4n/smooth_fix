package org.marj4n.smooth_fix.mixin.common;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.marj4n.smooth_fix.benchmark.AdvancedBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Observe native accepted hits without modifying damage or target selection. */
@Mixin(LivingEntity.class)
public abstract class BenchmarkDamageMixin {
    @Inject(method="damage",at=@At("RETURN"))
    private void smoothfix$accepted(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        LivingEntity entity=(LivingEntity)(Object)this;
        if(ci.getReturnValueZ() && entity.getWorld() instanceof ServerWorld)AdvancedBenchmark.observeDamage(entity,source);
    }
}
