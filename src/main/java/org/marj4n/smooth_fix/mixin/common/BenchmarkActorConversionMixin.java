package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import org.marj4n.smooth_fix.benchmark.AdvancedBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only active debug actors: native conversion products retain test cleanup bookkeeping, never Avenger ownership. */
@Mixin(MobEntity.class)
public abstract class BenchmarkActorConversionMixin {
    @Inject(
            method =
                    "convertTo(Lnet/minecraft/entity/EntityType;Z)Lnet/minecraft/entity/mob/MobEntity;",
            at = @At("RETURN"))
    private void smoothfix$trackConvertedActor(
            EntityType<?> type, boolean keepEquipment, CallbackInfoReturnable<MobEntity> result) {
        if (result.getReturnValue() != null)
            AdvancedBenchmark.actorConverted((MobEntity) (Object) this, result.getReturnValue());
    }
}
