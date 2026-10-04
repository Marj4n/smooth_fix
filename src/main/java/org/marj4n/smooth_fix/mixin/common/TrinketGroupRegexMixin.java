package org.marj4n.smooth_fix.mixin.common;
import org.marj4n.smooth_fix.performance.TrinketNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="dev.emi.trinkets.compat.WrappingTrinketsUtils",remap=false)
public abstract class TrinketGroupRegexMixin {
    @Redirect(method="filterGroupInfo",at=@At(value="INVOKE",target="Ljava/lang/String;replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),require=0)
    private static String smoothfix$reusePattern(String value,String expression,String replacement) {
        return TrinketNames.replace(value,expression,replacement);
    }
}
