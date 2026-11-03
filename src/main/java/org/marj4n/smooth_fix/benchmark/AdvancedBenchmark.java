package org.marj4n.smooth_fix.benchmark;

import com.google.gson.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.diagnostics.*;
import java.util.*;
import java.nio.file.Path;

/** Opt-in destructive gameplay benchmark in the actual dimensions of a disposable world copy. */
public final class AdvancedBenchmark {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final String TAG = "smoothfix_advanced:";
    private static Run active, completed;
    private static final BenchmarkBaselineGate<AdvancedPlan> baselineGate =
            new BenchmarkBaselineGate<>();
    private static long completedAt;
    private static final Set<Entity> stale = new HashSet<>();

    private AdvancedBenchmark() {}

    public static boolean isRunning() {
        return active != null || baselineGate.isPending();
    }

    public static String status() {
        if (baselineGate.isPending()) return "Awaiting original client state before mutation";
        return active == null
                ? "No advanced benchmark running."
                : active.phase
                        + 1
                        + "/"
                        + active.stages.size()
                        + ": "
                        + active.stage.name
                        + (active.structureSearch == null
                                ? ""
                                : "; searching radius "
                                        + active.structureSearch.radius()
                                        + " blocks");
    }

    public static boolean stop() {
        if (baselineGate.isPending()) {
            baselineGate.cancel();
            return true;
        }
        if (active == null) return false;
        active.finish("stopped_by_operator");
        return true;
    }

    public static void install() {
        ServerPlayNetworking.registerGlobalReceiver(
                BenchmarkProtocol.BASELINE_RESPONSE,
                (server, player, handler, buf, sender) -> {
                    UUID token = buf.readUuid();
                    String json = buf.readString(24000);
                    server.execute(
                            () -> {
                                var request =
                                        baselineGate.consume(
                                                player.getUuid(), token, System.nanoTime());
                                if (request == null) return;
                                try {
                                    JsonObject message =
                                            JsonParser.parseString(json).getAsJsonObject();
                                    if (!message.has("ready")
                                            || !message.get("ready").getAsBoolean())
                                        throw new IllegalStateException(
                                                "Client original-state capture refused");
                                    Map<String, Object> snapshot =
                                            GSON.fromJson(
                                                    message.get("snapshot"),
                                                    new com.google.gson.reflect.TypeToken<
                                                            Map<String, Object>>() {}.getType());
                                    if (snapshot == null
                                            || !Boolean.TRUE.equals(
                                                    snapshot.get("inventoryProjectionAvailable"))
                                            || !"canonical-nbt-v1"
                                                    .equals(
                                                            snapshot.get(
                                                                    "localSavedInventoryFormat"))
                                            || !String.valueOf(
                                                            snapshot.get(
                                                                    "localSavedInventorySha256"))
                                                    .matches("[0-9a-f]{64}"))
                                        throw new IllegalStateException(
                                                "Incomplete client original-state snapshot");
                                    if (!message.has("entityRenderDistanceMultiplier"))
                                        throw new IllegalStateException(
                                                "Native client render-distance multiplier unavailable");
                                    double renderScale =
                                            message.get("entityRenderDistanceMultiplier")
                                                    .getAsDouble();
                                    if (!Double.isFinite(renderScale)
                                            || renderScale <= 0
                                            || renderScale > 16)
                                        throw new IllegalStateException(
                                                "Invalid native entity render multiplier");
                                    startVerified(
                                            player,
                                            request.payload(),
                                            request.run(),
                                            snapshot,
                                            renderScale);
                                } catch (Exception failure) {
                                    SmoothFix.LOGGER.warn(
                                            "Advanced benchmark original-state preflight rejected; no new benchmark mutation",
                                            failure);
                                    player.sendMessage(
                                            Text.literal(
                                                    "Benchmark did not start: original client snapshot unavailable."),
                                            false);
                                }
                            });
                });
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) ->
                        dispatcher.register(
                                CommandManager.literal("smoothfix")
                                        .requires(s -> s.hasPermissionLevel(2))
                                        .then(
                                                CommandManager.literal("stress")
                                                        .then(
                                                                CommandManager.literal("advanced")
                                                                        .then(
                                                                                CommandManager
                                                                                        .literal(
                                                                                                "start")
                                                                                        .then(
                                                                                                CommandManager
                                                                                                        .literal(
                                                                                                                "world-copy")
                                                                                                        .executes(
                                                                                                                c ->
                                                                                                                        start(
                                                                                                                                c.getSource()
                                                                                                                                        .getPlayerOrThrow()))))
                                                                        .then(
                                                                                CommandManager
                                                                                        .literal(
                                                                                                "status")
                                                                                        .executes(
                                                                                                c -> {
                                                                                                    c.getSource()
                                                                                                            .sendFeedback(
                                                                                                                    () ->
                                                                                                                            Text
                                                                                                                                    .literal(
                                                                                                                                            status()),
                                                                                                                    false);
                                                                                                    return 1;
                                                                                                }))
                                                                        .then(
                                                                                CommandManager
                                                                                        .literal(
                                                                                                "stop")
                                                                                        .executes(
                                                                                                c ->
                                                                                                        stop()
                                                                                                                ? 1
                                                                                                                : 0))
                                                                        .then(
                                                                                CommandManager
                                                                                        .literal(
                                                                                                "list")
                                                                                        .executes(
                                                                                                c -> {
                                                                                                    try {
                                                                                                        AdvancedPlan
                                                                                                                p =
                                                                                                                        AdvancedPlan
                                                                                                                                .load();
                                                                                                        c.getSource()
                                                                                                                .sendFeedback(
                                                                                                                        () ->
                                                                                                                                Text
                                                                                                                                        .literal(
                                                                                                                                                "Actual-world stages: "
                                                                                                                                                        + p
                                                                                                                                                                .stages
                                                                                                                                                                .stream()
                                                                                                                                                                .map(
                                                                                                                                                                        s ->
                                                                                                                                                                                s.name)
                                                                                                                                                                .toList()
                                                                                                                                                        + "; auto mod structures: "
                                                                                                                                                        + p.automaticallySelectedModStructures
                                                                                                                                                        + ". Configure smooth_fix/advanced_benchmark.json. start world-copy confirms disposable save: TNT/Withers/mining/loot permanently change terrain."),
                                                                                                                        false);
                                                                                                        return 1;
                                                                                                    } catch (
                                                                                                            Exception
                                                                                                                    e) {
                                                                                                        c.getSource()
                                                                                                                .sendError(
                                                                                                                        Text
                                                                                                                                .literal(
                                                                                                                                        e
                                                                                                                                                .toString()));
                                                                                                        return 0;
                                                                                                    }
                                                                                                }))))));
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> AdvancedWorldRecovery.restore(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> {
                    if (active != null) active.finish("server_stopping");
                    drain();
                });
        ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> {
                    active = completed = null;
                    baselineGate.cancel();
                    stale.clear();
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    baselineGate.cancel();
                    if (active != null && active.owner.equals(handler.player.getUuid()))
                        active.finish("owner_disconnected", handler.player);
                });
        ServerEntityEvents.ENTITY_LOAD.register(
                (entity, world) -> {
                    for (String tag : entity.getCommandTags())
                        if (tag.startsWith(TAG) && (active == null || !tag.equals(active.tag())))
                            stale.add(entity);
                });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> stale.remove(entity));
        ServerChunkEvents.CHUNK_LOAD.register(
                (world, chunk) -> ChunkMetrics.loaded(world, chunk, true));
        ServerChunkEvents.CHUNK_UNLOAD.register(
                (world, chunk) -> ChunkMetrics.loaded(world, chunk, false));
        PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, be) -> {
                    if (world instanceof ServerWorld server
                            && active != null
                            && active.owner.equals(player.getUuid()))
                        ChunkMetrics.count(server, "playerBlocksBroken", 1);
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    var expired = baselineGate.expire(System.nanoTime());
                    if (expired != null) {
                        var waiting = server.getPlayerManager().getPlayer(expired.owner());
                        if (waiting != null)
                            waiting.sendMessage(
                                    Text.literal(
                                            "Benchmark did not start: original client snapshot timed out."),
                                    false);
                    }
                    drain();
                    if (completed != null && System.nanoTime() - completedAt > 120_000_000_000L)
                        completed = null;
                    if (active != null)
                        try {
                            active.tick();
                        } catch (Exception e) {
                            SmoothFix.LOGGER.error("Advanced benchmark failed", e);
                            Run failed = active;
                            if (failed != null) {
                                failed.report.put(
                                        "controllerFailure",
                                        Map.of(
                                                "type",
                                                e.getClass().getName(),
                                                "message",
                                                String.valueOf(e.getMessage()),
                                                "phase",
                                                failed.phase,
                                                "stage",
                                                failed.stage.name,
                                                "stack",
                                                Arrays.stream(e.getStackTrace())
                                                        .limit(48)
                                                        .map(StackTraceElement::toString)
                                                        .toList()));
                                failed.finish("failed: " + e);
                            }
                        }
                });
        ServerPlayNetworking.registerGlobalReceiver(
                BenchmarkProtocol.ADVANCED_RESPONSE,
                (server, player, handler, buf, sender) -> {
                    UUID id = buf.readUuid();
                    int phase = buf.readInt(), action = buf.readUnsignedByte();
                    String json = buf.readString(24000);
                    server.execute(() -> response(player, id, phase, action, json));
                });
    }

    private static void response(
            ServerPlayerEntity player, UUID id, int phase, int action, String json) {
        Run r = active != null && active.id.equals(id) ? active : completed;
        if (r == null || !r.id.equals(id) || !r.owner.equals(player.getUuid())) return;
        if (action == BenchmarkReportTransfer.PART_ACTION) {
            if (phase < 0 || phase >= r.results.size()) return;
            try {
                json = r.reportTransfer.accept(phase, json);
                if (json == null) return;
                action = 2;
            } catch (java.io.IOException e) {
                SmoothFix.LOGGER.warn("Invalid bounded advanced report transfer", e);
                return;
            }
        }
        if (action == 4 && r == active) {
            r.finish("client_stopped: " + json);
            return;
        }
        if (action == 0 && r == active && phase == r.phase) {
            r.finish("client_interrupted: " + json);
            return;
        }
        if (action == 6
                && r == active
                && phase == r.phase
                && Set.of("chest", "lootr").contains(r.stage.mode)) {
            if (r.container != null) r.emptyContainers.add(r.container);
            r.container = null;
            r.prepareContainer(player);
            if (r.container == null) {
                r.skip("skipped_no_nonempty_container");
                return;
            }
            r.send(3);
            return;
        }
        if (action == 7 && r == active && phase == r.phase) {
            r.heartbeat = System.nanoTime();
            return;
        }
        if (action == 5 && r == active && phase == r.phase) {
            r.result().put("clientSkipReason", json);
            r.skip("skipped_client_adapter");
            return;
        }
        if (r == active && phase == r.phase) {
            if (action == 1 && r.readyAt == 0 && r.structureSearch == null) {
                if (r.continuationAt == 0 && r.profiling) {
                    r.result().put("serverPreparation", ServerDiagnostics.finishRecording());
                    if (r.chunks != null)
                        r.result().put("chunkAndMechanicPreparation", r.chunks.finish());
                    r.chunks = ChunkMetrics.start(r.world);
                    r.result()
                            .put(
                                    "mechanicMeasurementWindow",
                                    "action_after_client_readiness_ack; preparation counters retained separately");
                    r.profiling = ServerDiagnostics.startRecording();
                    if (!r.profiling) {
                        r.finish("action_profiler_conflict");
                        return;
                    }
                }
                r.readyAt = System.nanoTime() - r.continuationAt;
                r.result().put("actionStartedUtc", java.time.Instant.now().toString());
            }
            if (action == 1 || action == 3) r.heartbeat = System.nanoTime();
        }
        if (action == 2 && phase >= 0 && phase < r.results.size())
            try {
                JsonObject summary = JsonParser.parseString(json).getAsJsonObject();
                r.results.get(phase).put("client", summary);
                boolean passed =
                        summary.has("workloadValidated")
                                && summary.get("workloadValidated").getAsBoolean();
                r.results
                        .get(phase)
                        .put(
                                "clientActionValidation",
                                passed ? "observed_success" : "not_exercised_or_failed");
                r.classify(r.results.get(phase));
                r.refreshOutcome();
                r.checkpoint();
                if (r == completed) BenchmarkFinalization.update(player, r.id, r.report);
            } catch (Exception e) {
                SmoothFix.LOGGER.warn("Invalid advanced client summary", e);
            }
    }

    private static int start(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (isRunning() || ServerBenchmark.isRunning() || ServerDiagnostics.isProfiling()) {
            player.sendMessage(Text.literal("Stop the current profile/benchmark first."), false);
            return 0;
        }
        if (server.getPlayerManager().getPlayerList().size() != 1) {
            player.sendMessage(
                    Text.literal(
                            "Advanced benchmark requires one player on this disposable server/world copy."),
                    false);
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, BenchmarkProtocol.ADVANCED_CONTROL)
                || !ServerPlayNetworking.canSend(player, BenchmarkProtocol.BASELINE_REQUEST)) {
            player.sendMessage(
                    Text.literal("Both sides need the original-state handshake benchmark build."),
                    false);
            return 0;
        }
        try {
            AdvancedPlan plan = AdvancedPlan.load();
            if (!BenchmarkRecovery.restore(player) || !AdvancedWorldRecovery.restore(server))
                throw new IllegalStateException("Unresolved recovery journal");
            var request = baselineGate.begin(player.getUuid(), plan, System.nanoTime());
            var packet = PacketByteBufs.create();
            packet.writeUuid(request.run());
            ServerPlayNetworking.send(player, BenchmarkProtocol.BASELINE_REQUEST, packet);
            player.sendMessage(
                    Text.literal(
                            "Capturing original client state BEFORE starting the disposable-world benchmark."),
                    false);
            return 1;
        } catch (Exception failure) {
            baselineGate.cancel();
            SmoothFix.LOGGER.error("Cannot request original benchmark state", failure);
            return 0;
        }
    }

    private static void startVerified(
            ServerPlayerEntity player,
            AdvancedPlan plan,
            UUID run,
            Map<String, Object> clientBefore,
            double renderScale) {
        MinecraftServer server = player.getServer();
        if (active != null
                || ServerBenchmark.isRunning()
                || ServerDiagnostics.isProfiling()
                || server.getPlayerManager().getPlayerList().size() != 1) {
            player.sendMessage(
                    Text.literal("Benchmark preflight became unavailable; no benchmark started."),
                    false);
            return;
        }
        try {
            var serverBefore =
                    RecoverySnapshot.capture(
                            player, player.interactionManager.getGameMode().getId());
            BenchmarkRecovery.saveFull(player);
            AdvancedWorldRecovery.save(server.getOverworld());
            active = new Run(player, plan, run);
            active.clientEntityRenderScale = renderScale;
            active.report.put("nativeClientEntityRenderMultiplier", renderScale);
            active.report.put("originalClientBaselineRequired", true);
            active.report.put("originalClientSnapshot", clientBefore);
            active.report.put("originalServerNetworkSnapshot", serverBefore);
            active.report.put(
                    "initialPeerNetworkComparison",
                    RecoverySnapshot.compare(serverBefore, clientBefore));
            active.checkpoint();
            active.next();
            player.sendMessage(
                    Text.literal(
                            "Advanced stress started IN THE REAL WORLD COPY after original-client acknowledgement. Stop: /smoothfix stress stop."),
                    false);
        } catch (Exception e) {
            SmoothFix.LOGGER.error("Cannot start advanced benchmark", e);
            if (active != null) active.finish("start_failed");
            else {
                BenchmarkRecovery.restore(player);
                AdvancedWorldRecovery.restore(server);
            }
            player.sendMessage(Text.literal("Advanced benchmark failed to start: " + e), false);
        }
    }

    private static void drain() {
        for (Entity e : new ArrayList<>(stale)) if (!e.isRemoved()) e.discard();
        stale.clear();
    }

    /** Spawned projectiles inherit their test owner's token; natural entities remain untouched. */
    public static void observeSpawn(Entity entity) {
        if (entity instanceof ProjectileEntity p && p.getOwner() != null)
            copyOwnership(p.getOwner(), entity);
    }

    public static void copyOwnership(Entity source, Entity target) {
        for (String tag : source.getCommandTags())
            if (tag.startsWith(TAG)) target.addCommandTag(tag);
    }

    public static void actorConverted(Entity source, Entity replacement) {
        Run r = active;
        if (r == null
                || source == null
                || replacement == null
                || source.getWorld().isClient()
                || !ownsActor(source)) return;
        copyOwnership(source, replacement);
        String types =
                Registries.ENTITY_TYPE.getId(source.getType())
                        + " -> "
                        + Registries.ENTITY_TYPE.getId(replacement.getType());
        if (r.nativeConversions.size() < 32 || r.nativeConversions.containsKey(types))
            r.nativeConversions.merge(types, 1, Integer::sum);
        r.result().put("nativeActorConversions", r.nativeConversions);
    }

    public static boolean ownsActor(Entity entity) {
        Run r = active;
        return r != null && entity != null && entity.getCommandTags().contains(r.tag());
    }

    public static void observeDamage(
            net.minecraft.entity.LivingEntity target,
            net.minecraft.entity.damage.DamageSource source) {
        Run r = active;
        if (r != null
                && source.getAttacker() instanceof ServerPlayerEntity p
                && r.owner.equals(p.getUuid())
                && target.getCommandTags().contains(r.tag()))
            ChunkMetrics.count(r.world, "playerHitsAccepted", 1);
    }

    private static final class Run {
        final UUID id, owner;
        final MinecraftServer server;
        final AdvancedPlan plan;
        final List<AdvancedPlan.Stage> stages;
        final Set<AdvancedPlan.Stage> optionalStructures =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Map<String, Object>> results = new ArrayList<>();
        final BenchmarkReportTransfer.Assembly reportTransfer =
                new BenchmarkReportTransfer.Assembly();
        final Map<String, Object> report = new LinkedHashMap<>();
        final Path file;
        final List<Entity> actors = new ArrayList<>();
        final List<BlockPos> tntSites = new ArrayList<>();
        final long started = System.nanoTime();
        final double originX, originZ;
        ServerWorld world;
        AdvancedPlan.Stage stage;
        ChunkMetrics.Recording chunks;
        int phase = -1, ticks, lastContainerId = -1, lastInventoryItems;
        String terminationReason;
        ItemStack lastHotbar;
        long phaseAt, readyAt, heartbeat, pressureAt, preparationAt, searchProgressAt, preparedAt;
        IncrementalStructureSearch structureSearch;
        boolean ending, profiling;
        double x, y, z, coldX, coldZ;
        BlockPos container, fixture;
        final Set<BlockPos> emptyContainers = new HashSet<>();
        Vec3d safePosition;
        NbtList continuationInventory;
        int continuationSelected;
        List<ItemStack> continuationStacks;
        long continuationAt;
        boolean continuing;
        BlockState fixtureOriginal;
        BlockPos lastStructure, entitySite;
        Vec3d entityCamera;
        float observerYaw, observerPitch = 27;
        double clientEntityRenderScale;
        boolean waitingEntityTerrain;
        long entityTerrainWaitAt, lastEntityTerrainRetry;
        NativeSpawnReadiness entityReadiness;
        long entityReadinessAt;
        int nativeApproaches;
        Map<String, Integer> nativeConversions = new LinkedHashMap<>();
        int nativeActionSamples,
                minimumNativeActionAlive = Integer.MAX_VALUE,
                maximumNativeActionAlive;

        Run(ServerPlayerEntity player, AdvancedPlan plan, UUID run) {
            this.id = run;
            this.owner = player.getUuid();
            this.server = player.getServer();
            this.plan = plan;
            this.stages = new ArrayList<>(plan.stages);
            originX =
                    player.getWorld() == server.getOverworld()
                            ? player.getX()
                            : server.getOverworld().getSpawnPos().getX();
            originZ =
                    player.getWorld() == server.getOverworld()
                            ? player.getZ()
                            : server.getOverworld().getSpawnPos().getZ();
            var registry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
            int added = 0,
                    index =
                            !stages.isEmpty() && stages.get(stages.size() - 1).mode.equals("save")
                                    ? stages.size() - 1
                                    : stages.size();
            for (Identifier key :
                    registry.getIds().stream()
                            .sorted(Comparator.comparing(Identifier::toString))
                            .toList())
                if (!key.getNamespace().equals("minecraft")
                        && added < plan.automaticallySelectedModStructures) {
                    var entry = registry.entryOf(RegistryKey.of(RegistryKeys.STRUCTURE, key));
                    if (!server.getOverworld()
                            .getChunkManager()
                            .getStructurePlacementCalculator()
                            .getPlacements(entry)
                            .isEmpty()) {
                        var optional =
                                new AdvancedPlan.Stage(
                                        "mod_structure_" + key, "structure", key.toString(), 0);
                        optionalStructures.add(optional);
                        stages.add(index++, optional);
                        added++;
                    }
                }
            file =
                    server.getSavePath(WorldSavePath.ROOT)
                            .resolve("smooth_fix/advanced_benchmarks/" + id + ".json");
            report.put("independentJvm", BenchmarkSession.snapshot());
            report.put("runId", id.toString());
            report.put("build", SmoothFix.BUILD_ID);
            report.put("suite", "actual_world_advanced_v2");
            report.put("status", "running");
            report.put("stages", results);
            report.put(
                    "installedMods",
                    net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream()
                            .map(
                                    m ->
                                            Map.of(
                                                    "id",
                                                    m.getMetadata().getId(),
                                                    "version",
                                                    m.getMetadata()
                                                            .getVersion()
                                                            .getFriendlyString()))
                            .toList());
            report.put("worldSeed", server.getOverworld().getSeed());
            report.put("worldCopyAcknowledged", true);
            report.put(
                    "limitations",
                    List.of(
                            "Terrain destruction, generated chunks, loot state, advancements and mod-global effects are permanent in the test copy.",
                            "Scripted camera/flight/native attacks and UI actions; no claim of every mod-specific ability being forced.",
                            "Client/server clocks are not synchronized. Frame/server action recorders restart after initial client readiness; preparation is retained separately. Chunk/mechanic hooks explicitly include preparation. Recovery inside an action window makes that stage unsuitable for controlled A/B comparison. First-render latency excludes GPU completion.",
                            "Structure search expands in block-radius bands until found or the world border is exhausted. Disabled, unregistered or unsupported placements are explicitly skipped. Native structure-start generation can add work while searching.",
                            "Routes become warm on repeated runs. A cold route is validated only by observed generation hooks.",
                            "Hook timings/sampling add overhead; no GPU hardware counters or index rebake is forced."));
        }

        String tag() {
            return TAG + id + ":" + phase;
        }

        ServerPlayerEntity player() {
            return server.getPlayerManager().getPlayer(owner);
        }

        Map<String, Object> result() {
            return results.get(phase);
        }

        void send(int action) {
            ServerPlayerEntity p = player();
            if (p == null) return;
            var data = new LinkedHashMap<String, Object>();
            data.put("action", action);
            data.put("run", id.toString());
            data.put("phase", phase);
            data.put("stage", stage == null ? "finished" : stage.name);
            data.put("mode", stage == null ? "stop" : stage.mode);
            data.put("targetCount", stage == null ? 0 : stage.count);
            data.put("fpsLimit", stage == null ? 0 : stage.fpsLimit);
            data.put("observerYaw", observerYaw);
            data.put("observerPitch", observerPitch);
            data.put("totalStages", stages.size());
            data.put(
                    "nextStage",
                    phase + 1 < stages.size()
                            ? stages.get(phase + 1).name
                            : "Finish and restore player");
            data.put(
                    "optionalSearchSeconds",
                    stage != null && optionalStructures.contains(stage)
                            ? plan.optionalModStructureSearchSeconds
                            : 0);
            data.put("prepared", action == 3 || action == 8);
            data.put("continuing", continuing);
            data.put("seconds", stage == null ? 0 : stage.seconds);
            data.put(
                    "dimension",
                    world == null
                            ? "minecraft:overworld"
                            : world.getRegistryKey().getValue().toString());
            data.put("x", x);
            data.put("y", y);
            data.put("z", z);
            data.put("queries", plan.emiQueries);
            data.put(
                    "entities",
                    actors.stream()
                            .filter(e -> e instanceof MobEntity && e.isAlive() && !e.isRemoved())
                            .filter(
                                    e ->
                                            stage != null && stage.mode.equals("entities")
                                                    ? Registries.ENTITY_TYPE
                                                            .getId(e.getType())
                                                            .toString()
                                                            .equals(stage.target)
                                                    : e.getType() != EntityType.IRON_GOLEM)
                            .map(Entity::getId)
                            .toList());
            data.put("actionElapsedSeconds", continuing ? continuationAt / 1e9 : 0);
            data.put("searchingStructure", structureSearch != null);
            if (structureSearch != null) data.put("structureSearch", structureSearch.progress());
            if (container != null)
                data.put(
                        "container",
                        Map.of(
                                "x",
                                container.getX(),
                                "y",
                                container.getY(),
                                "z",
                                container.getZ()));
            var buf = PacketByteBufs.create();
            buf.writeString(GSON.toJson(data), 24000);
            ServerPlayNetworking.send(p, BenchmarkProtocol.ADVANCED_CONTROL, buf);
        }

        void next() throws Exception {
            cleanup();
            if (++phase >= stages.size()) {
                finish("completed");
                return;
            }
            stage = stages.get(phase);
            waitingEntityTerrain = false;
            entityTerrainWaitAt = 0;
            lastEntityTerrainRetry = 0;
            entityReadiness = null;
            nativeApproaches = 0;
            nativeConversions = new LinkedHashMap<>();
            nativeActionSamples = 0;
            minimumNativeActionAlive = Integer.MAX_VALUE;
            maximumNativeActionAlive = 0;
            ticks = 0;
            readyAt = 0;
            heartbeat = phaseAt = System.nanoTime();
            container = null;
            lastContainerId = -1;
            emptyContainers.clear();
            safePosition = null;
            continuationInventory = null;
            continuationStacks = null;
            continuing = false;
            continuationAt = 0;
            world =
                    server.getWorld(
                            stage.mode.equals("nether")
                                    ? World.NETHER
                                    : stage.mode.equals("end") ? World.END : World.OVERWORLD);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phase", phase);
            data.put("name", stage.name);
            data.put("mode", stage.mode);
            data.put("target", stage.target);
            data.put("requestedEntities", stage.count);
            data.put("status", "preparing");
            data.put("clientActionValidation", "awaiting_client");
            results.add(data);
            player().sendMessage(
                            Text.literal(
                                    "[Smooth Fix Advanced "
                                            + (phase + 1)
                                            + "/"
                                            + stages.size()
                                            + "] "
                                            + stage.name
                                            + " — "
                                            + AdvancedStageInfo.task(stage.mode)
                                            + ". Action duration "
                                            + stage.seconds
                                            + " seconds."),
                            false);
            if (world == null) {
                data.put("status", "skipped_dimension_missing");
                checkpoint();
                next();
                return;
            }
            x = originX;
            z = originZ;
            if (stage.mode.equals("structure") && player().getServerWorld() == world) {
                x = player().getX();
                z = player().getZ();
            }
            if (stage.mode.equals("cold_route")) {
                coldX = originX + 8192 + ((owner.getLeastSignificantBits() & 31) * 1024);
                coldZ = originZ + 8192;
                x = coldX;
                z = coldZ;
            }
            if (stage.centerX != null) {
                x = stage.centerX;
                z = stage.centerZ;
                data.put("frozenAnchor", Map.of("x", x, "z", z));
                if (stage.mode.equals("cold_route")) {
                    coldX = x;
                    coldZ = z;
                }
            }
            if (stage.target.equals("previous_cold")) {
                x = coldX;
                z = coldZ;
            }
            if (stage.mode.equals("nether")) {
                x = originX / 8;
                z = originZ / 8;
            }
            if (stage.mode.equals("end")) {
                x = 0;
                z = 0;
            }
            if ((stage.mode.equals("chest") || stage.mode.equals("lootr"))
                    && lastStructure != null) {
                x = lastStructure.getX();
                z = lastStructure.getZ();
            }
            y = world == server.getWorld(World.NETHER) ? 100 : 140;
            // Start both recorders BEFORE native teleport/chunk generation and synchronous setup
            // work.
            send(1);
            chunks = ChunkMetrics.start(world);
            if (!ServerDiagnostics.startRecording())
                throw new IllegalStateException("Profiler conflict");
            profiling = true;
            data.put("worldBeforePreparation", snapshot());
            ServerPlayerEntity p = player();
            p.closeHandledScreen();
            p.changeGameMode(GameMode.SURVIVAL);
            BenchmarkAbilities.grant(p);
            p.teleport(world, x, y, z, 0, 20);
            preparationAt = System.nanoTime();
            if (stage.mode.equals("structure")) {
                var registry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                RegistryEntryList<Structure> entries;
                if (stage.target.equals("#minecraft:village"))
                    entries = VillageStructureTargets.resolve(world);
                else if (stage.target.startsWith("#"))
                    entries =
                            registry.getEntryList(
                                            TagKey.of(
                                                    RegistryKeys.STRUCTURE,
                                                    new Identifier(stage.target.substring(1))))
                                    .orElse(null);
                else {
                    var key = RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(stage.target));
                    entries = registry.getEntry(key).map(RegistryEntryList::of).orElse(null);
                }
                if (entries == null || entries.size() == 0) {
                    skip("skipped_structure_unregistered");
                    return;
                }
                List<String> targetIds = new ArrayList<>();
                for (var entry : entries)
                    entry.getKey().ifPresent(key -> targetIds.add(key.getValue().toString()));
                data.put("resolvedStructureTargets", targetIds);
                structureSearch =
                        new IncrementalStructureSearch(
                                world,
                                entries,
                                new BlockPos(MathHelper.floor(x), 80, MathHelper.floor(z)),
                                plan.structureSearchRadiusStepBlocks);
                data.put("structureSearchOrigin", Map.of("x", x, "z", z));
                data.put("status", "searching_structure");
                data.put("structureSearch", structureSearch.progress());
                if (structureSearch.unavailable() != null) {
                    skip(structureSearch.unavailable());
                    return;
                }
                send(6);
                searchProgressAt = System.nanoTime();
                checkpoint();
                return;
            }
            prepareStage(p);
        }

        GroundRoutePlanner.Point findStanding(ServerPlayerEntity p) {
            var terrain = new GroundCollisionTerrain(world, p);
            int cx = MathHelper.floor(x), cz = MathHelper.floor(z);
            int candidates = 0;
            Set<GroundRoutePlanner.Point> tested = new HashSet<>();
            boolean nether = world == server.getWorld(World.NETHER);
            for (int radius = 0; radius <= 12; radius += 2)
                for (int dx = -radius; dx <= radius; dx += 2)
                    for (int dz = -radius; dz <= radius; dz += 2) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius
                                || !world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) continue;
                        int top =
                                nether
                                        ? 110
                                        : world.getTopY(
                                                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                                cx + dx,
                                                cz + dz);
                        for (int height = top; height >= (nether ? 32 : top); height--) {
                            var point =
                                    terrain.step(
                                            new GroundRoutePlanner.Point(cx + dx, height, cz + dz),
                                            0,
                                            0);
                            if (point == null || !tested.add(point)) continue;
                            Set<GroundRoutePlanner.Point> seen = new HashSet<>();
                            ArrayDeque<GroundRoutePlanner.Point> queue = new ArrayDeque<>();
                            seen.add(point);
                            queue.add(point);
                            double extent = 0;
                            while (!queue.isEmpty() && seen.size() < 256) {
                                var at = queue.removeFirst();
                                extent =
                                        Math.max(
                                                extent,
                                                Math.hypot(at.x() - point.x(), at.z() - point.z()));
                                for (var d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                    var next = terrain.step(at, d[0], d[1]);
                                    if (next != null && seen.add(next)) queue.addLast(next);
                                }
                            }
                            if (extent >= 8 && seen.size() >= 24) {
                                result().put("walkableStartConnectedNodes", seen.size());
                                return point;
                            }
                            if (++candidates >= 64) return null;
                        }
                    }
            return null;
        }

        void prepareStage(ServerPlayerEntity p) throws Exception {
            Map<String, Object> data = result();
            world.getChunk(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4);
            if (world != server.getWorld(World.NETHER))
                y =
                        world.getTopY(
                                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                        MathHelper.floor(x),
                                        MathHelper.floor(z))
                                + 10;
            if (AdvancedStageInfo.walking(stage.mode)) {
                var standing = findStanding(p);
                if (standing == null) {
                    skip("skipped_no_safe_walkable_start");
                    return;
                }
                x = standing.x() + .5;
                y = standing.y();
                z = standing.z() + .5;
            }
            p.teleport(world, x, y, z, 0, 25);
            p.setVelocity(Vec3d.ZERO);
            p.fallDistance = 0;
            BenchmarkAbilities.grant(p, !AdvancedStageInfo.walking(stage.mode));
            safePosition = p.getPos();
            if (stage.mode.equals("entities")) {
                BlockPos dry =
                        entitySite == null
                                ? findEntitySurface(MathHelper.floor(x), MathHelper.floor(z))
                                : entitySite;
                entitySite = dry;
                if (dry == null) {
                    long now = System.nanoTime();
                    if (entityTerrainWaitAt == 0) entityTerrainWaitAt = now;
                    waitingEntityTerrain = true;
                    lastEntityTerrainRetry = now;
                    Map<String, Integer> classes = new TreeMap<>();
                    int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
                    for (int cx = (bx - 16) >> 4; cx <= (bx + 16) >> 4; cx++)
                        for (int cz = (bz - 16) >> 4; cz <= (bz + 16) >> 4; cz++) {
                            Chunk chunk =
                                    world.getChunkManager()
                                            .getChunk(cx, cz, ChunkStatus.FULL, false);
                            String type =
                                    chunk == null ? "not_available" : chunk.getClass().getName();
                            classes.merge(type, 1, Integer::sum);
                        }
                    int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    data.put(
                            "nativeTerrainReadiness",
                            Map.of(
                                    "elapsedSeconds",
                                    (now - entityTerrainWaitAt) / 1e9,
                                    "chunkClasses",
                                    classes,
                                    "centerTopY",
                                    top,
                                    "centerGround",
                                    Registries.BLOCK
                                            .getId(
                                                    world.getBlockState(
                                                                    new BlockPos(bx, top - 1, bz))
                                                            .getBlock())
                                            .toString(),
                                    "policy",
                                    "Normal player chunk tickets after teleport; retry only preparation. Original33x33 fluid/head-clearance/height/render gates unchanged; no force-load tickets or block edits here."));
                    if (now - entityTerrainWaitAt >= 30_000_000_000L) {
                        waitingEntityTerrain = false;
                        skip("skipped_entity_scene_no_loaded_dry_terrain");
                        return;
                    }
                    data.put("status", "waiting_for_native_loaded_entity_terrain");
                    send(6);
                    checkpoint();
                    return;
                }
                waitingEntityTerrain = false;
                x = dry.getX() + .5;
                z = dry.getZ() + .5;
                Vec3d center = new Vec3d(x, dry.getY() + 1.2, z);
                if (entityCamera == null) entityCamera = findEntityObserver(center, p);
                if (entityCamera == null) {
                    skip("skipped_entity_scene_no_clear_observer");
                    return;
                }
                Vec3d look = center.subtract(entityCamera.add(0, p.getEyeY() - p.getY(), 0));
                observerYaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90);
                observerPitch =
                        (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
                y = entityCamera.y;
                p.teleport(world, entityCamera.x, y, entityCamera.z, observerYaw, observerPitch);
                result().put("entityTerrain", "loaded_dry_surface");
                result().put(
                                "entityObserver",
                                Map.of(
                                        "x",
                                        entityCamera.x,
                                        "y",
                                        y,
                                        "z",
                                        entityCamera.z,
                                        "yaw",
                                        observerYaw,
                                        "pitch",
                                        observerPitch,
                                        "centerRayUnobstructed",
                                        true));
            }
            if (Set.of("entities", "combat", "effects", "bloodmoon", "wither")
                    .contains(stage.mode)) {
                if (stage.mode.equals("bloodmoon")) {
                    if (!BloodMoonAdapter.available()
                            || !AdvancedWorldRecovery.canForceMoon(server)) {
                        skip("skipped_blood_moon_adapter_or_backup_unavailable");
                        return;
                    }
                    // Enhanced Celestials suppresses lunar events while rain strength is visible.
                    world.setWeather(24000, 0, false, false);
                    world.setRainGradient(0);
                    world.setThunderGradient(0);
                    data.put(
                            "nativeBloodMoonPreparation",
                            BloodMoonAdapter.prepareActiveNight(world));
                }
                EntityType<?> type =
                        Registries.ENTITY_TYPE
                                .getOrEmpty(new Identifier(stage.target))
                                .orElse(null);
                if (type == null) {
                    skip("skipped_entity_unregistered");
                    return;
                }
                spawn(type, stage.count);
                if (!stage.mode.equals("combat") && !stage.mode.equals("entities"))
                    spawn(EntityType.IRON_GOLEM, 8);
                p.getInventory().setStack(8, new ItemStack(Items.DIAMOND_SWORD));
                p.getInventory().selectedSlot = 8;
            }
            if (stage.mode.equals("wither")) {
                y =
                        world.getTopY(
                                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                        MathHelper.floor(x),
                                        MathHelper.floor(z))
                                + 48;
                p.teleport(world, x, y, z, 0, 70);
                p.setVelocity(Vec3d.ZERO);
                safePosition = p.getPos();
                data.put("witherObserverHeightAboveTerrain", 48);
                data.put(
                        "witherObserverPolicy",
                        "Survival observer with unchanged temporary protection, above native blast area; nativeWither charge, targets, projectiles, explosions and all mob physics unchanged. No distance setting changes.");
            }
            if (stage.mode.equals("chest") || stage.mode.equals("lootr")) {
                prepareContainer(p);
                if (container == null) {
                    skip("skipped_lootr_block_unavailable");
                    return;
                }
            }
            if (stage.mode.equals("inventory") || stage.mode.startsWith("emi_")) fillInventory(p);
            if (stage.mode.equals("tnt")) {
                BlockPos dry = findDrySurface(MathHelper.floor(x), MathHelper.floor(z), 64);
                if (dry == null) {
                    skip("skipped_no_loaded_dry_terrain_for_tnt");
                    return;
                }
                x = dry.getX() + .5;
                z = dry.getZ() + .5;
                y = dry.getY() + 8;
                tntSites.clear();
                for (int i = 0; i < stage.count; i++) {
                    double angle = (i + 1) * 2.4;
                    BlockPos site =
                            findDrySurface(
                                    MathHelper.floor(x + Math.cos(angle) * 10),
                                    MathHelper.floor(z + Math.sin(angle) * 10),
                                    8);
                    tntSites.add(site == null ? dry : site);
                }
                p.teleport(world, x, y, z, 0, 35);
                BenchmarkAbilities.grant(p);
                data.put("observerHeightAboveTerrain", 8);
                data.put(
                        "tntSites",
                        tntSites.stream()
                                .map(
                                        pos ->
                                                Map.of(
                                                        "x",
                                                        pos.getX(),
                                                        "y",
                                                        pos.getY(),
                                                        "z",
                                                        pos.getZ()))
                                .toList());
            }
            if (stage.mode.equals("weather")) {
                BlockPos rain = findNativeRainObserver(MathHelper.floor(x), MathHelper.floor(z));
                if (rain == null) {
                    skip("skipped_no_loaded_native_rain_biome_observer");
                    return;
                }
                x = rain.getX() + .5;
                y = rain.getY();
                z = rain.getZ() + .5;
                p.teleport(world, x, y, z, 0, 25);
                p.setVelocity(Vec3d.ZERO);
                safePosition = p.getPos();
                world.setWeather(0, 24000, true, true);
                data.put(
                        "nativeRainObserver",
                        Map.of(
                                "x",
                                x,
                                "y",
                                y,
                                "z",
                                z,
                                "biome",
                                world.getBiome(rain).getKey().orElseThrow().getValue().toString(),
                                "policy",
                                "Actual loaded rainy biome above native roof height; native weather/renderer, no artificial particle system. Client must observe actual rain state, gradient and sky exposure."));
            }
            if (stage.mode.equals("break")) {
                p.getInventory().setStack(8, new ItemStack(Items.DIAMOND_PICKAXE));
                p.getInventory().selectedSlot = 8;
                data.put(
                        "nativeMiningPolicy",
                        "Ordinary unenchanted diamond pickaxe, held vanilla attack input in3s mining/2s navigation bursts; no direct block edits, hardness/attack-speed changes or repeated per-tick manual attack resets. Original inventory journal restored.");
            }

            if (stage.mode.equals("entities")) {
                // Native Spawn Animations hides hostiles until a nearby player activates them.
                // Do not clear its effects/tags or disable AI: approach during preparation and
                // wait.
                entityReadiness = new NativeSpawnReadiness(stage.count);
                entityReadinessAt = System.nanoTime();
                p.changeGameMode(GameMode.CREATIVE);
                p.teleport(world, x, entitySite.getY() + 5, z, 0, 25);
                p.setVelocity(Vec3d.ZERO);
                data.put("status", "waiting_for_native_entity_visibility_and_ai");
                data.put(
                        "nativeEntityPreparationPolicy",
                        "Approach natural crowd during preparation only, using temporary creative player protection5blocks above its center. Native Spawn Animations includes nonspectator creative players. Approach still-hidden actors without editing them, respecting native Manhattan activation distance. Require the exact33x33 dry unobstructed native spawn footprint. A97x97 terrain prerequisite was rejected as neither necessary nor sufficient for native AI travel; every-tick loaded live requested-type counts and actual renderer coverage remain mandatory. Wait for native visibility, AI and animation completion; replenish preparation losses with native spawns, maximum2x requested. Restore survival before timing. Minimum40 world ticks and20 consecutive settled ticks;120s wall timeout. No mob effects/tags/AI/config changed.");
                preparedAt = System.nanoTime();
                captureContinuation(p);
                checkpoint();
                return;
            }
            finishPreparation(p, data);
        }

        void finishPreparation(ServerPlayerEntity p, Map<String, Object> data) {
            p.getInventory().markDirty();
            p.playerScreenHandler.syncState();
            p.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(
                            p.getInventory().selectedSlot));
            lastHotbar = p.getInventory().getStack(0).copy();
            lastInventoryItems = inventoryItems(p);
            data.put("preparationMs", (System.nanoTime() - preparationAt) / 1e6);
            data.put("worldAfterPreparation", snapshot());
            data.put("status", "waiting_for_client");
            preparedAt = System.nanoTime();
            captureContinuation(p);
            send(3);
            checkpoint();
        }

        void tickEntityPreparation(ServerPlayerEntity p, long now) throws Exception {
            int alive = 0, visible = 0, ai = 0, finished = 0;
            EntityType<?> requested = Registries.ENTITY_TYPE.get(new Identifier(stage.target));
            for (Entity actor : actors)
                if (actor.isAlive()
                        && actor.getType() == requested
                        && actor instanceof MobEntity mob) {
                    alive++;
                    if (!actor.isInvisible()) visible++;
                    if (!mob.isAiDisabled()) ai++;
                    Set<String> tags = actor.getCommandTags();
                    if (!tags.contains("ts.sa.to_verify")
                            && !tags.contains("ts.sa.verify")
                            && !tags.contains("ts.sa.to_hide")
                            && !tags.contains("ts.sa.hidden")) finished++;
                }
            if (entityReadiness.ticks() % 60 == 0) approachPendingNativeActor(p);
            if (alive < stage.count && entityReadiness.ticks() % 20 == 0) {
                int replacements =
                        ((Number) result().getOrDefault("nativeEntityPreparationRespawns", 0))
                                .intValue();
                int add = Math.min(8, stage.count - alive);
                if (replacements + add > stage.count * 2) {
                    entityReadiness = null;
                    skip("skipped_excessive_native_entity_preparation_losses");
                    return;
                }
                spawn(requested, add);
                result().put("nativeEntityPreparationRespawns", replacements + add);
                actors.removeIf(Entity::isRemoved);
            }
            boolean ready = entityReadiness.observe(alive, visible, ai, finished);
            result().put(
                            "nativeEntityReadiness",
                            Map.of(
                                    "worldTicksObserved",
                                    entityReadiness.ticks(),
                                    "consecutiveSettledTicks",
                                    entityReadiness.stableTicks(),
                                    "requested",
                                    stage.count,
                                    "alive",
                                    alive,
                                    "visible",
                                    visible,
                                    "nativeAiReady",
                                    ai,
                                    "spawnAnimationFinished",
                                    finished,
                                    "elapsedWallSeconds",
                                    (now - entityReadinessAt) / 1e9));
            if (now - entityReadinessAt > 120_000_000_000L) {
                entityReadiness = null;
                skip("skipped_native_entity_visibility_or_ai_not_ready");
                return;
            }
            if (!ready) {
                if (entityReadiness.ticks() % 20 == 0) send(6);
                if (entityReadiness.ticks() % 100 == 0) checkpoint();
                return;
            }
            entityReadiness = null;
            Vec3d actualCamera = findActualEntityObserver(p);
            if (actualCamera == null) {
                p.changeGameMode(GameMode.SURVIVAL);
                skip("skipped_native_crowd_no_clear_actual_observer");
                return;
            }
            entityCamera = actualCamera;
            y = actualCamera.y;
            result().put(
                            "entityObserver",
                            Map.of(
                                    "x",
                                    actualCamera.x,
                                    "y",
                                    actualCamera.y,
                                    "z",
                                    actualCamera.z,
                                    "yaw",
                                    observerYaw,
                                    "pitch",
                                    observerPitch,
                                    "allActualNativeEyeRaysClear",
                                    true));
            p.changeGameMode(GameMode.SURVIVAL);
            BenchmarkAbilities.grant(p);
            p.teleport(
                    world,
                    entityCamera.x,
                    entityCamera.y,
                    entityCamera.z,
                    observerYaw,
                    observerPitch);
            p.setVelocity(Vec3d.ZERO);
            result().put("observerFlyingAfterNativeModeTransition", p.getAbilities().flying);
            result().put("nativeEntityPreparationCompleted", true);
            finishPreparation(p, result());
        }

        void approachPendingNativeActor(ServerPlayerEntity p) {
            // The installed Spawn Animations activation uses |dx|+|dy|+|dz| <=20.
            // Fly near a still-hidden native actor during PREPARATION; never edit that actor.
            for (Entity actor : actors)
                if (actor instanceof MobEntity mob
                        && actor.isAlive()
                        && (actor.isInvisible()
                                || actor.getCommandTags().contains("ts.sa.to_verify")
                                || actor.getCommandTags().contains("ts.sa.to_hide"))) {
                    for (int height = 5; height <= 9; height++) {
                        BlockPos feet =
                                BlockPos.ofFloored(
                                        actor.getX(), actor.getY() + height, actor.getZ());
                        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                                || !world.getBlockState(feet.up())
                                        .getCollisionShape(world, feet.up())
                                        .isEmpty()
                                || !world.getFluidState(feet).isEmpty()
                                || !world.getFluidState(feet.up()).isEmpty()) continue;
                        p.teleport(world, actor.getX(), actor.getY() + height, actor.getZ(), 0, 25);
                        p.setVelocity(Vec3d.ZERO);
                        nativeApproaches++;
                        result().put("nativePlayerPreparationApproaches", nativeApproaches);
                        return;
                    }
                }
        }

        Vec3d findActualEntityObserver(ServerPlayerEntity p) {
            EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(stage.target));
            List<Vec3d> eyes = new ArrayList<>();
            List<Entity> nativeTargets = new ArrayList<>();
            for (Entity actor : actors)
                if (actor instanceof MobEntity mob
                        && actor.isAlive()
                        && !actor.isRemoved()
                        && actor.getType() == type) {
                    eyes.add(mob.getEyePos());
                    nativeTargets.add(actor);
                }
            Map<String, Object> audit = new LinkedHashMap<>();
            result().put("actualActorObserverAudit", audit);
            audit.put("actualNativeTargets", eyes.size());
            audit.put("nativeEntityRenderMultiplier", clientEntityRenderScale);
            audit.put("nativeBoundingBoxRangeSafetyFactor", 0.9);
            audit.put(
                    "observerRevision",
                    "Camera17: native actor-specific distance gate retained; measured source16 footprint cannot satisfy arbitrary64block cap plus27degree cone. All-target actual renderer acceptance unchanged.");
            audit.put(
                    "policy",
                    "After native visibility/AI readiness, validate every actual native eye position, not only a sparse pre-spawn terrain grid. Loaded FULL chunks only; native COLLIDER rays; actual per-entity native bounding-box render range with0.9 safety factor and unchanged27degree view cone; no arbitrary64block camera cap. No AI, culling, FOV, simulation, entity-distance or render-threshold changes. Actual renderer gate remains mandatory.");
            if (eyes.size() != stage.count) return null;
            double minX = Double.POSITIVE_INFINITY,
                    maxX = Double.NEGATIVE_INFINITY,
                    minY = minX,
                    maxY = maxX,
                    minZ = minX,
                    maxZ = maxX;
            for (Vec3d v : eyes) {
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
                minZ = Math.min(minZ, v.z);
                maxZ = Math.max(maxZ, v.z);
            }
            Vec3d center = new Vec3d((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            double eyeHeight = p.getEyeY() - p.getY();
            audit.put(
                    "actualEyeBounds",
                    Map.of(
                            "minX", minX, "maxX", maxX, "minY", minY, "maxY", maxY, "minZ", minZ,
                            "maxZ", maxZ));
            int candidates = 0, blocked = 0, unloaded = 0, outside = 0;
            Map<String, Integer> blockerTypes = new LinkedHashMap<>();
            audit.put("blockerTypes", blockerTypes);
            Map<Long, Boolean> readyChunks = new HashMap<>();
            for (int radius : new int[] {0, 12, 20, 28, 36})
                for (int height : new int[] {72, 64, 80, 56, 48, 40, 36, 28, 20, 14, 8, 5})
                    for (int direction = 0; direction < 8; direction++) {
                        candidates++;
                        audit.put("candidates", candidates);
                        double angle = direction * Math.PI / 4;
                        Vec3d
                                camera =
                                        center.add(
                                                Math.sin(angle) * radius,
                                                height,
                                                -Math.cos(angle) * radius),
                                eye = camera.add(0, eyeHeight, 0),
                                forward = center.subtract(eye).normalize();
                        BlockPos feet = BlockPos.ofFloored(camera);
                        boolean valid = true;
                        double farthest = 0, widest = 0;
                        if (!world.isChunkLoaded(feet.getX() >> 4, feet.getZ() >> 4)) {
                            unloaded++;
                            continue;
                        }
                        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                                || !world.getBlockState(feet.up())
                                        .getCollisionShape(world, feet.up())
                                        .isEmpty()
                                || !world.getFluidState(feet).isEmpty()) continue;
                        for (int targetIndex = 0; targetIndex < eyes.size(); targetIndex++) {
                            Vec3d target = eyes.get(targetIndex);
                            Entity nativeTarget = nativeTargets.get(targetIndex);
                            double side = nativeTarget.getBoundingBox().getAverageSideLength();
                            if (Double.isNaN(side)) side = 1;
                            double nativeRange = side * 64 * clientEntityRenderScale;
                            // Conservative camera selection only. Never change the native
                            // threshold, entity dimensions or culling.
                            if (nativeTarget.getPos().squaredDistanceTo(eye)
                                    >= nativeRange * nativeRange * .81) {
                                outside++;
                                valid = false;
                                break;
                            }
                            Vec3d delta = target.subtract(eye);
                            double distance = delta.length(),
                                    dot = delta.normalize().dotProduct(forward);
                            farthest = Math.max(farthest, distance);
                            widest =
                                    Math.max(
                                            widest,
                                            Math.toDegrees(
                                                    Math.acos(MathHelper.clamp(dot, -1, 1))));
                            if (dot < Math.cos(Math.toRadians(27))) {
                                outside++;
                                valid = false;
                                break;
                            }
                            int ax = MathHelper.floor(Math.min(eye.x, target.x)) >> 4,
                                    bx = MathHelper.floor(Math.max(eye.x, target.x)) >> 4,
                                    az = MathHelper.floor(Math.min(eye.z, target.z)) >> 4,
                                    bz = MathHelper.floor(Math.max(eye.z, target.z)) >> 4;
                            for (int cx = ax; cx <= bx && valid; cx++)
                                for (int cz = az; cz <= bz; cz++) {
                                    long key = ChunkPos.toLong(cx, cz);
                                    Boolean ready = readyChunks.get(key);
                                    if (ready == null) {
                                        ready =
                                                world.getChunkManager()
                                                                .getChunk(
                                                                        cx,
                                                                        cz,
                                                                        ChunkStatus.FULL,
                                                                        false)
                                                        instanceof WorldChunk;
                                        readyChunks.put(key, ready);
                                    }
                                    if (!ready) {
                                        unloaded++;
                                        valid = false;
                                        break;
                                    }
                                }
                            if (!valid) break;
                            var hit =
                                    world.raycast(
                                            new net.minecraft.world.RaycastContext(
                                                    eye,
                                                    target,
                                                    net.minecraft.world.RaycastContext.ShapeType
                                                            .COLLIDER,
                                                    net.minecraft.world.RaycastContext.FluidHandling
                                                            .NONE,
                                                    p));
                            if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
                                String id =
                                        Registries.BLOCK
                                                .getId(
                                                        world.getBlockState(hit.getBlockPos())
                                                                .getBlock())
                                                .toString();
                                if (blockerTypes.size() < 16 || blockerTypes.containsKey(id))
                                    blockerTypes.merge(id, 1, Integer::sum);
                                blocked++;
                                valid = false;
                                break;
                            }
                        }
                        if (valid) {
                            Vec3d look = center.subtract(eye);
                            observerYaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90);
                            observerPitch =
                                    (float)
                                            -Math.toDegrees(
                                                    Math.atan2(look.y, Math.hypot(look.x, look.z)));
                            audit.put("selected", true);
                            audit.put("allNativeEyeRaysClear", true);
                            audit.put("maximumTargetDistanceBlocks", farthest);
                            audit.put("maximumTargetAngleDegrees", widest);
                            audit.put(
                                    "camera",
                                    Map.of(
                                            "x",
                                            camera.x,
                                            "y",
                                            camera.y,
                                            "z",
                                            camera.z,
                                            "yaw",
                                            observerYaw,
                                            "pitch",
                                            observerPitch));
                            audit.put("blockedCandidates", blocked);
                            audit.put("unloadedRejections", unloaded);
                            audit.put("outsideViewRejections", outside);
                            return camera;
                        }
                    }
            audit.put("selected", false);
            audit.put("blockedCandidates", blocked);
            audit.put("unloadedRejections", unloaded);
            audit.put("outsideViewRejections", outside);
            return null;
        }

        void prepareContainer(ServerPlayerEntity p) {
            boolean lootr = stage.mode.equals("lootr");
            if (fixture != null) {
                world.setBlockState(fixture, fixtureOriginal);
                fixture = null;
                fixtureOriginal = null;
            }
            if (emptyContainers.isEmpty()) {
                for (int slot = 0; slot < 36; slot++)
                    p.getInventory().setStack(slot, ItemStack.EMPTY);
                p.getInventory().markDirty();
                p.playerScreenHandler.sendContentUpdates();
            }
            for (int cx = (MathHelper.floor(x) >> 4) - 4;
                    cx <= (MathHelper.floor(x) >> 4) + 4 && container == null;
                    cx++)
                for (int cz = (MathHelper.floor(z) >> 4) - 4;
                        cz <= (MathHelper.floor(z) >> 4) + 4 && container == null;
                        cz++) {
                    Chunk chunk = world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk instanceof WorldChunk full)
                        for (var entry : full.getBlockEntities().entrySet()) {
                            String id =
                                    Registries.BLOCK
                                            .getId(full.getBlockState(entry.getKey()).getBlock())
                                            .toString();
                            if (emptyContainers.contains(entry.getKey())) continue;
                            if (entry.getValue()
                                            instanceof
                                            net.minecraft.screen.NamedScreenHandlerFactory
                                    && (lootr
                                            ? id.startsWith("lootr:")
                                            : id.equals("minecraft:chest")
                                                    || id.equals("minecraft:barrel"))) {
                                if (!lootr) {
                                    if (entry.getValue()
                                            instanceof LootableContainerBlockEntity loot)
                                        loot.checkLootInteraction(p);
                                    if (!(entry.getValue()
                                                    instanceof
                                                    net.minecraft.inventory.Inventory inventory)
                                            || !ContainerSlots.hasItems(inventory)) {
                                        result().put(
                                                        "emptyContainersRejected",
                                                        ((Number)
                                                                                result().getOrDefault(
                                                                                                "emptyContainersRejected",
                                                                                                0))
                                                                        .intValue()
                                                                + 1);
                                        continue;
                                    }
                                }
                                if (emptyContainers.size() >= 8) continue;
                                container = entry.getKey().toImmutable();
                                result().put(
                                                "containerSource",
                                                "existing_generated_or_player_placed");
                                break;
                            }
                        }
                }
            if (container == null) {
                Block block =
                        lootr
                                ? Registries.BLOCK.getIds().stream()
                                        .filter(
                                                id ->
                                                        id.getNamespace().equals("lootr")
                                                                && id.getPath()
                                                                        .equals("lootr_chest"))
                                        .findFirst()
                                        .map(Registries.BLOCK::get)
                                        .orElse(null)
                                : Blocks.CHEST;
                if (block == null) return;
                container =
                        new BlockPos(
                                MathHelper.floor(x) + 2,
                                world.getTopY(
                                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                        MathHelper.floor(x) + 2,
                                        MathHelper.floor(z)),
                                MathHelper.floor(z));
                while (!world.getBlockState(container).isAir()
                        && container.getY() < world.getTopY() - 1) container = container.up();
                if (!world.getBlockState(container).isAir()) {
                    container = null;
                    return;
                }
                fixture = container;
                fixtureOriginal = world.getBlockState(container);
                world.setBlockState(container, block.getDefaultState());
                BlockEntity be = world.getBlockEntity(container);
                if (be instanceof LootableContainerBlockEntity loot) {
                    loot.setLootTable(
                            new Identifier("minecraft", "chests/simple_dungeon"), world.getSeed());
                    if (!lootr) loot.checkLootInteraction(p);
                }
                if (!lootr
                        && be instanceof net.minecraft.inventory.Inventory inventory
                        && !ContainerSlots.hasItems(inventory))
                    inventory.setStack(0, new ItemStack(Items.IRON_INGOT, 16));
                result().put("containerSource", "explicit_fixture_in_actual_world");
            }
            result().put(
                            "containerBlock",
                            Registries.BLOCK
                                    .getId(world.getBlockState(container).getBlock())
                                    .toString());
            BlockPos stand = null;
            for (int dy = 0; dy <= 2 && stand == null; dy++)
                for (Direction direction : Direction.Type.HORIZONTAL) {
                    BlockPos candidate = container.offset(direction, 2).up(dy);
                    if (world.getBlockState(candidate).isAir()
                            && world.getBlockState(candidate.up()).isAir()) {
                        stand = candidate;
                        break;
                    }
                }
            if (stand == null)
                throw new IllegalStateException(
                        "No reachable air stand next to container " + container);
            x = stand.getX() + .5;
            z = stand.getZ() + .5;
            y = stand.getY();
            p.teleport(world, x, y, z, 180, 20);
            result().put("containerStand", Map.of("x", x, "y", y, "z", z));
            // Lootr's ordinary per-player use path runs in survival; flight/invulnerability are
            // temporary.
            p.changeGameMode(GameMode.SURVIVAL);
            BenchmarkAbilities.grant(p);
        }

        Vec3d findEntityObserver(Vec3d center, ServerPlayerEntity p) {
            double eyeHeight = p.getEyeY() - p.getY();
            Map<String, Object> audit = new LinkedHashMap<>();
            result().put("entityObserverAudit", audit);
            Map<String, Integer> blockers = new LinkedHashMap<>();
            audit.put("firstBlockerTypes", blockers);
            audit.put("sampledFootprintRadiusBlocks", 16);
            audit.put("spawnRingMaximumRadiusBlocks", 15);
            audit.put(
                    "selectionOrder",
                    "horizontal radius descending, then height descending; no mob AI or renderer changes");
            audit.put(
                    "policy",
                    "Native OUTLINE ray; only a non-colliding PlantBlock may be passed, and only when a second full native COLLIDER ray is clear. Solid blocks/leaves/unknown non-plants remain rejected. Actual renderer-ID and visual gates unchanged.");
            int candidates = 0, unloaded = 0, occupied = 0, blocked = 0, totalThin = 0;
            // Cover the entire 15-block spawn ring; source17 high-angle preference failed actual
            // coverage.
            for (int radius : new int[] {36, 28, 20, 12})
                for (int height : new int[] {28, 20, 14, 8, 5})
                    for (int direction = 0; direction < 8; direction++) {
                        candidates++;
                        audit.put("cameraCandidates", candidates);
                        double angle = direction * Math.PI / 4;
                        Vec3d camera =
                                center.add(
                                        Math.sin(angle) * radius,
                                        height,
                                        -Math.cos(angle) * radius);
                        Vec3d eye = camera.add(0, eyeHeight, 0);
                        boolean clear = true;
                        int selectedThin = 0;
                        for (Vec3d point : new Vec3d[] {camera, eye}) {
                            BlockPos block = BlockPos.ofFloored(point);
                            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                                audit.put("unloadedRejections", ++unloaded);
                                clear = false;
                                break;
                            }
                            if (!world.getFluidState(block).isEmpty()
                                    || !world.getBlockState(block)
                                            .getOutlineShape(world, block)
                                            .isEmpty()) {
                                audit.put("occupiedEyeRejections", ++occupied);
                                clear = false;
                                break;
                            }
                        }
                        if (!clear) continue;
                        for (int ox : new int[] {-16, -8, 0, 8, 16})
                            for (int oz : new int[] {-16, -8, 0, 8, 16}) {
                                int tx = MathHelper.floor(center.x + ox),
                                        tz = MathHelper.floor(center.z + oz);
                                if (!world.isChunkLoaded(tx >> 4, tz >> 4)) {
                                    audit.put("unloadedRejections", ++unloaded);
                                    clear = false;
                                    break;
                                }
                                double feet =
                                        world.getTopY(
                                                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tx, tz);
                                Vec3d target = new Vec3d(center.x + ox, feet + 1.2, center.z + oz);
                                var hit =
                                        world.raycast(
                                                new net.minecraft.world.RaycastContext(
                                                        eye,
                                                        target,
                                                        net.minecraft.world.RaycastContext.ShapeType
                                                                .OUTLINE,
                                                        net.minecraft.world.RaycastContext
                                                                .FluidHandling.NONE,
                                                        p));
                                if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
                                    BlockPos at = hit.getBlockPos();
                                    BlockState state = world.getBlockState(at);
                                    String type =
                                            Registries.BLOCK.getId(state.getBlock()).toString();
                                    if (blockers.size() < 8 || blockers.containsKey(type))
                                        blockers.merge(type, 1, Integer::sum);
                                    if (ThinPlantOcclusion.eligible(state, world, at)) {
                                        var solid =
                                                world.raycast(
                                                        new net.minecraft.world.RaycastContext(
                                                                eye,
                                                                target,
                                                                net.minecraft.world.RaycastContext
                                                                        .ShapeType.COLLIDER,
                                                                net.minecraft.world.RaycastContext
                                                                        .FluidHandling.NONE,
                                                                p));
                                        if (solid.getType()
                                                == net.minecraft.util.hit.HitResult.Type.MISS) {
                                            selectedThin++;
                                            audit.put("nonCollidingPlantRays", ++totalThin);
                                            continue;
                                        }
                                    }
                                    audit.put("blockingRayRejections", ++blocked);
                                    clear = false;
                                    break;
                                }
                            }
                        if (clear) {
                            audit.put("selected", true);
                            audit.put("selectedCameraThinPlantRays", selectedThin);
                            audit.put("originalStrictOutlineWouldPass", selectedThin == 0);
                            return camera;
                        }
                    }
            audit.put("selected", false);
            return null;
        }

        /** Loaded terrain only; reject shoreline/cliff scenes instead of silently measuring hidden underwater crowds. */
        BlockPos findEntitySurface(int centerX, int centerZ) {
            for (int radius = 0; radius <= 128; radius += 4)
                for (int dx = -radius; dx <= radius; dx += 4)
                    for (int dz = -radius; dz <= radius; dz += 4) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius)
                            continue;
                        int bx = centerX + dx,
                                bz = centerZ + dz,
                                min = Integer.MAX_VALUE,
                                max = Integer.MIN_VALUE;
                        boolean safe = true;
                        for (int ox = -16; ox <= 16 && safe; ox += 16)
                            for (int oz = -16; oz <= 16; oz += 16) {
                                int sx = bx + ox, sz = bz + oz;
                                if (!(world.getChunkManager()
                                                .getChunk(sx >> 4, sz >> 4, ChunkStatus.FULL, false)
                                        instanceof WorldChunk)) {
                                    safe = false;
                                    break;
                                }
                                int top =
                                        world.getTopY(
                                                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                                BlockPos ground = new BlockPos(sx, top - 1, sz);
                                if (!world.getFluidState(ground).isEmpty()
                                        || world.getBlockState(ground).isAir()
                                        || !world.getFluidState(ground.up()).isEmpty()
                                        || !world.getFluidState(ground.up(2)).isEmpty()
                                        || !world.getBlockState(ground.up())
                                                .getCollisionShape(world, ground.up())
                                                .isEmpty()
                                        || !world.getBlockState(ground.up(2))
                                                .getCollisionShape(world, ground.up(2))
                                                .isEmpty()) {
                                    safe = false;
                                    break;
                                }
                                min = Math.min(min, top);
                                max = Math.max(max, top);
                            }
                        if (safe && max - min <= 6 && naturalDryBuffer(bx, bz)) {
                            result().put("nativeDryBufferRadiusBlocks", 16);
                            result().put(
                                            "terrainGateRevision",
                                            "Exact native spawn footprint, not an arbitrary97x97 surrounding buffer. Native movement is validated every action tick; no timed replacements and no relaxed render threshold.");
                            result().put("denseNativeSpawnFootprintRadiusBlocks", 16);
                            return new BlockPos(
                                    bx,
                                    world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz),
                                    bz);
                        }
                    }
            return null;
        }

        boolean naturalDryBuffer(int bx, int bz) {
            // The native spawn ring reaches15blocks. Check every column in its33x33 footprint.
            // A97x97 dry prerequisite excluded legitimate scenes and could not guarantee where
            // active AI would travel. Exact action counts/render verification remain authoritative.
            for (int cx = (bx - 16) >> 4; cx <= (bx + 16) >> 4; cx++)
                for (int cz = (bz - 16) >> 4; cz <= (bz + 16) >> 4; cz++)
                    if (!(world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false)
                            instanceof WorldChunk)) return false;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int ox = -16; ox <= 16; ox++)
                for (int oz = -16; oz <= 16; oz++) {
                    int x = bx + ox, z = bz + oz;
                    if (!dryNativeColumn(x, z)) return false;
                    int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                            || !world.getBlockState(feet.up())
                                    .getCollisionShape(world, feet.up())
                                    .isEmpty()) return false;
                    min = Math.min(min, y);
                    max = Math.max(max, y);
                    if (max - min > 6) return false;
                }
            return true;
        }

        boolean dryNativeColumn(int x, int z) {
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            return !world.getBlockState(ground).isAir()
                    && world.getFluidState(ground).isEmpty()
                    && world.getFluidState(ground.up()).isEmpty()
                    && world.getFluidState(ground.up(2)).isEmpty();
        }

        BlockPos findNativeRainObserver(int centerX, int centerZ) {
            for (int radius = 0; radius <= 128; radius += 8)
                for (int dx = -radius; dx <= radius; dx += 8)
                    for (int dz = -radius; dz <= radius; dz += 8) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius)
                            continue;
                        int bx = centerX + dx, bz = centerZ + dz;
                        if (!(world.getChunkManager()
                                        .getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false)
                                instanceof WorldChunk)) continue;
                        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
                        BlockPos eye = new BlockPos(bx, top + 10, bz);
                        if (eye.getY() >= world.getTopY() - 8
                                || !world.isSkyVisible(eye)
                                || world.getBiome(eye).value().getPrecipitation(eye)
                                        != net.minecraft.world.biome.Biome.Precipitation.RAIN)
                            continue;
                        return eye;
                    }
            return null;
        }

        BlockPos findDrySurface(int centerX, int centerZ, int radius) {
            for (int r = 0; r <= radius; r += 4)
                for (int dx = -r; dx <= r; dx += 4)
                    for (int dz = -r; dz <= r; dz += 4) {
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        int bx = centerX + dx, bz = centerZ + dz;
                        Chunk chunk =
                                world.getChunkManager()
                                        .getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false);
                        if (!(chunk instanceof WorldChunk)) continue;
                        int by = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                        BlockPos ground = new BlockPos(bx, by - 1, bz);
                        if (world.getFluidState(ground).isEmpty()
                                && !world.getBlockState(ground).isAir()
                                && world.getBlockState(ground.up()).isAir()
                                && world.getBlockState(ground.up(2)).isAir()) return ground.up();
                    }
            return null;
        }

        void fillInventory(ServerPlayerEntity p) {
            p.changeGameMode(GameMode.SURVIVAL);
            BenchmarkAbilities.grant(p);
            List<Item> items = new ArrayList<>();
            Set<String> namespaces = new HashSet<>();
            for (Identifier id :
                    Registries.ITEM.getIds().stream()
                            .sorted(Comparator.comparing(Identifier::toString))
                            .toList())
                if (!id.getNamespace().equals("minecraft") && namespaces.add(id.getNamespace()))
                    items.add(Registries.ITEM.get(id));
            for (Item item :
                    List.of(
                            Items.DIAMOND_SWORD,
                            Items.DIAMOND_PICKAXE,
                            Items.IRON_INGOT,
                            Items.OAK_LOG,
                            Items.REDSTONE,
                            Items.BOW,
                            Items.BOOK,
                            Items.GOLD_INGOT)) items.add(item);
            if (items.isEmpty()) items.add(Items.STONE);
            for (int i = 0; i < 36; i++) {
                ItemStack stack = new ItemStack(items.get(i % items.size()));
                stack.setCustomName(Text.literal("Smooth Fix benchmark item " + i));
                p.getInventory().setStack(i, stack);
            }
            p.getInventory().markDirty();
            p.playerScreenHandler.sendContentUpdates();
            result().put("inventoryFixtureSlots", 36);
        }

        void spawn(EntityType<?> type, int count) {
            for (int i = 0; i < count; i++) {
                Entity entity = type.create(world);
                if (!(entity instanceof MobEntity mob))
                    throw new IllegalArgumentException("Entity scenario requires a mob: " + type);
                double angle = i * 2.39996323,
                        ex = x + Math.cos(angle) * (8 + i % 8),
                        ez = z + Math.sin(angle) * (8 + i % 8);
                world.getChunk(MathHelper.floor(ex) >> 4, MathHelper.floor(ez) >> 4);
                int ey =
                        world.getTopY(
                                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                MathHelper.floor(ex),
                                MathHelper.floor(ez));
                mob.refreshPositionAndAngles(ex, ey, ez, 0, 0);
                mob.initialize(
                        world,
                        world.getLocalDifficulty(mob.getBlockPos()),
                        SpawnReason.COMMAND,
                        null,
                        null);
                mob.setPersistent();
                mob.addCommandTag(tag());
                if (mob instanceof WitherEntity wither) wither.onSummoned();
                actors.add(mob);
                if (!world.spawnEntity(mob)) throw new IllegalStateException("Spawn rejected");
            }
        }

        void tick() throws Exception {
            ServerPlayerEntity p = player();
            long now = System.nanoTime();
            if (p == null) {
                finish("owner_missing");
                return;
            }
            if (server.getPlayerManager().getPlayerList().size() != 1) {
                finish("another_player_joined");
                return;
            }
            if (!p.isAlive()) {
                if (((Number) result().getOrDefault("playerDeathsRecovered", 0)).intValue() >= 3) {
                    complete("failed_repeated_player_deaths");
                    p = BenchmarkRespawn.replace(p);
                    p.teleport(world, x, y, z, 0, 20);
                    next();
                    return;
                }
                send(7);
                BenchmarkAbilities.revoke(p);
                p = BenchmarkRespawn.replace(p);
                if (continuationInventory != null) {
                    p.getInventory().clear();
                    p.getInventory().readNbt(continuationInventory);
                    p.getInventory().selectedSlot = continuationSelected;
                }
                result().put(
                                "playerDeathsRecovered",
                                ((Number) result().getOrDefault("playerDeathsRecovered", 0))
                                                .intValue()
                                        + 1);
                resumePlayer(p, "Player died: automatic respawn; resuming the workload");
                return;
            }
            if (continuing) {
                if (readyAt == 0) {
                    if (now - preparedAt > 120_000_000_000L) {
                        complete("failed_recovery_loading_timeout");
                        next();
                    }
                    return;
                }
                continuing = false;
            }
            if (p.getServerWorld() != world) {
                finish("owner_left_dimension");
                return;
            }
            if (AdvancedStageInfo.walking(stage.mode) && structureSearch == null && readyAt != 0) {
                if (safePosition != null
                        && p.getY() < safePosition.y - 6
                        && p.getVelocity().y < -.4) {
                    result().put(
                                    "fallRescues",
                                    ((Number) result().getOrDefault("fallRescues", 0)).intValue()
                                            + 1);
                    send(7);
                    resumePlayer(
                            p, "Ground lost: returning to the last safe position and resuming");
                    return;
                }
                if (p.isOnGround()
                        && world.isChunkLoaded(p.getBlockX() >> 4, p.getBlockZ() >> 4)
                        && new GroundCollisionTerrain(world, p)
                                        .step(
                                                new GroundRoutePlanner.Point(
                                                        p.getBlockX(), p.getY(), p.getBlockZ()),
                                                0,
                                                0)
                                != null) safePosition = p.getPos();
            }
            captureContinuation(p);
            var heap =
                    java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            if (heap.getMax() > 0 && heap.getUsed() > heap.getMax() * 0.95) {
                if (pressureAt == 0) pressureAt = now;
                else if (now - pressureAt > 10_000_000_000L) {
                    finish("sustained_heap_pressure");
                    return;
                }
            } else pressureAt = 0;
            if (waitingEntityTerrain) {
                if (now - lastEntityTerrainRetry >= 1_000_000_000L) prepareStage(p);
                return;
            }
            if (entityReadiness != null) {
                tickEntityPreparation(p, now);
                return;
            }
            if (structureSearch != null) {
                if (optionalStructures.contains(stage)
                        && now - preparationAt
                                >= plan.optionalModStructureSearchSeconds * 1_000_000_000L) {
                    result().put(
                                    "optionalSearchBudgetSeconds",
                                    plan.optionalModStructureSearchSeconds);
                    skip("skipped_optional_structure_search_budget");
                    return;
                }
                if (now - heartbeat > 30_000_000_000L) {
                    finish("client_heartbeat_timeout_during_structure_search");
                    return;
                }
                structureSearch.tick();
                result().put("structureSearch", structureSearch.progress());
                if (structureSearch.unavailable() != null) {
                    skip(structureSearch.unavailable());
                    return;
                }
                var located = structureSearch.found();
                if (located != null) {
                    lastStructure = located.position();
                    x = lastStructure.getX();
                    z = lastStructure.getZ();
                    result().put(
                                    "locatedStructure",
                                    located.structure()
                                            .getKey()
                                            .orElseThrow()
                                            .getValue()
                                            .toString());
                    result().put("structureLocateMs", (System.nanoTime() - preparationAt) / 1e6);
                    structureSearch.close();
                    structureSearch = null;
                    readyAt = 0;
                    prepareStage(p);
                    return;
                }
                if (now - searchProgressAt >= 2_000_000_000L) {
                    searchProgressAt = now;
                    send(6);
                    checkpoint();
                }
                return;
            }
            if (readyAt == 0) {
                if (now - preparedAt > 120_000_000_000L) finish("client_loading_timeout");
                return;
            }
            if (now - heartbeat > 30_000_000_000L) {
                finish("client_heartbeat_timeout");
                return;
            }
            if (stage.mode.equals("entities") || stage.mode.equals("wither")) {
                EntityType<?> requested = Registries.ENTITY_TYPE.get(new Identifier(stage.target));
                int count = 0;
                for (Entity actor : actors)
                    if (actor.isAlive()
                            && !actor.isRemoved()
                            && actor.getType() == requested
                            && world.isChunkLoaded(actor.getBlockX() >> 4, actor.getBlockZ() >> 4))
                        count++;
                nativeActionSamples++;
                minimumNativeActionAlive = Math.min(minimumNativeActionAlive, count);
                maximumNativeActionAlive = Math.max(maximumNativeActionAlive, count);
                if (count != stage.count) {
                    complete("failed_native_entity_count_changed_during_action");
                    next();
                    return;
                }
            }
            ticks++;
            if (stage.mode.equals("inventory")
                    && !ItemStack.areEqual(p.getInventory().getStack(0), lastHotbar)) {
                lastHotbar = p.getInventory().getStack(0).copy();
                ChunkMetrics.count(world, "inventoryHotbarChanges", 1);
            }
            if (Set.of("chest", "lootr").contains(stage.mode)) {
                int items = inventoryItems(p);
                if (items > lastInventoryItems)
                    ChunkMetrics.count(world, "lootItemsReceived", items - lastInventoryItems);
                lastInventoryItems = items;
            }
            if (p.currentScreenHandler != p.playerScreenHandler
                    && p.currentScreenHandler.syncId != lastContainerId) {
                lastContainerId = p.currentScreenHandler.syncId;
                ChunkMetrics.count(world, "containerSessionsOpened", 1);
            }
            if (ticks % 20 == 0) {
                if (Set.of("combat", "effects", "bloodmoon").contains(stage.mode)) {
                    int alive =
                            (int)
                                    actors.stream()
                                            .filter(
                                                    e ->
                                                            e.getType()
                                                                            .equals(
                                                                                    Registries
                                                                                            .ENTITY_TYPE
                                                                                            .get(
                                                                                                    new Identifier(
                                                                                                            stage.target)))
                                                                    && e.isAlive())
                                            .count();
                    if (alive < stage.count)
                        spawn(
                                Registries.ENTITY_TYPE.get(new Identifier(stage.target)),
                                Math.min(8, stage.count - alive));
                    actors.removeIf(Entity::isRemoved);
                    send(3);
                }

                int owned = 0;
                for (Entity entity : world.iterateEntities())
                    if (entity.getCommandTags().contains(tag())) owned++;
                if (owned > 384) {
                    finish("test_entity_limit_384");
                    return;
                }
                result().put(
                                "peakOwnedEntities",
                                Math.max(
                                        owned,
                                        ((Number) result().getOrDefault("peakOwnedEntities", 0))
                                                .intValue()));
                if (stage.mode.equals("effects"))
                    for (Entity entity : actors)
                        if (entity instanceof MobEntity mob && mob.isAlive()) {
                            mob.addStatusEffect(
                                    new net.minecraft.entity.effect.StatusEffectInstance(
                                            net.minecraft.entity.effect.StatusEffects.SPEED, 80));
                            mob.addStatusEffect(
                                    new net.minecraft.entity.effect.StatusEffectInstance(
                                            net.minecraft.entity.effect.StatusEffects.REGENERATION,
                                            80));
                            ChunkMetrics.count(world, "benchmarkEffectApplications", 2);
                        }
                if (stage.mode.equals("bloodmoon")) {
                    boolean moon = BloodMoonAdapter.isActiveInWorld(world);
                    result().put(
                                    "bloodMoonActiveObserved",
                                    Boolean.TRUE.equals(result().get("bloodMoonActiveObserved"))
                                            || moon);
                }
                if (stage.mode.equals("tnt") && ticks <= stage.count * 20) {
                    BlockPos site = tntSites.get(ticks / 20 - 1);
                    TntEntity tnt =
                            new TntEntity(
                                    world, site.getX() + .5, site.getY(), site.getZ() + .5, p);
                    tnt.setFuse(40);
                    tnt.addCommandTag(tag());
                    actors.add(tnt);
                    world.spawnEntity(tnt);
                }
                if (stage.mode.equals("teleport") && ticks % 100 == 0) {
                    p.teleport(world, x + (ticks % 200 == 0 ? 0 : 384), y, z, 0, 20);
                    ChunkMetrics.count(world, "nativeTeleports", 1);
                    send(3);
                }
            }
            if (stage.mode.equals("save") && ticks == 40) {
                long at = System.nanoTime();
                server.save(false, true, true);
                result().put("explicitWorldSaveMs", (System.nanoTime() - at) / 1e6);
                ChunkMetrics.count(world, "explicitWorldSaves", 1);
            }
            result().put("status", "measuring");
            if (now - readyAt >= stage.seconds * 1_000_000_000L) {
                complete("measured");
                next();
            }
        }

        void captureContinuation(ServerPlayerEntity p) {
            if (!p.isAlive()) return;
            continuationSelected = p.getInventory().selectedSlot;
            boolean changed =
                    continuationStacks == null
                            || continuationStacks.size() != p.getInventory().size();
            if (!changed)
                for (int slot = 0; slot < continuationStacks.size(); slot++)
                    if (!ItemStack.areEqual(
                            continuationStacks.get(slot), p.getInventory().getStack(slot))) {
                        changed = true;
                        break;
                    }
            if (!changed) return;
            continuationStacks = new ArrayList<>(p.getInventory().size());
            for (int slot = 0; slot < p.getInventory().size(); slot++)
                continuationStacks.add(p.getInventory().getStack(slot).copy());
            continuationInventory = p.getInventory().writeNbt(new NbtList());
        }

        void resumePlayer(ServerPlayerEntity p, String message) {
            continuationAt = readyAt == 0 ? 0 : System.nanoTime() - readyAt;
            readyAt = 0;
            continuing = true;
            preparedAt = heartbeat = System.nanoTime();
            Vec3d anchor = safePosition == null ? new Vec3d(x, y, z) : safePosition;
            x = anchor.x;
            y = anchor.y;
            z = anchor.z;
            p.closeHandledScreen();
            p.changeGameMode(GameMode.SURVIVAL);
            p.teleport(world, x, y, z, 0, 20);
            p.setVelocity(Vec3d.ZERO);
            p.fallDistance = 0;
            p.setHealth(p.getMaxHealth());
            p.setAir(p.getMaxAir());
            BenchmarkAbilities.grant(p, !AdvancedStageInfo.walking(stage.mode));
            p.getInventory().markDirty();
            p.playerScreenHandler.syncState();
            p.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(
                            p.getInventory().selectedSlot));
            p.sendMessage(
                    Text.literal(
                            "[Smooth Fix Advanced] "
                                    + message
                                    + "; the recovery event remains recorded."),
                    false);
            send(8);
            checkpoint();
        }

        void skip(String reason) {
            complete(reason);
            try {
                next();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        void complete(String status) {
            if (stage.mode.equals("entities") || stage.mode.equals("wither"))
                result().put(
                                "nativeEntityActionCounts",
                                Map.of(
                                        "samples",
                                        nativeActionSamples,
                                        "minimumAliveLoadedRequestedType",
                                        nativeActionSamples == 0 ? 0 : minimumNativeActionAlive,
                                        "maximumAliveLoadedRequestedType",
                                        maximumNativeActionAlive,
                                        "expected",
                                        stage.count,
                                        "policy",
                                        "Every native server action tick; no in-action replacements. Death, unload or type conversion invalidates this controlled crowd stage."));
            result().put("status", status);
            result().put("elapsedSeconds", (System.nanoTime() - phaseAt) / 1e9);
            result().put(
                            "actionWindowSeconds",
                            readyAt == 0 ? 0 : (System.nanoTime() - readyAt) / 1e9);
            result().put("worldAtEnd", snapshot());
            if (profiling) {
                result().put("server", ServerDiagnostics.finishRecording());
                profiling = false;
            }
            if (chunks != null) {
                result().put("chunkAndMechanicMetrics", chunks.finish());
                chunks = null;
            }
            result().put(
                            "serverWorkloadValidation",
                            status.startsWith("skipped") ? "not_exercised_skipped" : validate());
            result().put("measurementStatus", status);
            ServerPlayerEntity notified = player();
            if (notified != null)
                notified.sendMessage(
                        Text.literal(
                                "[Smooth Fix Advanced "
                                        + (phase + 1)
                                        + "/"
                                        + stages.size()
                                        + "] "
                                        + stage.name
                                        + " — "
                                        + status
                                        + "; server validation: "
                                        + result().get("serverWorkloadValidation")),
                        false);
            if (status.equals("measured")) result().put("status", "measured_pending_client_report");
            send(2);
            classify(result());
            checkpoint();
        }

        String validate() {
            if (stage.mode.equals("entities"))
                return nativeActionSamples > 0
                                && minimumNativeActionAlive == stage.count
                                && maximumNativeActionAlive == stage.count
                        ? "requested_native_entities_observed"
                        : "requested_entity_count_not_observed";
            if (stage.mode.equals("bloodmoon"))
                return Boolean.TRUE.equals(result().get("bloodMoonActiveObserved"))
                        ? "event_active_observed"
                        : "event_not_observed_active";
            Map<?, ?> metrics = (Map<?, ?>) result().get("chunkAndMechanicMetrics");
            Map<?, ?> counts =
                    metrics == null ? Map.of() : (Map<?, ?>) metrics.get("mechanicCounters");
            if (stage.mode.equals("cold_route")) {
                Map<?, ?> unique =
                        metrics == null ? Map.of() : (Map<?, ?>) metrics.get("uniqueChunksByHook");
                return unique.get("featureGeneration") instanceof Number n && n.intValue() > 0
                        ? "feature_generation_observed"
                        : "not_validated_as_new_chunks";
            }
            if (Set.of("chest", "lootr").contains(stage.mode))
                return counts.containsKey("lootItemsReceived")
                        ? "native_open_and_loot_received"
                        : counts.containsKey("containerSessionsOpened")
                                ? "container_opened_no_loot_received"
                                : "container_not_opened";
            if (stage.mode.equals("structure")) {
                Object located = result().get("locatedStructure");
                Map<?, ?> context = (Map<?, ?>) result().get("worldAtEnd");
                Map<?, ?> starts =
                        context == null
                                ? null
                                : (Map<?, ?>) context.get("nearbyLoadedStructureStarts");
                return located != null && starts != null && starts.containsKey(located)
                        ? "structure_start_loaded"
                        : "located_only_structure_not_loaded";
            }
            if (stage.mode.equals("combat"))
                return counts.containsKey("playerHitsAccepted")
                        ? "player_damage_accepted"
                        : "no_player_damage_observed";
            if (stage.mode.equals("tnt"))
                return counts.containsKey("explosions")
                                && counts.containsKey("explosionDestroyedBlocks")
                        ? "native_explosion_and_terrain_destruction_observed"
                        : "explosion_or_terrain_destruction_not_observed";
            if (stage.mode.equals("wither")) {
                if (nativeActionSamples == 0
                        || minimumNativeActionAlive != stage.count
                        || maximumNativeActionAlive != stage.count)
                    return "requested_entity_count_not_observed";
                return counts.containsKey("explosions")
                        ? "native_explosions_observed"
                        : "explosions_not_observed";
            }
            if (stage.mode.equals("break"))
                return counts.containsKey("playerBlocksBroken")
                        ? "native_block_break_observed"
                        : "no_block_break_observed";
            if (stage.mode.equals("inventory"))
                return counts.containsKey("inventoryHotbarChanges")
                        ? "inventory_changes_observed"
                        : "no_inventory_changes_observed";
            if (stage.mode.equals("effects"))
                return counts.containsKey("benchmarkEffectApplications")
                        ? "native_status_effects_observed"
                        : "status_effects_not_observed";
            if (stage.mode.equals("save"))
                return counts.containsKey("explicitWorldSaves")
                        ? "native_world_save_observed"
                        : "world_save_not_observed";
            if (stage.mode.equals("teleport"))
                return counts.containsKey("nativeTeleports")
                        ? "native_teleports_observed"
                        : "teleports_not_observed";
            if (stage.mode.equals("weather"))
                return world.isRaining() ? "native_rain_observed" : "rain_not_observed";
            return "see_stage_context_and_client_actions";
        }

        void classify(Map<String, Object> result) {
            String status = String.valueOf(result.get("status"));
            if (status.startsWith("skipped") || !"measured".equals(result.get("measurementStatus")))
                return;
            if (!(result.get("client") instanceof JsonObject client)) {
                result.put("status", "measured_pending_client_report");
                return;
            }
            String serverValidation = String.valueOf(result.get("serverWorkloadValidation"));
            boolean serverPassed =
                    !Set.of(
                                    "requested_entity_count_not_observed",
                                    "not_validated_as_new_chunks",
                                    "container_opened_no_loot_received",
                                    "container_not_opened",
                                    "located_only_structure_not_loaded",
                                    "no_player_damage_observed",
                                    "explosions_not_observed",
                                    "explosion_or_terrain_destruction_not_observed",
                                    "no_block_break_observed",
                                    "no_inventory_changes_observed",
                                    "event_not_observed_active",
                                    "status_effects_not_observed",
                                    "world_save_not_observed",
                                    "teleports_not_observed",
                                    "rain_not_observed")
                            .contains(serverValidation);
            boolean clientPassed =
                    client.has("workloadValidated")
                            && client.get("workloadValidated").getAsBoolean();
            result.put(
                    "status",
                    result.containsKey("playerDeathsRecovered")
                            ? "failed_recovered_player_death"
                            : result.containsKey("fallRescues")
                                    ? "failed_recovered_fall"
                                    : !serverPassed
                                            ? "failed_server_workload_verification"
                                            : !clientPassed
                                                    ? "failed_client_workload_verification"
                                                    : "passed");
        }

        void refreshOutcome() {
            report.put("summary", BenchmarkOutcome.counts(results));
            if ("completed".equals(terminationReason))
                report.put("status", BenchmarkOutcome.status(results));
        }

        Map<String, Object> snapshot() {
            Map<String, Integer> entities = new TreeMap<>(),
                    blockEntities = new TreeMap<>(),
                    structures = new TreeMap<>();
            for (Entity entity : world.iterateEntities())
                entities.merge(
                        Registries.ENTITY_TYPE.getId(entity.getType()).toString(), 1, Integer::sum);
            int cx = MathHelper.floor(x) >> 4, cz = MathHelper.floor(z) >> 4;
            var registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    Chunk chunk =
                            world.getChunkManager()
                                    .getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
                    if (chunk instanceof WorldChunk full) {
                        for (BlockEntity be : full.getBlockEntities().values())
                            blockEntities.merge(
                                    String.valueOf(
                                            Registries.BLOCK_ENTITY_TYPE.getId(be.getType())),
                                    1,
                                    Integer::sum);
                        full.getStructureStarts()
                                .forEach(
                                        (type, start) -> {
                                            if (start.hasChildren())
                                                structures.merge(
                                                        String.valueOf(registry.getId(type)),
                                                        1,
                                                        Integer::sum);
                                        });
                    }
                }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dimension", world.getRegistryKey().getValue().toString());
            out.put("loadedChunks", world.getChunkManager().getLoadedChunkCount());
            out.put("entitiesByType", entities);
            out.put("nearbyLoadedBlockEntities", blockEntities);
            out.put("nearbyLoadedStructureStarts", structures);
            out.put("contextRadiusChunks", 4);
            out.put("timeOfDay", world.getTimeOfDay());
            out.put("raining", world.isRaining());
            ServerPlayerEntity p = player();
            if (p != null)
                out.put("playerPosition", Map.of("x", p.getX(), "y", p.getY(), "z", p.getZ()));
            return out;
        }

        int inventoryItems(ServerPlayerEntity p) {
            int count = 0;
            for (int i = 0; i < 36; i++) count += p.getInventory().getStack(i).getCount();
            return count;
        }

        void cleanup() {
            if (structureSearch != null) {
                result().put("structureSearch", structureSearch.progress());
                structureSearch.close();
                structureSearch = null;
            }
            ServerPlayerEntity p = player();
            if (p != null) {
                p.closeHandledScreen();
                BenchmarkAbilities.revoke(p);
            }
            for (ServerWorld w : server.getWorlds()) {
                List<Entity> owned = new ArrayList<>();
                for (Entity entity : w.iterateEntities())
                    for (String t : entity.getCommandTags())
                        if (t.startsWith(TAG + id + ":")) {
                            owned.add(entity);
                            break;
                        }
                for (Entity e : owned) e.discard();
            }
            actors.clear();
            tntSites.clear();
            if (fixture != null && world != null) {
                world.setBlockState(fixture, fixtureOriginal);
                fixture = null;
                fixtureOriginal = null;
            }
        }

        void checkpoint() {
            try {
                BenchmarkRecovery.atomic(file, GSON.toJson(report));
            } catch (Exception e) {
                throw new IllegalStateException("Checkpoint write failed", e);
            }
        }

        void finish(String reason) {
            finish(reason, player());
        }

        void finish(String reason, ServerPlayerEntity recovering) {
            if (ending) return;
            ending = true;
            try {
                if (phase >= 0 && phase < results.size() && profiling) complete(reason);
            } catch (Exception e) {
                SmoothFix.LOGGER.error("Could not finalize advanced stage", e);
            }
            try {
                send(0);
                cleanup();
            } catch (Exception e) {
                SmoothFix.LOGGER.error("Could not clean advanced actors", e);
            }
            if (profiling) {
                ServerDiagnostics.finishRecording();
                profiling = false;
            }
            if (chunks != null) {
                chunks.finish();
                chunks = null;
            }
            terminationReason = reason;
            report.put("terminationReason", reason);
            report.put("status", reason);
            refreshOutcome();
            report.put("elapsedSeconds", (System.nanoTime() - started) / 1e9);
            report.put(
                    "playerRestored", recovering != null && BenchmarkRecovery.restore(recovering));
            report.put("recoveryVerification", BenchmarkRecovery.lastVerification(owner));
            report.put("environmentRestored", AdvancedWorldRecovery.restore(server));
            BenchmarkFinalization.send(recovering, id, report, this::checkpoint);
            if (recovering != null)
                recovering.sendMessage(
                        Text.literal(
                                "[Smooth Fix Advanced] "
                                        + report.get("status")
                                        + ". Server recovery: "
                                        + report.get("playerRestored")
                                        + ". Report: "
                                        + file.getFileName()),
                        false);
            try {
                checkpoint();
                SmoothFix.LOGGER.info("Advanced benchmark {}: {}", reason, file);
            } catch (Exception e) {
                SmoothFix.LOGGER.error("Final advanced checkpoint failed", e);
            }
            completed = this;
            completedAt = System.nanoTime();
            active = null;
        }
    }
}
