package org.marj4n.smooth_fix.mixin.client;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Exact Zenith Attributes Renewed 1.0.3 client UI preference; attribute calculations are untouched. */
@Pseudo
@Mixin(targets="dev.shadowsoffire.attributeslib.ALConfig", remap=false)
public abstract class InventoryAttributeButtonMixin {
    @Shadow public static boolean enableAttributesGui;
    @Inject(method="load",at=@At("TAIL"),require=1,remap=false)
    private static void smoothfix$hideInventoryAttributes(CallbackInfo ci) {
        if(org.marj4n.smooth_fix.config.SmoothFixConfig.get().hideInventoryAttributesButton)enableAttributesGui=false;
    }
}
