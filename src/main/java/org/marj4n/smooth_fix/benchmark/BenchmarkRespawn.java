package org.marj4n.smooth_fix.benchmark;
import net.minecraft.server.network.ServerPlayerEntity;
/** Mirror the vanilla respawn packet handler's connection handoff. */
public final class BenchmarkRespawn {
    private BenchmarkRespawn() { }
    public static ServerPlayerEntity replace(ServerPlayerEntity player) {
        ServerPlayerEntity replacement=player.getServer().getPlayerManager().respawnPlayer(player,false);
        replacement.networkHandler.player=replacement;
        return replacement;
    }
}
