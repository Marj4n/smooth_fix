package org.marj4n.smooth_fix.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.muon.dynamic_resource_bars.render.HealthBarRenderer", remap = false, priority = 1)
public abstract class ResourceBarTemperatureMarkerMixin { }
