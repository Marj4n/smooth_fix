package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.client.render.model.WeightedBakedModel;
import org.spongepowered.asm.mixin.Mixin;

/** Runs after the installed Kilt injection. Unsupported method shapes retain upstream code. */
@Mixin(value = WeightedBakedModel.class, priority = 900)
public abstract class KiltRenderContextMarkerMixin {}
