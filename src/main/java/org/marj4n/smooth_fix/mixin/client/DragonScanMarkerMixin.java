package org.marj4n.smooth_fix.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets="com.leon.saintsdragons.client.camera.IgnivorusSkyfallScreenEffects",remap=false,priority=1)
public abstract class DragonScanMarkerMixin { }
