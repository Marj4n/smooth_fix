package org.marj4n.smooth_fix.mixin.common;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.Entity;
import org.marj4n.smooth_fix.benchmark.ServerBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Test dimensions only: delayed actors/projectiles retain their original run/stage ownership. */
@Mixin(ServerWorld.class)
public abstract class BenchmarkSpawnOwnershipMixin {
    @Inject(method="spawnEntity",at=@At("HEAD"),cancellable=true)
    private void smoothfix$ownBenchmarkSpawn(Entity entity,CallbackInfoReturnable<Boolean> cir) {
        org.marj4n.smooth_fix.benchmark.AdvancedBenchmark.observeSpawn(entity);
        if(!ServerBenchmark.allowSpawn((ServerWorld)(Object)this,entity))cir.setReturnValue(false);
    }
}
