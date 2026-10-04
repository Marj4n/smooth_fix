package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;

/** Marker: the plugin substitutes only the verified non-escaping quaternion bytecode sequence. */
@Mixin(value = ModelPart.class, priority = 900)
public abstract class ModelPartAllocationMixin { }
