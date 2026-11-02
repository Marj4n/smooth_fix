package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.marj4n.smooth_fix.benchmark.AdvancedClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Read-only benchmark observation; does not cancel, alter, replace or skip rendering. */
@Mixin(WorldRenderer.class)
public abstract class BenchmarkOwnedEntityRenderMixin {
 @Inject(method="renderEntity",at=@At("RETURN"))
 private void smoothfix$ownedRender(Entity entity,double x,double y,double z,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertices,CallbackInfo ci){AdvancedClient.entityRendered(entity);}
}
