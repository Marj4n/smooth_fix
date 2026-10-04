package org.marj4n.smooth_fix.benchmark;

import com.google.gson.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.diagnostics.*;
import java.util.*;
import java.nio.file.Path;

/** One operator-owned run, real gameplay actors, separate dimensions, bounded workloads and per-stage checkpoints. */
public final class ServerBenchmark {
    public static final RegistryKey<World> ARENA=RegistryKey.of(RegistryKeys.WORLD,new Identifier("smooth_fix","benchmark"));
    public static final RegistryKey<World> TERRAIN=RegistryKey.of(RegistryKeys.WORLD,new Identifier("smooth_fix","benchmark_terrain"));
    public static final RegistryKey<World> LUNAR=RegistryKey.of(RegistryKeys.WORLD,new Identifier("smooth_fix","benchmark_lunar"));
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static Run active,completed;
    private static final Set<Entity> staleActors = new LinkedHashSet<>();
    private static long completedAt;
    private ServerBenchmark() { }
    public static boolean isRunning(){return active!=null;}
    private static boolean reserved(ServerWorld world){var key=world.getRegistryKey();return key.equals(ARENA)||key.equals(TERRAIN)||key.equals(LUNAR);}
    public static boolean allowSpawn(ServerWorld world,Entity entity){
        if(!reserved(world) || entity instanceof net.minecraft.entity.player.PlayerEntity)return true;
        Run r=active;
        if(r==null || r.ending || r.world!=world){entity.discard();return false;}
        entity.addCommandTag("smoothfix_bench:"+r.id+":"+r.phase);return true;
    }
    private record Stage(String name,String mode,EntityType<?> type,int count) { }
    public static void install() {
        BenchmarkFinalization.install();
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(
            CommandManager.literal("smoothfix").requires(s->s.hasPermissionLevel(2))
                .then(CommandManager.literal("stress")
                    .then(CommandManager.literal("start").executes(c->start(c.getSource(),suite(),0)))
                    .then(CommandManager.literal("wither").executes(c->start(c.getSource(),List.of(new Stage("wither_10","combat",EntityType.WITHER,10)),0))
                        .then(CommandManager.argument("count",IntegerArgumentType.integer(1,10)).executes(c->{int n=IntegerArgumentType.getInteger(c,"count");return start(c.getSource(),List.of(new Stage("wither_"+n,"combat",EntityType.WITHER,n)),0);})))
                    .then(CommandManager.literal("soak").then(CommandManager.argument("minutes",IntegerArgumentType.integer(5,60)).executes(c->start(c.getSource(),suite(),IntegerArgumentType.getInteger(c,"minutes")))))
                    .then(CommandManager.literal("feature").then(CommandManager.argument("entity",net.minecraft.command.argument.IdentifierArgumentType.identifier())
                        .then(CommandManager.argument("count",IntegerArgumentType.integer(1,32)).executes(c->{
                            Identifier id=net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(c,"entity");
                            EntityType<?> type=Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
                            if(type==null){c.getSource().sendError(Text.literal("Unknown entity: "+id));return 0;}
                            int count=IntegerArgumentType.getInteger(c,"count");
                            if(type==EntityType.WITHER && count>10){c.getSource().sendError(Text.literal("Wither limit is 10."));return 0;}
                            return start(c.getSource(),List.of(new Stage("feature_"+id,"combat",type,count)),0);
                        }))))
                    .then(CommandManager.literal("list").executes(c->{c.getSource().sendFeedback(()->Text.literal("Suite: baseline, village 32, mobs 16/32/64 + golems, positive effects, Blood Moon, 10 Withers, teleport, terrain exploration. Wither: 1..10. Feature: registered mob ID, 1..32. Blood Moon uses Enhanced Celestials 2 in its own night arena when installed. Individual mod abilities are not automatically forced; profile them with /smoothfix profile and /smoothfixc profile."),false);return 1;}))
                    .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal(active==null?AdvancedBenchmark.status():active.status()),false);return 1;}))
                    .then(CommandManager.literal("stop").executes(c->{if(active!=null){active.finish("stopped_by_operator");return 1;}return AdvancedBenchmark.stop()?1:0;})))));
        ServerTickEvents.END_SERVER_TICK.register(server->{discardStaleActors();if(completed!=null && System.nanoTime()-completedAt>60_000_000_000L)completed=null;Run r=active;if(r!=null)try{r.tick();}catch(Exception e){SmoothFix.LOGGER.error("Benchmark stage failed",e);r.finish("failed: "+e.getClass().getSimpleName()+": "+e.getMessage());}});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(!reserved(world) || entity instanceof net.minecraft.entity.player.PlayerEntity)return;
            Run r=active;
            // Loading may iterate a chunk's entity list while saving/unloading it.
            // Discarding inside this callback mutates that list and breaks the save.
            if(r==null || r.ending || r.world!=world || !entity.getCommandTags().contains("smoothfix_bench:"+r.id+":"+r.phase))staleActors.add(entity);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->staleActors.remove(entity));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(active!=null)active.finish("server_stopping");discardStaleActors();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{staleActors.clear();completed=null;});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->BenchmarkRecovery.restore(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{if(active!=null && active.owner.equals(handler.player.getUuid()))active.finish("owner_disconnected",handler.player);BenchmarkRecovery.restore(handler.player);});
        ServerPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.RESPONSE,(server,player,handler,buf,sender)->{
            UUID id=buf.readUuid();int phase=buf.readInt();int action=buf.readUnsignedByte();String data=buf.readString(24000);
            server.execute(()->acceptResponse(player,id,phase,action,data));
        });
    }
    private static void acceptResponse(ServerPlayerEntity player,UUID id,int phase,int action,String data) {
        Run r=active!=null && active.id.equals(id)?active:completed;
        if(r==null || !r.id.equals(id) || !r.owner.equals(player.getUuid()))return;
        // An intentional user stop applies to the run; automatic aborts belong only to their stage.
        if(action==4 && r==active){r.finish("client_stopped: "+data);return;}
        if(action==0 && r==active){
            if(phase==r.phase)r.finish("client_stopped: "+data);
            else SmoothFix.LOGGER.debug("Ignored benchmark abort from old phase {} (current {})",phase,r.phase);
            return;
        }
        if(action==1 && r==active && phase==r.phase && r.readyAt==0){r.readyAt=System.nanoTime();if(r.continuing){if(r.measuring)r.measurementAt=r.readyAt-r.continuationElapsed;else r.readyAt-=r.continuationElapsed;r.continuing=false;}r.heartbeat=System.nanoTime();}
        if(action==2 && phase>=0 && phase<r.results.size()) {
            try{JsonElement json=JsonParser.parseString(data);if(json.isJsonObject()){r.results.get(phase).put("clientSummary",json);r.checkpoint();}}
            catch(Exception e){SmoothFix.LOGGER.warn("Rejected malformed benchmark summary",e);}
        }
        if(action==3 && r==active && phase==r.phase)r.heartbeat=System.nanoTime();
    }
    private static List<Stage> suite(){return List.of(new Stage("baseline","orbit",null,0),new Stage("village_32","orbit",EntityType.VILLAGER,32),new Stage("mobs_16","combat",EntityType.HUSK,16),new Stage("mobs_32","combat",EntityType.HUSK,32),new Stage("mobs_64","combat",EntityType.HUSK,64),new Stage("positive_effects_64","effects",EntityType.HUSK,64),new Stage("blood_moon","bloodmoon",EntityType.HUSK,64),new Stage("wither_10","combat",EntityType.WITHER,10),new Stage("teleport","teleport",null,0),new Stage("exploration","explore",null,0));}
    private static int start(ServerCommandSource source,List<Stage> stages,int soakMinutes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player=source.getPlayerOrThrow();MinecraftServer server=source.getServer();
        if(active!=null || AdvancedBenchmark.isRunning() || ServerDiagnostics.isProfiling()){source.sendError(Text.literal("Stop the active benchmark/profile first."));return 0;}
        if(!ServerPlayNetworking.canSend(player,BenchmarkProtocol.CONTROL)){source.sendError(Text.literal("Install this Smooth Fix build with diagnostics enabled on the client too."));return 0;}
        if(player.getWorld().getRegistryKey().equals(ARENA)||player.getWorld().getRegistryKey().equals(TERRAIN)||player.getWorld().getRegistryKey().equals(LUNAR)){source.sendError(Text.literal("Return to a normal dimension first."));return 0;}
        ServerWorld arena=server.getWorld(ARENA),terrain=server.getWorld(TERRAIN),lunar=server.getWorld(LUNAR);
        if(arena==null||terrain==null||lunar==null){source.sendError(Text.literal("Benchmark dimensions missing. Restart with the Smooth Fix built-in data pack enabled."));return 0;}
        if(!arena.getPlayers().isEmpty() || !terrain.getPlayers().isEmpty() || !lunar.getPlayers().isEmpty()){source.sendError(Text.literal("Benchmark dimensions must have no other players."));return 0;}
        try {
            // Validate optional feature BEFORE journaling/moving the player.
            for(Stage stage:stages)if(stage.type!=null){Entity probe=stage.type.create(arena);if(!(probe instanceof MobEntity)){if(probe!=null)probe.discard();source.sendError(Text.literal("Feature tests accept mob entities only."));return 0;}probe.discard();}
            if(!BenchmarkRecovery.restore(player)){source.sendError(Text.literal("Resolve the existing recovery journal first; see server log."));return 0;}
            BenchmarkRecovery.save(player);
            Run run=new Run(server,player,stages,soakMinutes);active=run;
            run.checkpoint();player.changeGameMode(GameMode.CREATIVE);run.next();
            source.sendFeedback(()->Text.literal("Smooth Fix automatic benchmark started. 10s warm-up + 60s measurement per stage. Keep Minecraft focused. /smoothfix stress stop ends and restores location/mode. Benchmark dimensions are reserved test arenas."),false);return 1;
        }catch(Exception e){SmoothFix.LOGGER.error("Cannot start benchmark",e);if(active!=null)active.finish("start_failed");else BenchmarkRecovery.restore(player);source.sendError(Text.literal("Benchmark could not start; see server log."));return 0;}
    }
    private static final class Run {
        final UUID id=UUID.randomUUID(),owner;
        final MinecraftServer server;
        final List<Stage> stages;
        final List<Map<String,Object>> results=new ArrayList<>();
        final Map<String,Object> report=new LinkedHashMap<>();
        final Path checkpoint;
        final long started=System.nanoTime(),soakNanos;
        int phase=-1,cycle,localTick;
        long stageStarted,readyAt,heartbeat,measurementAt,lastTeleport,pressureAt;
        boolean continuing;long continuationElapsed;net.minecraft.nbt.NbtList continuationInventory;int continuationSelected;
        boolean measuring,ending;
        ServerWorld world;
        Stage stage;
        final List<MobEntity> actors=new ArrayList<>();
        double centerX,centerY,centerZ;
        Run(MinecraftServer server,ServerPlayerEntity player,List<Stage> stages,int soakMinutes){
            this.server=server;this.owner=player.getUuid();this.stages=stages;this.soakNanos=soakMinutes*60_000_000_000L;
            checkpoint=server.getSavePath(WorldSavePath.ROOT).resolve("smooth_fix/benchmarks/"+id+".json");
            report.put("runId",id.toString());report.put("build",SmoothFix.BUILD_ID);report.put("status","running");report.put("memoryAtStart",MemoryReport.snapshot("server"));report.put("stages",results);
            report.put("representativeness",Map.of(
                    "suite", "Controlled entity regression workload, not whole-modpack gameplay certification",
                    "flatStages", "Baseline, villagers, combat, effects, Blood Moon, Withers and teleport use flat reserved arenas. Withers cannot destroy the bedrock floor; villagers have workstations, not a generated village.",
                    "exploration", "Overworld noise generator in a custom dimension, scripted flight at y=180, 8 blocks/second. Repeated runs reuse chunks; overworld-only dimension tags/mod hooks may exclude structures or features.",
                    "unmeasured", "Actual overworld structure coverage, generated-vs-loaded chunk counts, chest/Lootr interactions, inventory open/search latency, EMI search/recipe latency, GPU time and general mod ability coverage"));
            report.put("coverage",Map.of("controller","scripted movement/camera and client attacks, not a general AI agent","bloodMoon","Enhanced Celestials 2 API in reserved nighttime dimension when installed; active event checked before measuring; no global time/weather edits","allModAbilities","not automatically activated; use feature entity tests and manual profiles","crashDetection","stage checkpoints plus recovery journal; no guarantee against OS/JVM failure","terrain","repeat runs reuse generated chunks; compare equally warm routes","playerState","position, dimension, mode and movement abilities restored; vanilla/mod statistics, advancements and mod-global side effects are not rolled back"));
        }
        ServerPlayerEntity player(){return server.getPlayerManager().getPlayer(owner);}
        String status(){return "Run "+id+", stage "+(phase+1)+": "+stage.name+" ("+(measuring?"measuring":"warm-up/loading")+"), cycle "+cycle;}
        void send(int action) {
            ServerPlayerEntity p=player();if(p==null)return;
            PacketByteBuf buf=PacketByteBufs.create();buf.writeByte(action);buf.writeUuid(id);buf.writeInt(phase);
            buf.writeString(stage==null?"finished":stage.name);buf.writeString(stage==null?"stop":stage.mode);
            buf.writeString(world==null?ARENA.getValue().toString():world.getRegistryKey().getValue().toString());buf.writeDouble(centerX);buf.writeDouble(centerY);buf.writeDouble(centerZ);buf.writeInt(stages.size());buf.writeInt(cycle);buf.writeBoolean(soakNanos>0);
            buf.writeString(phase+1<stages.size() || soakNanos>0?stages.get((phase+1)%stages.size()).name:"Finish and restore player");buf.writeDouble(continuationElapsed/1e9);
            ServerPlayNetworking.send(p,BenchmarkProtocol.CONTROL,buf);
        }
        void next() {
            if(ending)return;
            int next=phase+1;
            if(next>=stages.size() && next%stages.size()==0){if(soakNanos==0 || System.nanoTime()-started>=soakNanos){finish("completed");return;}cycle++;}
            phase=next;stage=stages.get(phase%stages.size());
            Map<String,Object> result=new LinkedHashMap<>();result.put("phase",phase);result.put("cycle",cycle);result.put("name",stage.name);result.put("requestedEntities",stage.count);result.put("workloadNote","AI enabled; missing actors replenished once per 20 ticks, with 8 golem opponents in combat/effects stages");result.put("entityType",stage.type==null?null:Registries.ENTITY_TYPE.getId(stage.type).toString());result.put("status","warming_up");results.add(result);
            for(MobEntity actor:actors)actor.discard();actors.clear();
            clean(server.getWorld(ARENA));clean(server.getWorld(TERRAIN));clean(server.getWorld(LUNAR));BloodMoonAdapter.reset(server.getWorld(LUNAR));
            if(stage.mode.equals("bloodmoon") && !BloodMoonAdapter.available()){result.put("status","skipped_mod_unavailable");checkpoint();next();return;}
            world=server.getWorld(stage.mode.equals("explore")?TERRAIN:stage.mode.equals("bloodmoon")?LUNAR:ARENA);
            centerX=0.5;centerZ=0.5;centerY=stage.mode.equals("explore")?180:70;
            ServerPlayerEntity p=player();if(p==null){finish("owner_missing");return;}
            stageStarted=System.nanoTime();readyAt=measurementAt=0;heartbeat=stageStarted;measuring=false;localTick=0;continuing=false;continuationElapsed=0;continuationInventory=null;lastTeleport=stageStarted;
            // This control packet must precede vanilla respawn/teleport packets on the same connection.
            // It ends the client's previous recording and resets its readiness gate before world changes.
            send(1);
            p.teleport(world,centerX,centerY,centerZ,0,15);p.getAbilities().flying=true;p.sendAbilitiesUpdate();
            // All AI remains enabled. Stations are placed only in the reserved benchmark arena.
            if(stage.type==EntityType.VILLAGER)for(int i=0;i<32;i++){int x=(i%8)*3-12,z=(i/8)*3-6;world.setBlockState(new BlockPos(x,65,z),Blocks.COMPOSTER.getDefaultState());}
            if(stage.type!=null)spawn(stage.type,stage.count);
            if(stage.mode.equals("combat") || stage.mode.equals("effects") || stage.mode.equals("bloodmoon"))spawn(EntityType.IRON_GOLEM,8);
            if(stage.mode.equals("bloodmoon")) {
                try {BloodMoonAdapter.enable(world);result.put("lunarEvent","enhancedcelestials2defaultlunarevents:blood_moon");}
                catch(Exception e){result.put("status","skipped_adapter_unavailable");result.put("reason",e.toString());checkpoint();next();return;}
            }
            continuationInventory=p.getInventory().writeNbt(new net.minecraft.nbt.NbtList());continuationSelected=p.getInventory().selectedSlot;
            checkpoint();p.sendMessage(Text.literal("[Smooth Fix Stress "+(phase%stages.size()+1)+"/"+stages.size()+"] "+stage.name+" — "+AdvancedStageInfo.legacyTask(stage.name,stage.mode)+". Warm-up: 10 seconds; measurement: 60 seconds. Next: "+(phase+1<stages.size() || soakNanos>0?stages.get((phase+1)%stages.size()).name:"Finish and restore player")),false);
        }
        void spawn(EntityType<?> type,int count) {
            for(int i=0;i<count;i++){
                Entity entity=type.create(world);if(!(entity instanceof MobEntity mob))throw new IllegalArgumentException("Cannot create mob: "+type);
                double angle=i*2.3999632297,r=6+(i%7)*2;
                mob.refreshPositionAndAngles(centerX+Math.cos(angle)*r,65,centerZ+Math.sin(angle)*r,0,0);
                mob.initialize(world,world.getLocalDifficulty(mob.getBlockPos()),SpawnReason.COMMAND,null,null);mob.setPersistent();
                if(mob instanceof WitherEntity wither)wither.onSummoned();
                actors.add(mob);
                if(!world.spawnEntity(mob))throw new IllegalStateException("Spawn refused: "+type);
            }
        }
        void tick() {
            if(ending)return;ServerPlayerEntity p=player();long now=System.nanoTime();
            if(p==null){finish("owner_missing");return;}
            if(!p.isAlive()){
                continuationElapsed=readyAt==0?0:now-(measuring?measurementAt:readyAt);send(7);BenchmarkAbilities.revoke(p);p=BenchmarkRespawn.replace(p);
                if(continuationInventory!=null){p.getInventory().clear();p.getInventory().readNbt(continuationInventory);p.getInventory().selectedSlot=continuationSelected;}
                p.teleport(world,centerX,centerY,centerZ,0,15);p.setHealth(p.getMaxHealth());p.setVelocity(net.minecraft.util.math.Vec3d.ZERO);p.fallDistance=0;BenchmarkAbilities.grant(p);p.playerScreenHandler.syncState();
                var result=results.get(phase);result.put("playerDeathsRecovered",((Number)result.getOrDefault("playerDeathsRecovered",0)).intValue()+1);readyAt=0;stageStarted=heartbeat=now;continuing=true;send(8);checkpoint();p.sendMessage(Text.literal("[Smooth Fix Stress] Player died: automatically respawning and resuming this stage. The event remains recorded."),false);return;
            }
            if(localTick%20==0){continuationInventory=p.getInventory().writeNbt(new net.minecraft.nbt.NbtList());continuationSelected=p.getInventory().selectedSlot;}
            if(!p.getWorld().getRegistryKey().equals(world.getRegistryKey())){finish("player_left_test_dimension");return;}
            if(world.getPlayers().size()>1){finish("another_player_entered_test_dimension");return;}
            var heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            if(heap.getMax()>0 && heap.getUsed()>heap.getMax()*0.95){if(pressureAt==0)pressureAt=now;else if(now-pressureAt>10_000_000_000L){finish("aborted_sustained_heap_pressure");return;}}else pressureAt=0;
            if(soakNanos>0 && now-started>=soakNanos && measuring){completeStage("soak_duration_reached");finish("completed");return;}
            if(readyAt==0){if(now-stageStarted>120_000_000_000L)finish("client_loading_timeout");return;}
            if(now-heartbeat>30_000_000_000L){finish("client_heartbeat_timeout");return;}
            localTick++;
            if(localTick%20==0 && stage.type!=null) {
                int count=0,golems=0;
                actors.removeIf(Entity::isRemoved);
                for(MobEntity mob:actors)if(mob.isAlive()) {
                    if(mob.getType()==stage.type)count++;
                    if(mob.getType()==EntityType.IRON_GOLEM)golems++;
                }
                if(count<stage.count)spawn(stage.type,stage.count-count);
                if((stage.mode.equals("combat") || stage.mode.equals("effects") || stage.mode.equals("bloodmoon")) && golems<8 && stage.type!=EntityType.IRON_GOLEM)spawn(EntityType.IRON_GOLEM,8-golems);
            }
            if(localTick%20==0 && entityCount(world)>256){finish("aborted_entity_limit_256");return;}
            if(stage.mode.equals("effects") && localTick%20==0)for(Entity e:world.iterateEntities())if(e instanceof MobEntity mob)mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,60,0));
            if(stage.mode.equals("teleport") && now-lastTeleport>15_000_000_000L){lastTeleport=now;centerX=centerX==0.5?128.5:0.5;centerZ=-centerX+1;p.teleport(world,centerX,70,centerZ,p.getYaw(),p.getPitch());send(3);}
            if(!measuring && now-readyAt>=10_000_000_000L){
                if(stage.mode.equals("bloodmoon")) {
                    try{if(!BloodMoonAdapter.isActive(world)){results.get(phase).put("lunarEventActive",false);finish("blood_moon_not_active_no_valid_measurement");return;}results.get(phase).put("lunarEventActive",true);}
                    catch(Exception e){finish("blood_moon_adapter_failed");return;}
                }
                if(!ServerDiagnostics.startRecording()){finish("profiler_conflict");return;}
                measuring=true;measurementAt=now;results.get(phase).put("status","measuring");results.get(phase).put("loadedEntitiesAtStart",entityCount(world));results.get(phase).put("worldAtStart",worldSnapshot());checkpoint();send(2);
            }
            if(measuring && now-measurementAt>=60_000_000_000L){completeStage("complete");next();}
        }
        void completeStage(String status) {
            if(phase<0 || phase>=results.size())return;
            Map<String,Object> result=results.get(phase);result.put("status",result.containsKey("playerDeathsRecovered") && (status.equals("complete") || status.equals("soak_duration_reached"))?"failed_recovered_player_death":status);result.put("stageElapsedSeconds",(System.nanoTime()-stageStarted)/1e9);
            if(measuring){result.put("server",ServerDiagnostics.finishRecording());measuring=false;}
            result.put("loadedEntitiesAtEnd",entityCount(world));result.put("worldAtEnd",worldSnapshot());checkpoint();
        }
        Map<String,Object> worldSnapshot() {
            Map<String,Integer> types=new TreeMap<>();
            for(Entity entity:world.iterateEntities())types.merge(Registries.ENTITY_TYPE.getId(entity.getType()).toString(),1,Integer::sum);
            int requested=0,opponents=0;
            for(MobEntity entity:actors)if(!entity.isRemoved() && entity.isAlive()) {
                if(entity.getType()==stage.type)requested++;
                if(entity.getType()==EntityType.IRON_GOLEM)opponents++;
            }
            Map<String,Object> snapshot=new LinkedHashMap<>();
            snapshot.put("dimension",world.getRegistryKey().getValue().toString());
            snapshot.put("loadedChunkCount",world.getChunkManager().getLoadedChunkCount());
            snapshot.put("totalChunksLoadedCount",world.getChunkManager().getTotalChunksLoadedCount());
            snapshot.put("chunkCountNote","ServerChunkManager snapshots at the measurement boundaries, not newly generated chunks, disk reloads, generation latency or stage peaks.");
            snapshot.put("loadedEntitiesByType",types);
            snapshot.put("liveRequestedActors",requested);snapshot.put("liveGolemOpponents",opponents);
            ServerPlayerEntity p=player();
            if(p!=null)snapshot.put("playerPosition",Map.of("x",p.getX(),"y",p.getY(),"z",p.getZ()));
            return snapshot;
        }
        void checkpoint(){try{BenchmarkRecovery.atomic(checkpoint,GSON.toJson(report));}catch(Exception e){throw new IllegalStateException("Could not write benchmark checkpoint",e);}}
        void finish(String reason){finish(reason,player());}
        void finish(String reason,ServerPlayerEntity recovering) {
            if(ending)return;ending=true;
            try { if(phase>=0 && phase<results.size() && (results.get(phase).get("status").equals("warming_up") || results.get(phase).get("status").equals("measuring")))completeStage(reason); }
            catch(Exception e){SmoothFix.LOGGER.error("Could not finalize benchmark phase",e);}
            report.put("status",reason);report.put("elapsedSeconds",(System.nanoTime()-started)/1e9);
            try {report.put("memoryAtEnd",MemoryReport.snapshot("server"));send(0);}
            catch(Exception e){SmoothFix.LOGGER.error("Could not notify benchmark client",e);}
            ServerPlayerEntity p=recovering;
            if(p!=null) {
                boolean restored=BenchmarkRecovery.restore(p);report.put("playerRestored",restored);report.put("recoveryVerification",BenchmarkRecovery.lastVerification(owner));
                try {p.sendMessage(Text.literal("Smooth Fix benchmark "+reason+". Server report: "+checkpoint.toAbsolutePath()),false);}catch(Exception ignored){ }
            }
            for(MobEntity actor:actors)actor.discard();actors.clear();
            try {clean(server.getWorld(ARENA));clean(server.getWorld(TERRAIN));clean(server.getWorld(LUNAR));BloodMoonAdapter.reset(server.getWorld(LUNAR));}
            catch(Exception e){SmoothFix.LOGGER.error("Could not clean benchmark entities",e);}
            report.put("terminationReason",reason);report.put("summary",BenchmarkOutcome.counts(results));if(reason.equals("completed"))report.put("status",BenchmarkOutcome.status(results));BenchmarkFinalization.send(p,id,report,this::checkpoint);
            try {checkpoint();SmoothFix.LOGGER.info("Smooth Fix benchmark {}: {}",reason,checkpoint.toAbsolutePath());}
            catch(Exception e){SmoothFix.LOGGER.error("Could not save final benchmark checkpoint",e);}
            finally {if(measuring){ServerDiagnostics.finishRecording();measuring=false;}completed=this;completedAt=System.nanoTime();active=null;}
        }
    }

    private static void discardStaleActors(){List<Entity> pending=new ArrayList<>(staleActors);staleActors.clear();for(Entity entity:pending)if(!entity.isRemoved())entity.discard();}
    private static int entityCount(ServerWorld world){int count=0;for(Entity e:world.iterateEntities())count++;return count;}
    private static void clean(ServerWorld world){if(world==null)return;List<Entity> entities=new ArrayList<>();for(Entity e:world.iterateEntities())if(!(e instanceof net.minecraft.entity.player.PlayerEntity))entities.add(e);for(Entity e:entities)e.discard();}
}
