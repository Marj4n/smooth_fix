package org.marj4n.smooth_fix.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {
        "com.leon.saintsdragons.client.sound.cindervane.CindervaneFireBodySoundController",
        "com.leon.saintsdragons.client.sound.ignivorus.IgnivorusFireBreathSoundController",
        "com.leon.saintsdragons.client.sound.raevyx.RaevyxDiveSoundController",
        "com.leon.saintsdragons.client.sound.raevyx.RaevyxLightningBeamSoundController",
        "com.leon.saintsdragons.client.sound.volitans.VolitansBreathSoundController",
        "com.leon.saintsdragons.client.sound.volitans.VolitansBurrowSoundController"
}, remap = false, priority = 1)
public abstract class DragonSoundScanMarkerMixin { }
