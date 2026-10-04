package verification.mixin;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientPlayerEntity.class)
public interface ClientAutoJumpAccess {
    @Accessor("autoJumpEnabled") void fixtureAutoJumpEnabled(boolean enabled);
}
