package org.marj4n.smooth_fix.mixin.server;

import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebase the previous-tick distance origin only after vanilla accepts a pending server teleport. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ConfirmedTeleportAnchorMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private Vec3d requestedTeleportPos;
    @Shadow private double lastTickX;
    @Shadow private double lastTickY;
    @Shadow private double lastTickZ;

    @Inject(method = "onTeleportConfirm", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestedTeleportPos:Lnet/minecraft/util/math/Vec3d;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void smoothfix$rebaseAcceptedTeleport(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
        // This field is cleared only in the successful confirmation branch, on the server thread.
        if (requestedTeleportPos == null) {
            lastTickX = player.getX();
            lastTickY = player.getY();
            lastTickZ = player.getZ();
        }
    }
}
