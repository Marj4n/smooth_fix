package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.client.render.block.BlockModels;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BlockModels.class, priority = 900)
public abstract class BlockModelVariantMarkerMixin {}
