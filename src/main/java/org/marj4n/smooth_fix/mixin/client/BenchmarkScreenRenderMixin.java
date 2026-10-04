package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class BenchmarkScreenRenderMixin {
    @Inject(method="renderWithTooltip",at=@At("RETURN")) private void smoothfix$screenRendered(DrawContext context,int x,int y,float delta,CallbackInfo ci){AdvancedClient.screenRendered((Screen)(Object)this);}
}
