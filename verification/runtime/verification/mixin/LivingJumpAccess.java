package verification.mixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface LivingJumpAccess {
    @Invoker("swimUpward") void fixtureSwimUpward(net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid);
    @Invoker("jump") void fixtureJump();
}
