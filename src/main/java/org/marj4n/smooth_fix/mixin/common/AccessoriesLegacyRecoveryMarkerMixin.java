package org.marj4n.smooth_fix.mixin.common;

import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(
        targets = "io.wispforest.accessories.impl.AccessoriesContainerImpl",
        remap = false,
        priority = 500)
public abstract class AccessoriesLegacyRecoveryMarkerMixin {}
