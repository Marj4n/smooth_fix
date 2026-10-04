package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Run after Better Mob Combat's default-priority equipment injection. */
@Mixin(value = MobEntity.class, priority = 900)
public abstract class MobEquipmentMarkerMixin { }
