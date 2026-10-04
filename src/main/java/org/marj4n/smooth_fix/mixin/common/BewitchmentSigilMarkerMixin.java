package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Late marker: only verified upstream expressions are rewritten in postApply. */
@Mixin(value=LivingEntity.class, priority=1)
public abstract class BewitchmentSigilMarkerMixin { }
