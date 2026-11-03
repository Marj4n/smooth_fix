package org.marj4n.smooth_fix.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Optional missing Forge-side ModernFix bridge; bytecode and config guarded by plugin. */
@Pseudo
@Mixin(targets = "net.minecraftforge.registries.GameData$BlockCallbacks", remap = false)
public abstract class KiltRegistryCacheMarkerMixin {}
