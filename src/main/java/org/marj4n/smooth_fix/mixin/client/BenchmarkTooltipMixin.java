package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DrawContext.class)
public abstract class BenchmarkTooltipMixin {
    @Unique private long smoothfix$tooltipAt;
    @Inject(method="drawItemTooltip",at=@At("HEAD")) private void smoothfix$beginTooltip(TextRenderer text,ItemStack stack,int x,int y,CallbackInfo ci){smoothfix$tooltipAt=AdvancedClient.tooltipStarted();}
    @Inject(method="drawItemTooltip",at=@At("RETURN")) private void smoothfix$endTooltip(TextRenderer text,ItemStack stack,int x,int y,CallbackInfo ci){AdvancedClient.tooltipEnded(smoothfix$tooltipAt);smoothfix$tooltipAt=0;}
}
