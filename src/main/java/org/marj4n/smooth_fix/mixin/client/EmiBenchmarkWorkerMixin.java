package org.marj4n.smooth_fix.mixin.client;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Preserve the actual exception instead of reporting an unexplained query timeout. */
@Pseudo
@Mixin(targets="dev.emi.emi.search.EmiSearch$SearchWorker",remap=false)
public abstract class EmiBenchmarkWorkerMixin {
    @ModifyArg(method="run",at=@At(value="INVOKE",target="Ldev/emi/emi/runtime/EmiLog;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),index=1,require=1)
    private Throwable smoothfix$queryFailure(Throwable cause){AdvancedClient.searchFailed(this,cause);return cause;}
}
