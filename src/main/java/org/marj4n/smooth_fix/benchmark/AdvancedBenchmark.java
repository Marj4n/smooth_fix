package org.marj4n.smooth_fix.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.diagnostics.MemoryReport;
import org.marj4n.smooth_fix.diagnostics.ServerDiagnostics;

import java.nio.file.Path;
import java.util.*;

/**
 * Real-world advanced benchmark. Unlike the legacy synthetic arenas this suite operates in the
 * owner's actual world, validates observable effects, and restores the complete player snapshot.
 */
public final class AdvancedBenchmark {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static Run active;
    private static boolean installed;

    private AdvancedBenchmark() { }

    public static void install() {
        if (installed) return;
        installed = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Run run = active;
            if (run != null) {
                try { run.tick(); }
                catch (Throwable t) {
                    SmoothFix.LOGGER.error("Advanced benchmark tick failed", t);
                    run.finish("failed_runtime_exception");
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active != null) active.finish("server_stopping");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Run run = active;
            if (run != null && run.owner.equals(handler.player.getUuid())) run.finishWithPlayer("owner_disconnected", handler.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.ADV_RESPONSE, (server, player, handler, buf, sender) -> {
            UUID runId = buf.readUuid();
            int phase = buf.readInt();
            int action = buf.readUnsignedByte();
            String data = buf.readString(24000);
            server.execute(() -> accept(player, runId, phase, action, data));
        });
    }

    public static boolean isRunning() { return active != null; }

    public static String status() {
        return active == null ? "No advanced benchmark running." : active.status();
    }

    public static int start(ServerCommandSource source) {
        if (active != null) { source.sendError(Text.literal("An advanced benchmark is already running.")); return 0; }
        if (ServerBenchmark.isRunning() || ServerDiagnostics.isProfiling()) { source.sendError(Text.literal("Stop the legacy benchmark/profile first.")); return 0; }
        ServerPlayerEntity player;
        try { player = source.getPlayerOrThrow(); }
        catch (Exception e) { source.sendError(Text.literal("Run the advanced benchmark as a player.")); return 0; }

        try {
            if (!ServerPlayNetworking.canSend(player, BenchmarkProtocol.ADV_CONTROL)) { source.sendError(Text.literal("Install this Smooth Fix advanced build with diagnostics enabled on the client too.")); return 0; }
            if (!BenchmarkRecovery.restore(player)) {
                source.sendError(Text.literal("An older benchmark recovery journal could not be restored. Check latest.log."));
                return 0;
            }
            PlayerSnapshot original = PlayerSnapshot.capture(player);
            BenchmarkRecovery.save(player);
            Run run = new Run(player.getServer(), player, original);
            active = run;
            BenchmarkPlayerAbilities.grantTemporary(player, true);
            run.checkpoint();
            run.next();
            source.sendFeedback(() -> Text.literal("Smooth Fix advanced real-world stress test started. Do not manually move items or change dimensions. /smoothfix stress stop restores your full player state."), false);
            return 1;
        } catch (Throwable t) {
            SmoothFix.LOGGER.error("Could not start advanced benchmark", t);
            if (active != null) active.finish("start_failed");
            else BenchmarkRecovery.restore(player);
            source.sendError(Text.literal("Advanced benchmark could not start; check latest.log."));
            return 0;
        }
    }

    public static boolean stop(String reason) {
        Run run = active;
        if (run == null) return false;
        run.finish(reason);
        return true;
    }

    /** Called only after LivingEntity.damage returned true. */
    public static void onAcceptedDamage(net.minecraft.entity.LivingEntity victim, net.minecraft.entity.damage.DamageSource source, float amount) {
        Run run = active;
        if (run == null || run.ending || run.stage == null || !"combat".equals(run.stage.mode())) return;
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity player) || !player.getUuid().equals(run.owner)) return;
        if (!victim.getCommandTags().contains(run.actorTag())) return;
        run.acceptedPlayerHits++;
        run.acceptedPlayerDamage += Math.max(0, amount);
    }

    private static void accept(ServerPlayerEntity player, UUID id, int phase, int action, String data) {
        Run run = active;
        if (run == null || !run.id.equals(id) || !run.owner.equals(player.getUuid()) || phase != run.phase) return;
        long now = System.nanoTime();
        run.lastHeartbeat = now;
        if (action == 4) { run.finish("stopped_from_client: " + data); return; }
        if (action == 3) return;
        if (action == 0) {
            run.clientFailure = data;
            run.requestStageEnd("client_failed");
            return;
        }
        if (action == 1 && run.readyAt == 0) {
            run.readyAt = now;
            run.stageResult().put("status", "measuring");
            run.stageResult().put("readyLatencyMs", (now - run.stageStarted) / 1_000_000.0);
            if (ServerDiagnostics.startRecording()) run.serverProfiler = true;
            run.checkpoint();
            return;
        }
        if (action == 5) {
            run.clientVerified = true;
            run.stageResult().put("clientVerification", parseJsonOrText(data));
            return;
        }
        if (action == 2 && run.awaitingClientReport) {
            run.stageResult().put("client", parseJsonOrText(data));
            run.completeStage(run.pendingStatus == null ? "complete" : run.pendingStatus);
            run.next();
        }
    }

    private static Object parseJsonOrText(String data) {
        if (data == null || data.isBlank()) return Map.of();
        try { return JsonParser.parseString(data); }
        catch (Throwable ignored) { return data; }
    }

    private static final class Run {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final MinecraftServer server;
        final PlayerSnapshot original;
        final Path checkpoint;
        final Map<String,Object> report = new LinkedHashMap<>();
        final List<Map<String,Object>> results = new ArrayList<>();
        final long started = System.nanoTime();
        int phase = -1;
        AdvancedStagePlan.Stage stage;
        long stageStarted, readyAt, lastHeartbeat, endRequestedAt;
        boolean ending, awaitingClientReport, clientVerified, serverProfiler;
        String pendingStatus, clientFailure;
        BlockPos target;
        String preparedInventorySignature;
        int preparedSelectedSlot;
        int acceptedPlayerHits;
        double acceptedPlayerDamage;
        final Set<String> structures = new TreeSet<>();
        final List<BlockPos> temporaryBlocks = new ArrayList<>();
        final List<Entity> temporaryEntities = new ArrayList<>();

        Run(MinecraftServer server, ServerPlayerEntity player, PlayerSnapshot original) {
            this.server = server;
            this.owner = player.getUuid();
            this.original = original;
            this.checkpoint = server.getSavePath(WorldSavePath.ROOT).resolve("smooth_fix/benchmarks/advanced_" + id + ".json");
            report.put("runId", id.toString());
            report.put("suite", "advanced_real_world_v1");
            report.put("build", SmoothFix.BUILD_ID);
            report.put("status", "running");
            report.put("memoryAtStart", MemoryReport.snapshot("server"));
            report.put("stages", results);
            report.put("safety", Map.of(
                    "recovery", "Full inventory, selected hotbar slot, dimension/position/rotation, gamemode and vanilla ability flags/speeds are journaled before mutation.",
                    "world", "Temporary vanilla chest and TNT test blocks are placed only into air and removed after their stage. Natural Lootr containers are never modified by server code beyond the normal player interaction.",
                    "coldChunk", "Chunk is synchronously loaded before terrain height is queried, preventing the old below-terrain placement bug."
            ));
        }

        ServerPlayerEntity player() { return server.getPlayerManager().getPlayer(owner); }
        ServerWorld world() {
            ServerPlayerEntity p = player();
            return p == null ? null : (ServerWorld) p.getWorld();
        }
        String actorTag() { return "smoothfix_adv:" + id + ":" + phase; }
        Map<String,Object> stageResult() { return results.get(phase); }
        String status() { return "Advanced run " + id + ", " + (phase + 1) + "/" + AdvancedStagePlan.stages().size() + " " + (stage == null ? "idle" : stage.name()) + ", " + (awaitingClientReport ? "collecting client report" : readyAt == 0 ? "waiting for client" : "running"); }

        void next() {
            if (ending) return;
            cleanupStage();
            int next = AdvancedStagePlan.next(phase);
            if (phase == -1) next = 0;
            if (next < 0) { finish("completed"); return; }
            phase = next;
            stage = AdvancedStagePlan.stages().get(phase);
            stageStarted = System.nanoTime(); readyAt = 0; lastHeartbeat = stageStarted; endRequestedAt = 0;
            awaitingClientReport = false; clientVerified = false; serverProfiler = false; pendingStatus = null; clientFailure = null;
            target = null; preparedInventorySignature = null; acceptedPlayerHits = 0; acceptedPlayerDamage = 0; structures.clear();
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("phase", phase); result.put("name", stage.name()); result.put("mode", stage.mode());
            result.put("status", "preparing"); result.put("requiresVerification", stage.requiresVerification());
            results.add(result);
            try {
                if (!prepareStage()) return;
                result.put("status", "waiting_client_ready");
                checkpoint();
                send(1);
            } catch (Throwable t) {
                result.put("error", t.toString());
                SmoothFix.LOGGER.error("Could not prepare advanced benchmark stage {}", stage.name(), t);
                completeWithoutClient("failed_prepare");
                next();
            }
        }

        boolean prepareStage() {
            ServerPlayerEntity p = requirePlayer();
            ServerWorld world = (ServerWorld) p.getWorld();
            BenchmarkPlayerAbilities.grantTemporary(p, true);
            switch (stage.mode()) {
                case "route" -> {
                    target = p.getBlockPos();
                    stageResult().put("loadedChunksAtPrepare", world.getChunkManager().getLoadedChunkCount());
                }
                case "cold_route" -> prepareColdChunk(p, world);
                case "structure" -> { target = p.getBlockPos(); scanStructures(world, p.getBlockPos(), 4); }
                case "container" -> prepareVanillaChest(p, world);
                case "lootr" -> {
                    if (!FabricLoader.getInstance().isModLoaded("lootr")) {
                        stageResult().put("status", "skipped_mod_unavailable"); checkpoint(); next(); return false;
                    }
                    BlockPos found = findLootr(world, p.getBlockPos(), 5);
                    if (found == null) {
                        stageResult().put("status", "skipped_no_loaded_lootr_container");
                        stageResult().put("note", "Run the suite from/near a generated Lootr structure for a real Lootr interaction sample.");
                        checkpoint(); next(); return false;
                    }
                    BlockPos safe = findSafeStand(world, found, 4);
                    if (safe == null) {
                        stageResult().put("status", "skipped_lootr_container_inaccessible"); checkpoint(); next(); return false;
                    }
                    p.teleport(world, safe.getX() + .5, safe.getY(), safe.getZ() + .5, p.getYaw(), p.getPitch());
                    target = found;
                    preparedInventorySignature = inventorySignature(p);
                    stageResult().put("lootrBlockEntity", world.getBlockEntity(found).getType().toString());
                    stageResult().put("target", posMap(found));
                }
                case "inventory" -> prepareInventory(p);
                case "emi" -> { target = p.getBlockPos(); }
                case "combat" -> prepareCombat(p, world);
                case "tnt" -> prepareTnt(p, world);
                default -> throw new IllegalStateException("Unknown advanced stage mode " + stage.mode());
            }
            return true;
        }

        void prepareColdChunk(ServerPlayerEntity p, ServerWorld world) {
            int x = p.getBlockX() + 4096 + Math.floorMod(owner.hashCode(), 256);
            int z = p.getBlockZ() + 4096 + Math.floorMod(Long.hashCode(id.getMostSignificantBits()), 256);
            int cx = x >> 4, cz = z >> 4;
            long before = System.nanoTime();
            WorldChunk chunk = world.getChunk(cx, cz); // force readiness BEFORE querying height
            long after = System.nanoTime();
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            p.teleport(world, x + .5, y + 2, z + .5, p.getYaw(), p.getPitch());
            target = new BlockPos(x, y + 2, z);
            stageResult().put("chunk", Map.of("x", cx, "z", cz));
            stageResult().put("chunkPrepareMs", (after - before) / 1_000_000.0);
            stageResult().put("chunkStatus", chunk.getStatus().toString());
            stageResult().put("terrainTopY", y);
        }

        void prepareVanillaChest(ServerPlayerEntity p, ServerWorld world) {
            BlockPos pos = airAboveTerrain(world, p.getBlockX() + 2, p.getBlockZ() + 2, 1);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            temporaryBlocks.add(pos);
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof Inventory inventory)) throw new IllegalStateException("Temporary chest did not expose an inventory");
            inventory.setStack(0, new ItemStack(Items.DIAMOND, 1));
            be.markDirty();
            target = pos;
            preparedInventorySignature = inventorySignature(p);
            stageResult().put("target", posMap(pos));
            stageResult().put("verification", "server observes actual player inventory NBT changing after QUICK_MOVE");
        }

        void prepareInventory(ServerPlayerEntity p) {
            preparedSelectedSlot = p.getInventory().selectedSlot;
            int inventoryIndex = 9;
            ItemStack existing = p.getInventory().getStack(inventoryIndex);
            if (existing.isEmpty()) p.getInventory().setStack(inventoryIndex, new ItemStack(Items.COBBLESTONE, 7));
            else p.getInventory().setStack(inventoryIndex, new ItemStack(Items.GOLD_INGOT, 3));
            p.currentScreenHandler.sendContentUpdates();
            p.playerScreenHandler.sendContentUpdates();
            preparedInventorySignature = inventorySignature(p);
            target = p.getBlockPos();
            stageResult().put("selectedSlotBeforeUi", preparedSelectedSlot);
            stageResult().put("temporaryInventoryIndex", inventoryIndex);
        }

        void prepareCombat(ServerPlayerEntity p, ServerWorld world) {
            MobEntity mob = (MobEntity) EntityType.ZOMBIE.create(world);
            if (mob == null) throw new IllegalStateException("Could not create combat target");
            mob.refreshPositionAndAngles(p.getX() + 3.0, p.getY(), p.getZ(), 0, 0);
            mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.COMMAND, null, null);
            mob.setPersistent(); mob.addCommandTag(actorTag());
            if (!world.spawnEntity(mob)) throw new IllegalStateException("Combat target spawn refused");
            temporaryEntities.add(mob); target = mob.getBlockPos();
            stageResult().put("targetEntity", Registries.ENTITY_TYPE.getId(mob.getType()).toString());
            stageResult().put("verification", "LivingEntity.damage returned true for damage whose attacker is the benchmark owner");
        }

        void prepareTnt(ServerPlayerEntity p, ServerWorld world) {
            BlockPos center = airAboveTerrain(world, p.getBlockX() + 5, p.getBlockZ(), 3);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                if (world.getBlockState(pos).isAir()) { world.setBlockState(pos, Blocks.GLASS.getDefaultState()); temporaryBlocks.add(pos); }
            }
            TntEntity tnt = new TntEntity(world, center.getX() + .5, center.getY() + 1.0, center.getZ() + .5, p);
            tnt.setFuse(40); tnt.addCommandTag(actorTag());
            world.spawnEntity(tnt); temporaryEntities.add(tnt); target = center;
            stageResult().put("target", posMap(center));
            stageResult().put("testGlassBlocks", temporaryBlocks.size());
            stageResult().put("verification", "at least one benchmark glass block is actually destroyed after primed TNT detonates");
        }

        void tick() {
            if (ending) return;
            ServerPlayerEntity p = player();
            if (p == null) { finish("owner_missing"); return; }
            long now = System.nanoTime();
            if (readyAt == 0) {
                if (now - stageStarted > stage.timeoutSeconds() * 1_000_000_000L) requestStageEnd("client_ready_timeout");
                return;
            }
            if (now - lastHeartbeat > 20_000_000_000L) { finish("client_heartbeat_timeout"); return; }
            if (awaitingClientReport) {
                if (now - endRequestedAt > 15_000_000_000L) { completeStage((pendingStatus == null ? "complete" : pendingStatus) + "_client_report_timeout"); next(); }
                return;
            }

            ServerWorld world = (ServerWorld) p.getWorld();
            if ("structure".equals(stage.mode()) || "route".equals(stage.mode()) || "cold_route".equals(stage.mode())) scanStructures(world, p.getBlockPos(), 2);

            boolean verified = switch (stage.mode()) {
                case "container", "lootr" -> preparedInventorySignature != null && !preparedInventorySignature.equals(inventorySignature(p));
                case "inventory" -> preparedSelectedSlot != p.getInventory().selectedSlot && preparedInventorySignature != null && !preparedInventorySignature.equals(inventorySignature(p));
                case "emi" -> clientVerified;
                case "combat" -> acceptedPlayerHits > 0;
                case "tnt" -> temporaryBlocks.stream().anyMatch(pos -> !world.getBlockState(pos).isOf(Blocks.GLASS));
                default -> false;
            };
            if (verified) {
                stageResult().put("actionVerified", true);
                if ("combat".equals(stage.mode())) { stageResult().put("acceptedPlayerHits", acceptedPlayerHits); stageResult().put("acceptedPlayerDamage", acceptedPlayerDamage); }
                requestStageEnd("complete");
                return;
            }

            long elapsed = now - readyAt;
            if (!stage.requiresVerification() && elapsed >= stage.measurementSeconds() * 1_000_000_000L) {
                stageResult().put("actionVerified", "structure".equals(stage.mode()) ? !structures.isEmpty() : true);
                if (!structures.isEmpty()) stageResult().put("structuresObserved", new ArrayList<>(structures));
                requestStageEnd("structure".equals(stage.mode()) && structures.isEmpty() ? "complete_no_structure_observed" : "complete");
            } else if (elapsed >= stage.timeoutSeconds() * 1_000_000_000L) {
                stageResult().put("actionVerified", false);
                if ("combat".equals(stage.mode())) stageResult().put("acceptedPlayerHits", acceptedPlayerHits);
                requestStageEnd(clientFailure == null ? "failed_action_not_verified" : "client_failed: " + clientFailure);
            }
        }

        void requestStageEnd(String status) {
            if (awaitingClientReport || ending) return;
            pendingStatus = status;
            awaitingClientReport = true;
            endRequestedAt = System.nanoTime();
            send(2);
        }

        void completeWithoutClient(String status) {
            pendingStatus = status;
            completeStage(status);
        }

        void completeStage(String status) {
            if (phase < 0 || phase >= results.size()) return;
            Map<String,Object> result = stageResult();
            result.put("status", status);
            result.put("elapsedSeconds", (System.nanoTime() - stageStarted) / 1_000_000_000.0);
            if (!structures.isEmpty()) result.put("structuresObserved", new ArrayList<>(structures));
            if (serverProfiler) { result.put("server", ServerDiagnostics.finishRecording()); serverProfiler = false; }
            result.put("worldAtEnd", worldSnapshot());
            checkpoint();
        }

        void scanStructures(ServerWorld world, BlockPos center, int radiusChunks) {
            int cx = center.getX() >> 4, cz = center.getZ() >> 4;
            for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) for (int z = cz - radiusChunks; z <= cz + radiusChunks; z++) {
                WorldChunk chunk;
                try { chunk = world.getChunk(x, z); } catch (Throwable ignored) { continue; }
                for (Map.Entry<Structure, StructureStart> entry : chunk.getStructureStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start == null || !start.hasChildren()) continue;
                    var id = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE).getId(entry.getKey());
                    structures.add(id == null ? entry.getKey().getClass().getName() : id.toString());
                }
            }
        }

        BlockPos findLootr(ServerWorld world, BlockPos center, int radiusChunks) {
            BlockPos nearest = null; double nearestSq = Double.MAX_VALUE;
            int cx = center.getX() >> 4, cz = center.getZ() >> 4;
            for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) for (int z = cz - radiusChunks; z <= cz + radiusChunks; z++) {
                WorldChunk chunk;
                try { chunk = world.getChunk(x, z); } catch (Throwable ignored) { continue; }
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    var typeId = Registries.BLOCK_ENTITY_TYPE.getId(e.getValue().getType());
                    if (!"lootr".equals(typeId.getNamespace())) continue;
                    double dx=e.getKey().getX()-center.getX(), dy=e.getKey().getY()-center.getY(), dz=e.getKey().getZ()-center.getZ();
                    double d = dx*dx + dy*dy + dz*dz;
                    if (d < nearestSq) { nearestSq = d; nearest = e.getKey().toImmutable(); }
                }
            }
            return nearest;
        }

        BlockPos findSafeStand(ServerWorld world, BlockPos around, int radius) {
            for (int r = 1; r <= radius; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                BlockPos pos = around.add(dx, 0, dz);
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos feet = pos.add(0, dy, 0);
                    if (world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir() && !world.getBlockState(feet.down()).isAir()) return feet;
                }
            }
            return null;
        }

        BlockPos airAboveTerrain(ServerWorld world, int x, int z, int extra) {
            world.getChunk(x >> 4, z >> 4); // readiness is mandatory before height query
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, top + Math.max(1, extra), z);
            for (int i = 0; i < 12 && !world.getBlockState(pos).isAir(); i++) pos = pos.up();
            if (!world.getBlockState(pos).isAir()) throw new IllegalStateException("Could not find safe air test position");
            return pos;
        }

        void cleanupStage() {
            if (serverProfiler) { try { ServerDiagnostics.finishRecording(); } catch (Throwable ignored) { } serverProfiler = false; }
            ServerWorld world = world();
            for (Entity e : temporaryEntities) if (e != null && !e.isRemoved()) e.discard();
            temporaryEntities.clear();
            if (world != null) for (BlockPos pos : temporaryBlocks) {
                try { world.setBlockState(pos, Blocks.AIR.getDefaultState()); } catch (Throwable ignored) { }
            }
            temporaryBlocks.clear();
        }

        void send(int action) {
            ServerPlayerEntity p = player();
            if (p == null || !ServerPlayNetworking.canSend(p, BenchmarkProtocol.ADV_CONTROL)) return;
            var buf = PacketByteBufs.create();
            buf.writeByte(action); buf.writeUuid(id); buf.writeInt(phase);
            buf.writeString(stage == null ? "" : stage.name());
            buf.writeString(stage == null ? "" : stage.mode());
            ServerWorld world = world();
            buf.writeString(world == null ? "" : world.getRegistryKey().getValue().toString());
            BlockPos c = target == null ? (p == null ? BlockPos.ORIGIN : p.getBlockPos()) : target;
            buf.writeDouble(c.getX() + .5); buf.writeDouble(c.getY()); buf.writeDouble(c.getZ() + .5);
            buf.writeLong(target == null ? Long.MIN_VALUE : target.asLong());
            buf.writeInt(stage == null ? 0 : stage.measurementSeconds());
            ServerPlayNetworking.send(p, BenchmarkProtocol.ADV_CONTROL, buf);
        }

        void checkpoint() {
            try { BenchmarkRecovery.atomic(checkpoint, GSON.toJson(report)); }
            catch (Throwable t) { SmoothFix.LOGGER.error("Could not write advanced benchmark checkpoint", t); }
        }

        void finish(String reason) { finishWithPlayer(reason, player()); }

        void finishWithPlayer(String reason, ServerPlayerEntity p) {
            if (ending) return;
            ending = true;
            cleanupStage();
            try { if (p != null) send(0); } catch (Throwable ignored) { }
            report.put("status", reason);
            report.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
            report.put("memoryAtEnd", MemoryReport.snapshot("server"));
            boolean restored = p != null && BenchmarkRecovery.restore(p);
            report.put("playerRestored", restored);
            if (p != null && restored) report.put("recoveryVerification", original.compare(p));
            else report.put("recoveryVerification", Map.of("all", false, "reason", p == null ? "player_missing" : "journal_restore_failed"));
            checkpoint();
            if (p != null) p.sendMessage(Text.literal("Smooth Fix advanced benchmark " + reason + ". Report: " + checkpoint.toAbsolutePath()), false);
            SmoothFix.LOGGER.info("Smooth Fix advanced benchmark {}: {}", reason, checkpoint.toAbsolutePath());
            active = null;
        }

        ServerPlayerEntity requirePlayer() {
            ServerPlayerEntity p = player();
            if (p == null) throw new IllegalStateException("Benchmark owner is not online");
            return p;
        }

        Map<String,Object> worldSnapshot() {
            ServerPlayerEntity p = player(); ServerWorld w = world();
            if (p == null || w == null) return Map.of("available", false);
            return Map.of(
                    "dimension", w.getRegistryKey().getValue().toString(),
                    "loadedChunkCount", w.getChunkManager().getLoadedChunkCount(),
                    "player", Map.of("x", p.getX(), "y", p.getY(), "z", p.getZ())
            );
        }
    }

    private record PlayerSnapshot(String dimension, double x, double y, double z, float yaw, float pitch,
                                  int gameMode, boolean flying, boolean allowFlying, boolean invulnerable,
                                  float flySpeed, float walkSpeed, int selectedSlot, String inventory) {
        static PlayerSnapshot capture(ServerPlayerEntity p) {
            PlayerAbilities a = p.getAbilities();
            return new PlayerSnapshot(
                    p.getWorld().getRegistryKey().getValue().toString(), p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(),
                    p.interactionManager.getGameMode().getId(), a.flying, a.allowFlying, a.invulnerable,
                    a.getFlySpeed(), a.getWalkSpeed(), p.getInventory().selectedSlot, inventorySignature(p)
            );
        }

        Map<String,Object> compare(ServerPlayerEntity p) {
            PlayerSnapshot now = capture(p);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("inventory", inventory.equals(now.inventory));
            m.put("selectedHotbarSlot", selectedSlot == now.selectedSlot);
            m.put("dimension", dimension.equals(now.dimension));
            m.put("location", Math.abs(x-now.x)<.01 && Math.abs(y-now.y)<.01 && Math.abs(z-now.z)<.01);
            m.put("rotation", Math.abs(yaw-now.yaw)<.01 && Math.abs(pitch-now.pitch)<.01);
            m.put("gameMode", gameMode == now.gameMode);
            m.put("abilities", flying==now.flying && allowFlying==now.allowFlying && invulnerable==now.invulnerable && Math.abs(flySpeed-now.flySpeed)<.0001 && Math.abs(walkSpeed-now.walkSpeed)<.0001);
            boolean all = m.values().stream().allMatch(Boolean.TRUE::equals);
            m.put("all", all);
            return m;
        }
    }

    private static String inventorySignature(ServerPlayerEntity p) {
        return p.getInventory().writeNbt(new NbtList()).toString();
    }

    private static Map<String,Integer> posMap(BlockPos pos) {
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
    }
}
