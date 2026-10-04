package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.network.ClientPlayerEntity;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Benchmark jump pulses own the movement; the user's auto-jump option stays intact. */
@Mixin(ClientPlayerEntity.class)
public abstract class BenchmarkAutoJumpMixin {
    @Shadow private int ticksToNextAutojump;
    @Inject(method="isAutoJumpEnabled",at=@At("HEAD"),cancellable=true,require=1)
    private void smoothfix$controlledAutoJump(CallbackInfoReturnable<Boolean> cir){if((AdvancedClient.controlsMovement() || org.marj4n.smooth_fix.benchmark.ClientBenchmark.controlsMovement()))cir.setReturnValue(false);}
    @Inject(method="tickMovement",at=@At("HEAD"),require=1)
    private void smoothfix$clearPendingAutoJump(CallbackInfo ci){if((AdvancedClient.controlsMovement() || org.marj4n.smooth_fix.benchmark.ClientBenchmark.controlsMovement()))ticksToNextAutojump=0;}
}
