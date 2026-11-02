package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.marj4n.smooth_fix.diagnostics.ClientFrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Timings exist only during a requested profile; presentation/FPS-limiter waits are excluded from work time. */
@Mixin(MinecraftClient.class)
public abstract class ClientFrameTimingMixin {
    @org.spongepowered.asm.mixin.Unique private long smoothfix$reportFrameStart;
    @org.spongepowered.asm.mixin.Unique private void smoothfix$observeReportPressure() {
        if (smoothfix$reportFrameStart != 0) {
            org.marj4n.smooth_fix.performance.ReportIo.observeFrame(System.nanoTime()-smoothfix$reportFrameStart);
            smoothfix$reportFrameStart=0;
        }
    }
    @Inject(method = "render", at = @At("HEAD"))
    private void smoothfix$startFrame(boolean tick, CallbackInfo ci) { smoothfix$reportFrameStart=org.marj4n.smooth_fix.performance.ReportIo.active()?System.nanoTime():0; org.marj4n.smooth_fix.benchmark.AdvancedClient.renderFrameStarted();ClientFrameProfiler.beginFrame(); }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;swapBuffers()V"))
    private void smoothfix$endWork(boolean tick, CallbackInfo ci) { smoothfix$observeReportPressure(); ClientFrameProfiler.endWork(); }

    @Inject(method = "render", at = @At("RETURN"))
    private void smoothfix$endFrame(boolean tick, CallbackInfo ci) { org.marj4n.smooth_fix.benchmark.AdvancedClient.renderFrameEnded(); smoothfix$observeReportPressure(); ClientFrameProfiler.endWork(); }
}
