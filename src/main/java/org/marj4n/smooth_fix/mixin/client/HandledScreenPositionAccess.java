package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(HandledScreen.class)
public interface HandledScreenPositionAccess {
    @Accessor("x") int smoothfix$getX();
    @Accessor("y") int smoothfix$getY();
}
