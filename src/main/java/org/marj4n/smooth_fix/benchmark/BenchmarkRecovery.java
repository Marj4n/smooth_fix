package org.marj4n.smooth_fix.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import org.marj4n.smooth_fix.SmoothFix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Crash-safe player journal for automatic benchmarks.
 *
 * The file is intentionally written before the benchmark mutates the player. It survives a
 * disconnect or server restart and is deleted only after every saved field has been restored.
 */
public final class BenchmarkRecovery {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BenchmarkRecovery() { }

    public record State(
            String dimension,
            double x, double y, double z,
            float yaw, float pitch,
            int gameMode,
            boolean flying, boolean allowFlying, boolean invulnerable,
            float flySpeed, float walkSpeed,
            int selectedSlot,
            String inventorySnbt
    ) { }

    private static Path file(MinecraftServer server, java.util.UUID player) {
        return server.getSavePath(WorldSavePath.ROOT)
                .resolve("smooth_fix/benchmark_recovery/" + player + ".json");
    }

    public static void save(ServerPlayerEntity player) throws IOException {
        var abilities = player.getAbilities();
        NbtCompound inventory = new NbtCompound();
        inventory.put("Inventory", player.getInventory().writeNbt(new NbtList()));

        State state = new State(
                player.getWorld().getRegistryKey().getValue().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(),
                player.interactionManager.getGameMode().getId(),
                abilities.flying, abilities.allowFlying, abilities.invulnerable,
                abilities.getFlySpeed(), abilities.getWalkSpeed(),
                player.getInventory().selectedSlot,
                inventory.toString()
        );
        atomic(file(player.getServer(), player.getUuid()), GSON.toJson(state));
    }

    public static void atomic(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean restore(ServerPlayerEntity player) {
        Path path = file(player.getServer(), player.getUuid());
        if (!Files.exists(path)) return true;

        try {
            State state = GSON.fromJson(Files.readString(path), State.class);
            if (state == null) throw new IOException("Recovery journal is empty or invalid");

            var world = player.getServer().getWorld(
                    RegistryKey.of(RegistryKeys.WORLD, new Identifier(state.dimension))
            );
            if (world == null) throw new IOException("Original dimension is unavailable: " + state.dimension);

            // Revoke only the temporary benchmark grants before restoring the real vanilla flags.
            BenchmarkPlayerAbilities.revokeTemporary(player);
            // Recovery may happen while a chest/EMI-backed handled screen is still open. Close it first
            // so inventory slot updates are applied against the normal player handler (sync id 0).
            if (player.currentScreenHandler != player.playerScreenHandler) player.closeHandledScreen();

            player.changeGameMode(GameMode.byId(state.gameMode));
            player.teleport(world, state.x, state.y, state.z, state.yaw, state.pitch);

            if (state.inventorySnbt != null && !state.inventorySnbt.isBlank()) {
                NbtCompound root = StringNbtReader.parse(state.inventorySnbt);
                player.getInventory().clear();
                player.getInventory().readNbt(root.getList("Inventory", 10));
            }

            int selected = Math.max(0, Math.min(8, state.selectedSlot));
            player.getInventory().selectedSlot = selected;

            var abilities = player.getAbilities();
            abilities.flying = state.flying;
            abilities.allowFlying = state.allowFlying;
            abilities.invulnerable = state.invulnerable;
            abilities.setFlySpeed(state.flySpeed);
            abilities.setWalkSpeed(state.walkSpeed);
            player.sendAbilitiesUpdate();

            // Vanilla inventory sync does not guarantee that a benchmark-forced hotbar selection is
            // corrected immediately on the client, so sync both contents and the selected slot.
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.sendContentUpdates();
            if (player.networkHandler != null) {
                player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(selected));
            }

            Files.delete(path);
            return true;
        } catch (Exception exception) {
            SmoothFix.LOGGER.error(
                    "Benchmark recovery kept for {}: original player state could not be restored",
                    player.getUuid(), exception
            );
            return false;
        }
    }
}
