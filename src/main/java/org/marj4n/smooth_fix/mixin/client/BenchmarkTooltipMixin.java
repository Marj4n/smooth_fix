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
    // The private component renderer is shared by item, optional-data and ordered-text overloads.
    @Inject(method="drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V",at=@At("HEAD"),require=1)
    private void smoothfix$beginTooltip(TextRenderer text,java.util.List<net.minecraft.client.gui.tooltip.TooltipComponent> lines,int x,int y,net.minecraft.client.gui.tooltip.TooltipPositioner positioner,CallbackInfo ci){smoothfix$tooltipAt=lines.isEmpty()?0:AdvancedClient.tooltipStarted();}
    @Inject(method="drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V",at=@At("RETURN"),require=1)
    private void smoothfix$endTooltip(TextRenderer text,java.util.List<net.minecraft.client.gui.tooltip.TooltipComponent> lines,int x,int y,net.minecraft.client.gui.tooltip.TooltipPositioner positioner,CallbackInfo ci){AdvancedClient.tooltipEnded(smoothfix$tooltipAt);smoothfix$tooltipAt=0;}
}
