package org.marj4n.smooth_fix.mixin.client;
import java.util.List;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.emi.emi.search.EmiSearch",remap=false)
public abstract class EmiBenchmarkSearchMixin {
    @Inject(method="search",at=@At("HEAD")) private static void smoothfix$search(String query,CallbackInfo ci){AdvancedClient.searchStarted(query);}
    // Only the accepted worker executes this write. Obsolete workers can share an empty result list.
    @Inject(method="apply",at=@At(value="FIELD",target="Ldev/emi/emi/search/EmiSearch;stacks:Ljava/util/List;",opcode=org.objectweb.asm.Opcodes.PUTSTATIC,shift=At.Shift.AFTER)) private static void smoothfix$apply(@Coerce Object worker,List<?> result,CallbackInfo ci){AdvancedClient.searchApplied(worker,result);}
}
