package org.marj4n.smooth_fix.mixin.common;

import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;

/** Post-merge marker: covers vanilla construction and ThreadTweak's replacement factory. */
@Mixin(value = Util.class, priority = 800)
public abstract class BackgroundWorkerBudgetMixin { }
