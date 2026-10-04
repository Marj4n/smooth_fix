package org.marj4n.smooth_fix.mixin.client;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Keep the native cursor model in sync when automation moves GLFW's cursor. */
@Mixin(Mouse.class)
public interface BenchmarkMouseAccess {
    @Accessor("x") void smoothfix$setX(double x);
    @Accessor("y") void smoothfix$setY(double y);
}
