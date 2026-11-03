package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** After Horseman's default-priority return-value callback; no native collision replacement. */
@Mixin(value = Entity.class, priority = 900)
public abstract class HorsemanBoundsMarkerMixin {}
