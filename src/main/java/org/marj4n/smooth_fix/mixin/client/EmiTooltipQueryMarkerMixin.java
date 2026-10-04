package org.marj4n.smooth_fix.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets = "dev.emi.emi.search.TooltipQuery", remap = false, priority = 900)
public abstract class EmiTooltipQueryMarkerMixin { }
