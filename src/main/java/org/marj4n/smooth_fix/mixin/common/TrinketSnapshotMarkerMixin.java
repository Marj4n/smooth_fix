package org.marj4n.smooth_fix.mixin.common;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets="io.wispforest.tclayer.ImmutableDelegatingMap",remap=false,priority=1)
public abstract class TrinketSnapshotMarkerMixin { }
