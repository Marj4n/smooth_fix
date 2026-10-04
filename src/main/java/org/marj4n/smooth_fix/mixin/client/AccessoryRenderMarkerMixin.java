package org.marj4n.smooth_fix.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
/** Optional target; the plugin checks the upstream flush expression before replacing it. */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.AccessoriesRenderLayer", remap = false, priority = 900)
public abstract class AccessoryRenderMarkerMixin { }
