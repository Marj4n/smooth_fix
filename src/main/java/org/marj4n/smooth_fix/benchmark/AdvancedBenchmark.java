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
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final String TAG="smoothfix_advanced:";
    private static Run active,completed;
    private static long completedAt;
    private static final Set<Entity> stale=new HashSet<>();
    private AdvancedBenchmark() { }
    public static boolean isRunning(){return active!=null;}
    public static String status(){return active==null?"No advanced benchmark running.":active.phase+1+"/"+active.stages.size()+": "+active.stage.name+(active.structureSearch==null?"":"; searching radius "+active.structureSearch.radius()+" blocks");}
    public static boolean stop(){if(active==null)return false;active.finish("stopped_by_operator");return true;}

    public static void install() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
            CommandManager.literal("smoothfix").requires(s->s.hasPermissionLevel(2)).then(CommandManager.literal("stress")
                .then(CommandManager.literal("advanced")
                    .then(CommandManager.literal("start").then(CommandManager.literal("world-copy").executes(c->start(c.getSource().getPlayerOrThrow()))))
                    .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal(status()),false);return 1;}))
                    .then(CommandManager.literal("stop").executes(c->stop()?1:0))
                    .then(CommandManager.literal("list").executes(c->{try{AdvancedPlan p=AdvancedPlan.load();c.getSource().sendFeedback(()->Text.literal("Actual-world stages: "+p.stages.stream().map(s->s.name).toList()+"; auto mod structures: "+p.automaticallySelectedModStructures+". Configure smooth_fix/advanced_benchmark.json. start world-copy confirms disposable save: TNT/Withers/mining/loot permanently change terrain."),false);return 1;}catch(Exception e){c.getSource().sendError(Text.literal(e.toString()));return 0;}}))))));
        ServerLifecycleEvents.SERVER_STARTED.register(server->AdvancedWorldRecovery.restore(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(active!=null)active.finish("server_stopping");drain();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{active=completed=null;stale.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{if(active!=null && active.owner.equals(handler.player.getUuid()))active.finish("owner_disconnected",handler.player);});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{for(String tag:entity.getCommandTags())if(tag.startsWith(TAG) && (active==null || !tag.equals(active.tag())))stale.add(entity);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->stale.remove(entity));
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->ChunkMetrics.loaded(world,chunk,true));
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->ChunkMetrics.loaded(world,chunk,false));
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,be)->{if(world instanceof ServerWorld server && active!=null && active.owner.equals(player.getUuid()))ChunkMetrics.count(server,"playerBlocksBroken",1);});
        ServerTickEvents.END_SERVER_TICK.register(server->{drain();if(completed!=null && System.nanoTime()-completedAt>120_000_000_000L)completed=null;if(active!=null)try{active.tick();}catch(Exception e){SmoothFix.LOGGER.error("Advanced benchmark failed",e);Run failed=active;if(failed!=null){failed.report.put("controllerFailure",Map.of("type",e.getClass().getName(),"message",String.valueOf(e.getMessage()),"phase",failed.phase,"stage",failed.stage.name,"stack",Arrays.stream(e.getStackTrace()).limit(48).map(StackTraceElement::toString).toList()));failed.finish("failed: "+e);}}});
        ServerPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.ADVANCED_RESPONSE,(server,player,handler,buf,sender)->{
            UUID id=buf.readUuid();int phase=buf.readInt(),action=buf.readUnsignedByte();String json=buf.readString(24000);
            server.execute(()->response(player,id,phase,action,json));
        });
    }
    private static void response(ServerPlayerEntity player,UUID id,int phase,int action,String json) {
        Run r=active!=null && active.id.equals(id)?active:completed;
        if(r==null || !r.id.equals(id) || !r.owner.equals(player.getUuid()))return;
        if(action==4 && r==active){r.finish("client_stopped: "+json);return;}
        if(action==0 && r==active && phase==r.phase){r.finish("client_interrupted: "+json);return;}
        if(action==6 && r==active && phase==r.phase && Set.of("chest","lootr").contains(r.stage.mode)){
            if(r.container!=null)r.emptyContainers.add(r.container);r.container=null;
            r.prepareContainer(player);if(r.container==null){r.skip("skipped_no_nonempty_container");return;}r.send(3);return;
        }
        if(action==7 && r==active && phase==r.phase){r.heartbeat=System.nanoTime();return;}
        if(action==5 && r==active && phase==r.phase){r.result().put("clientSkipReason",json);r.skip("skipped_client_adapter");return;}
        if(r==active && phase==r.phase){if(action==1 && r.readyAt==0 && r.structureSearch==null)r.readyAt=System.nanoTime()-r.continuationAt;if(action==1 || action==3)r.heartbeat=System.nanoTime();}
        if(action==2 && phase>=0 && phase<r.results.size())try{
            JsonObject summary=JsonParser.parseString(json).getAsJsonObject();r.results.get(phase).put("client",summary);
            boolean passed=summary.has("workloadValidated") && summary.get("workloadValidated").getAsBoolean();
            r.results.get(phase).put("clientActionValidation",passed?"observed_success":"not_exercised_or_failed");r.classify(r.results.get(phase));r.refreshOutcome();r.checkpoint();if(r==completed)BenchmarkFinalization.update(player,r.id,r.report);
        }catch(Exception e){SmoothFix.LOGGER.warn("Invalid advanced client summary",e);}
    }
    private static int start(ServerPlayerEntity player) {
        MinecraftServer server=player.getServer();
        if(active!=null || ServerBenchmark.isRunning() || ServerDiagnostics.isProfiling()){player.sendMessage(Text.literal("Stop the current profile/benchmark first."),false);return 0;}
        if(server.getPlayerManager().getPlayerList().size()!=1){player.sendMessage(Text.literal("Advanced benchmark requires one player on this disposable server/world copy."),false);return 0;}
        if(!ServerPlayNetworking.canSend(player,BenchmarkProtocol.ADVANCED_CONTROL)){player.sendMessage(Text.literal("Both sides need the same advanced Smooth Fix build."),false);return 0;}
        try {
            AdvancedPlan plan=AdvancedPlan.load();
            if(!BenchmarkRecovery.restore(player) || !AdvancedWorldRecovery.restore(server))throw new IllegalStateException("Unresolved recovery journal");
            BenchmarkRecovery.saveFull(player);AdvancedWorldRecovery.save(server.getOverworld());
            active=new Run(player,plan);active.checkpoint();active.next();
            player.sendMessage(Text.literal("Advanced stress started IN THE REAL WORLD COPY. Keep focused. Original inventory/location restored; terrain/loot/advancements are not rolled back. Stop: /smoothfix stress stop."),false);return 1;
        }catch(Exception e){SmoothFix.LOGGER.error("Cannot start advanced benchmark",e);if(active!=null)active.finish("start_failed");else {BenchmarkRecovery.restore(player);AdvancedWorldRecovery.restore(server);}player.sendMessage(Text.literal("Advanced benchmark failed to start: "+e),false);return 0;}
    }
    private static void drain(){for(Entity e:new ArrayList<>(stale))if(!e.isRemoved())e.discard();stale.clear();}
    /** Spawned projectiles inherit their test owner's token; natural entities remain untouched. */
    public static void observeSpawn(Entity entity){if(entity instanceof ProjectileEntity p && p.getOwner()!=null)copyOwnership(p.getOwner(),entity);}
    public static void copyOwnership(Entity source,Entity target){for(String tag:source.getCommandTags())if(tag.startsWith(TAG))target.addCommandTag(tag);}
    public static boolean ownsActor(Entity entity){Run r=active;return r!=null && entity!=null && entity.getCommandTags().contains(r.tag());}
    public static void observeDamage(net.minecraft.entity.LivingEntity target,net.minecraft.entity.damage.DamageSource source) {
        Run r=active;if(r!=null && source.getAttacker() instanceof ServerPlayerEntity p && r.owner.equals(p.getUuid()) && target.getCommandTags().contains(r.tag()))ChunkMetrics.count(r.world,"playerHitsAccepted",1);
    }

    private static final class Run {
        final UUID id=UUID.randomUUID(),owner;
        final MinecraftServer server;
        final AdvancedPlan plan;
        final List<AdvancedPlan.Stage> stages;
        final Set<AdvancedPlan.Stage> optionalStructures=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Map<String,Object>> results=new ArrayList<>();
        final Map<String,Object> report=new LinkedHashMap<>();
        final Path file;
        final List<Entity> actors=new ArrayList<>();
        final List<BlockPos> tntSites=new ArrayList<>();
        final long started=System.nanoTime();
        final double originX,originZ;
        ServerWorld world;
        AdvancedPlan.Stage stage;
        ChunkMetrics.Recording chunks;
        int phase=-1,ticks,lastContainerId=-1,lastInventoryItems;
        String terminationReason;
        ItemStack lastHotbar;
        long phaseAt,readyAt,heartbeat,pressureAt,preparationAt,searchProgressAt,preparedAt;
        IncrementalStructureSearch structureSearch;
        boolean ending,profiling;
        double x,y,z,coldX,coldZ;
        BlockPos container,fixture;
        final Set<BlockPos> emptyContainers=new HashSet<>();
        Vec3d safePosition;
        NbtList continuationInventory;
        int continuationSelected;
        List<ItemStack> continuationStacks;
        long continuationAt;
        boolean continuing;
        BlockState fixtureOriginal;
        BlockPos lastStructure;
        Run(ServerPlayerEntity player,AdvancedPlan plan) {
            this.owner=player.getUuid();this.server=player.getServer();this.plan=plan;this.stages=new ArrayList<>(plan.stages);
            originX=player.getWorld()==server.getOverworld()?player.getX():server.getOverworld().getSpawnPos().getX();originZ=player.getWorld()==server.getOverworld()?player.getZ():server.getOverworld().getSpawnPos().getZ();
            var registry=server.getRegistryManager().get(RegistryKeys.STRUCTURE);int added=0,index=!stages.isEmpty() && stages.get(stages.size()-1).mode.equals("save")?stages.size()-1:stages.size();
            for(Identifier key:registry.getIds().stream().sorted(Comparator.comparing(Identifier::toString)).toList())if(!key.getNamespace().equals("minecraft") && added<plan.automaticallySelectedModStructures) {
                var entry=registry.entryOf(RegistryKey.of(RegistryKeys.STRUCTURE,key));
                if(!server.getOverworld().getChunkManager().getStructurePlacementCalculator().getPlacements(entry).isEmpty()){var optional=new AdvancedPlan.Stage("mod_structure_"+key,"structure",key.toString(),0);optionalStructures.add(optional);stages.add(index++,optional);added++;}
            }
            file=server.getSavePath(WorldSavePath.ROOT).resolve("smooth_fix/advanced_benchmarks/"+id+".json");
            report.put("runId",id.toString());report.put("build",SmoothFix.BUILD_ID);report.put("suite","actual_world_advanced_v2");report.put("status","running");report.put("stages",results);
            report.put("installedMods",net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream().map(m->Map.of("id",m.getMetadata().getId(),"version",m.getMetadata().getVersion().getFriendlyString())).toList());
            report.put("worldSeed",server.getOverworld().getSeed());report.put("worldCopyAcknowledged",true);
            report.put("limitations",List.of("Terrain destruction, generated chunks, loot state, advancements and mod-global effects are permanent in the test copy.","Scripted camera/flight/native attacks and UI actions; no claim of every mod-specific ability being forced.","Client/server clocks are not synchronized. Stage recording includes preparation/loading; first-render latency excludes GPU completion.","Structure search expands in block-radius bands until found or the world border is exhausted. Disabled, unregistered or unsupported placements are explicitly skipped. Native structure-start generation can add work while searching.","Routes become warm on repeated runs. A cold route is validated only by observed generation hooks.","Hook timings/sampling add overhead; no GPU hardware counters or index rebake is forced."));
        }
        String tag(){return TAG+id+":"+phase;}
        ServerPlayerEntity player(){return server.getPlayerManager().getPlayer(owner);}
        Map<String,Object> result(){return results.get(phase);}
        void send(int action) {
            ServerPlayerEntity p=player();if(p==null)return;
            var data=new LinkedHashMap<String,Object>();data.put("action",action);data.put("run",id.toString());data.put("phase",phase);data.put("stage",stage==null?"finished":stage.name);data.put("mode",stage==null?"stop":stage.mode);
            data.put("totalStages",stages.size());data.put("nextStage",phase+1<stages.size()?stages.get(phase+1).name:"Finish and restore player");data.put("optionalSearchSeconds",stage!=null && optionalStructures.contains(stage)?plan.optionalModStructureSearchSeconds:0);
            data.put("prepared",action==3 || action==8);data.put("continuing",continuing);data.put("seconds",stage==null?0:stage.seconds);data.put("dimension",world==null?"minecraft:overworld":world.getRegistryKey().getValue().toString());data.put("x",x);data.put("y",y);data.put("z",z);data.put("queries",plan.emiQueries);data.put("entities",actors.stream().filter(e->e instanceof MobEntity && e.getType()!=EntityType.IRON_GOLEM).map(Entity::getId).toList());
            data.put("actionElapsedSeconds",continuing?continuationAt/1e9:0);data.put("searchingStructure",structureSearch!=null);
            if(structureSearch!=null)data.put("structureSearch",structureSearch.progress());
            if(container!=null)data.put("container",Map.of("x",container.getX(),"y",container.getY(),"z",container.getZ()));
            var buf=PacketByteBufs.create();buf.writeString(GSON.toJson(data),24000);ServerPlayNetworking.send(p,BenchmarkProtocol.ADVANCED_CONTROL,buf);
        }
        void next() throws Exception {
            cleanup();if(++phase>=stages.size()){finish("completed");return;}
            stage=stages.get(phase);ticks=0;readyAt=0;heartbeat=phaseAt=System.nanoTime();container=null;lastContainerId=-1;emptyContainers.clear();safePosition=null;continuationInventory=null;continuationStacks=null;continuing=false;continuationAt=0;
            world=server.getWorld(stage.mode.equals("nether")?World.NETHER:stage.mode.equals("end")?World.END:World.OVERWORLD);
            Map<String,Object> data=new LinkedHashMap<>();data.put("phase",phase);data.put("name",stage.name);data.put("mode",stage.mode);data.put("target",stage.target);data.put("requestedEntities",stage.count);data.put("status","preparing");data.put("clientActionValidation","awaiting_client");results.add(data);
            player().sendMessage(Text.literal("[Smooth Fix Advanced "+(phase+1)+"/"+stages.size()+"] "+stage.name+" — "+AdvancedStageInfo.task(stage.mode)+". Action duration "+stage.seconds+" seconds."),false);
            if(world==null){data.put("status","skipped_dimension_missing");checkpoint();next();return;}
            x=originX;z=originZ;
            if(stage.mode.equals("structure") && player().getServerWorld()==world){x=player().getX();z=player().getZ();}
            if(stage.mode.equals("cold_route")){coldX=originX+8192+((owner.getLeastSignificantBits()&31)*1024);coldZ=originZ+8192;x=coldX;z=coldZ;}
            if(stage.target.equals("previous_cold")){x=coldX;z=coldZ;}
            if(stage.mode.equals("nether")){x=originX/8;z=originZ/8;}
            if(stage.mode.equals("end")){x=0;z=0;}
            if((stage.mode.equals("chest") || stage.mode.equals("lootr")) && lastStructure!=null){x=lastStructure.getX();z=lastStructure.getZ();}
            y=world==server.getWorld(World.NETHER)?100:140;
            // Start both recorders BEFORE native teleport/chunk generation and synchronous setup work.
            send(1);chunks=ChunkMetrics.start(world);if(!ServerDiagnostics.startRecording())throw new IllegalStateException("Profiler conflict");profiling=true;
            data.put("worldBeforePreparation",snapshot());ServerPlayerEntity p=player();p.closeHandledScreen();p.changeGameMode(GameMode.SURVIVAL);BenchmarkAbilities.grant(p);p.teleport(world,x,y,z,0,20);
            preparationAt=System.nanoTime();
            if(stage.mode.equals("structure")) {
                var registry=server.getRegistryManager().get(RegistryKeys.STRUCTURE);RegistryEntryList<Structure> entries;
                if(stage.target.equals("#minecraft:village"))entries=VillageStructureTargets.resolve(world);
                else if(stage.target.startsWith("#"))entries=registry.getEntryList(TagKey.of(RegistryKeys.STRUCTURE,new Identifier(stage.target.substring(1)))).orElse(null);
                else {var key=RegistryKey.of(RegistryKeys.STRUCTURE,new Identifier(stage.target));entries=registry.getEntry(key).map(RegistryEntryList::of).orElse(null);}
                if(entries==null || entries.size()==0){skip("skipped_structure_unregistered");return;}
                List<String> targetIds=new ArrayList<>();for(var entry:entries)entry.getKey().ifPresent(key->targetIds.add(key.getValue().toString()));data.put("resolvedStructureTargets",targetIds);
                structureSearch=new IncrementalStructureSearch(world,entries,new BlockPos(MathHelper.floor(x),80,MathHelper.floor(z)),plan.structureSearchRadiusStepBlocks);
                data.put("structureSearchOrigin",Map.of("x",x,"z",z));data.put("status","searching_structure");data.put("structureSearch",structureSearch.progress());
                if(structureSearch.unavailable()!=null){skip(structureSearch.unavailable());return;}
                send(6);searchProgressAt=System.nanoTime();checkpoint();return;
            }
            prepareStage(p);
        }
        GroundRoutePlanner.Point findStanding(ServerPlayerEntity p) {
            var terrain=new GroundCollisionTerrain(world,p);
            int cx=MathHelper.floor(x),cz=MathHelper.floor(z);int candidates=0;Set<GroundRoutePlanner.Point> tested=new HashSet<>();
            boolean nether=world==server.getWorld(World.NETHER);
            for(int radius=0;radius<=12;radius+=2)for(int dx=-radius;dx<=radius;dx+=2)for(int dz=-radius;dz<=radius;dz+=2){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius || !world.isChunkLoaded(new BlockPos(cx+dx,0,cz+dz)))continue;
                int top=nether?110:world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,cx+dx,cz+dz);
                for(int height=top;height>=(nether?32:top);height--){
                    var point=terrain.step(new GroundRoutePlanner.Point(cx+dx,height,cz+dz),0,0);if(point==null || !tested.add(point))continue;
                    Set<GroundRoutePlanner.Point> seen=new HashSet<>();ArrayDeque<GroundRoutePlanner.Point> queue=new ArrayDeque<>();seen.add(point);queue.add(point);
                    double extent=0;
                    while(!queue.isEmpty() && seen.size()<256){var at=queue.removeFirst();extent=Math.max(extent,Math.hypot(at.x()-point.x(),at.z()-point.z()));
                        for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){var next=terrain.step(at,d[0],d[1]);if(next!=null && seen.add(next))queue.addLast(next);}}
                    if(extent>=8 && seen.size()>=24){result().put("walkableStartConnectedNodes",seen.size());return point;}
                    if(++candidates>=64)return null;
                }
            }
            return null;
        }
        void prepareStage(ServerPlayerEntity p) throws Exception {
            Map<String,Object> data=result();
            world.getChunk(MathHelper.floor(x)>>4,MathHelper.floor(z)>>4);
            if(world!=server.getWorld(World.NETHER))y=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,MathHelper.floor(x),MathHelper.floor(z))+10;
            if(AdvancedStageInfo.walking(stage.mode)){
                var standing=findStanding(p);if(standing==null){skip("skipped_no_safe_walkable_start");return;}
                x=standing.x()+.5;y=standing.y();z=standing.z()+.5;
            }
            p.teleport(world,x,y,z,0,25);p.setVelocity(Vec3d.ZERO);p.fallDistance=0;BenchmarkAbilities.grant(p,!AdvancedStageInfo.walking(stage.mode));safePosition=p.getPos();
            if(Set.of("combat","effects","bloodmoon","wither").contains(stage.mode)) {
                if(stage.mode.equals("bloodmoon")) {
                    if(!BloodMoonAdapter.available() || !AdvancedWorldRecovery.canForceMoon(server)){skip("skipped_blood_moon_adapter_or_backup_unavailable");return;}
                    // Enhanced Celestials suppresses lunar events while rain strength is visible.
                    world.setWeather(24000,0,false,false);world.setRainGradient(0);world.setThunderGradient(0);
                    world.setTimeOfDay((world.getTimeOfDay()/24000)*24000+14000);BloodMoonAdapter.enableInWorld(world);
                }
                EntityType<?> type=Registries.ENTITY_TYPE.getOrEmpty(new Identifier(stage.target)).orElse(null);
                if(type==null){skip("skipped_entity_unregistered");return;}
                spawn(type,stage.count);if(!stage.mode.equals("combat"))spawn(EntityType.IRON_GOLEM,8);p.getInventory().setStack(8,new ItemStack(Items.DIAMOND_SWORD));p.getInventory().selectedSlot=8;
            }
            if(stage.mode.equals("chest") || stage.mode.equals("lootr")){prepareContainer(p);if(container==null){skip("skipped_lootr_block_unavailable");return;}}
            if(stage.mode.equals("inventory") || stage.mode.startsWith("emi_"))fillInventory(p);
            if(stage.mode.equals("tnt")){
                BlockPos dry=findDrySurface(MathHelper.floor(x),MathHelper.floor(z),64);
                if(dry==null){skip("skipped_no_loaded_dry_terrain_for_tnt");return;}
                x=dry.getX()+.5;z=dry.getZ()+.5;y=dry.getY()+8;tntSites.clear();
                for(int i=0;i<stage.count;i++){double angle=(i+1)*2.4;BlockPos site=findDrySurface(MathHelper.floor(x+Math.cos(angle)*10),MathHelper.floor(z+Math.sin(angle)*10),8);tntSites.add(site==null?dry:site);}
                p.teleport(world,x,y,z,0,35);BenchmarkAbilities.grant(p);data.put("observerHeightAboveTerrain",8);data.put("tntSites",tntSites.stream().map(pos->Map.of("x",pos.getX(),"y",pos.getY(),"z",pos.getZ())).toList());
            }
            if(stage.mode.equals("weather"))world.setWeather(0,24000,true,true);
            p.getInventory().markDirty();p.playerScreenHandler.syncState();
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(p.getInventory().selectedSlot));lastHotbar=p.getInventory().getStack(0).copy();lastInventoryItems=inventoryItems(p);data.put("preparationMs",(System.nanoTime()-preparationAt)/1e6);data.put("worldAfterPreparation",snapshot());data.put("status","waiting_for_client");preparedAt=System.nanoTime();captureContinuation(p);send(3);checkpoint();
        }
        void prepareContainer(ServerPlayerEntity p) {
            boolean lootr=stage.mode.equals("lootr");
            if(fixture!=null){world.setBlockState(fixture,fixtureOriginal);fixture=null;fixtureOriginal=null;}
            if(emptyContainers.isEmpty()){for(int slot=0;slot<36;slot++)p.getInventory().setStack(slot,ItemStack.EMPTY);p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();}
            for(int cx=(MathHelper.floor(x)>>4)-4;cx<=(MathHelper.floor(x)>>4)+4 && container==null;cx++)for(int cz=(MathHelper.floor(z)>>4)-4;cz<=(MathHelper.floor(z)>>4)+4 && container==null;cz++) {
                Chunk chunk=world.getChunkManager().getChunk(cx,cz,ChunkStatus.FULL,false);
                if(chunk instanceof WorldChunk full)for(var entry:full.getBlockEntities().entrySet()) {
                    String id=Registries.BLOCK.getId(full.getBlockState(entry.getKey()).getBlock()).toString();
                    if(emptyContainers.contains(entry.getKey()))continue;
                    if(entry.getValue() instanceof net.minecraft.screen.NamedScreenHandlerFactory && (lootr?id.startsWith("lootr:"):id.equals("minecraft:chest") || id.equals("minecraft:barrel"))){
                        if(!lootr){if(entry.getValue() instanceof LootableContainerBlockEntity loot)loot.checkLootInteraction(p);
                            if(!(entry.getValue() instanceof net.minecraft.inventory.Inventory inventory) || !ContainerSlots.hasItems(inventory)){result().put("emptyContainersRejected",((Number)result().getOrDefault("emptyContainersRejected",0)).intValue()+1);continue;}}
                        if(emptyContainers.size()>=8)continue;
                        container=entry.getKey().toImmutable();result().put("containerSource","existing_generated_or_player_placed");break;
                    }
                }
            }
            if(container==null) {
                Block block=lootr?Registries.BLOCK.getIds().stream().filter(id->id.getNamespace().equals("lootr") && id.getPath().equals("lootr_chest")).findFirst().map(Registries.BLOCK::get).orElse(null):Blocks.CHEST;
                if(block==null)return;
                container=new BlockPos(MathHelper.floor(x)+2,world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,MathHelper.floor(x)+2,MathHelper.floor(z)),MathHelper.floor(z));while(!world.getBlockState(container).isAir() && container.getY()<world.getTopY()-1)container=container.up();if(!world.getBlockState(container).isAir()){container=null;return;}fixture=container;fixtureOriginal=world.getBlockState(container);world.setBlockState(container,block.getDefaultState());
                BlockEntity be=world.getBlockEntity(container);if(be instanceof LootableContainerBlockEntity loot){loot.setLootTable(new Identifier("minecraft","chests/simple_dungeon"),world.getSeed());if(!lootr)loot.checkLootInteraction(p);}
                if(!lootr && be instanceof net.minecraft.inventory.Inventory inventory && !ContainerSlots.hasItems(inventory))inventory.setStack(0,new ItemStack(Items.IRON_INGOT,16));
                result().put("containerSource","explicit_fixture_in_actual_world");
            }
            result().put("containerBlock",Registries.BLOCK.getId(world.getBlockState(container).getBlock()).toString());
            BlockPos stand=null;
            for(int dy=0;dy<=2 && stand==null;dy++)for(Direction direction:Direction.Type.HORIZONTAL){BlockPos candidate=container.offset(direction,2).up(dy);if(world.getBlockState(candidate).isAir() && world.getBlockState(candidate.up()).isAir()){stand=candidate;break;}}
            if(stand==null)throw new IllegalStateException("No reachable air stand next to container "+container);
            x=stand.getX()+.5;z=stand.getZ()+.5;y=stand.getY();p.teleport(world,x,y,z,180,20);result().put("containerStand",Map.of("x",x,"y",y,"z",z));
            // Lootr's ordinary per-player use path runs in survival; flight/invulnerability are temporary.
            p.changeGameMode(GameMode.SURVIVAL);BenchmarkAbilities.grant(p);
        }
        BlockPos findDrySurface(int centerX,int centerZ,int radius){
            for(int r=0;r<=radius;r+=4)for(int dx=-r;dx<=r;dx+=4)for(int dz=-r;dz<=r;dz+=4){
                if(r>0 && Math.abs(dx)!=r && Math.abs(dz)!=r)continue;
                int bx=centerX+dx,bz=centerZ+dz;Chunk chunk=world.getChunkManager().getChunk(bx>>4,bz>>4,ChunkStatus.FULL,false);
                if(!(chunk instanceof WorldChunk))continue;
                int by=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,bx,bz);BlockPos ground=new BlockPos(bx,by-1,bz);
                if(world.getFluidState(ground).isEmpty() && !world.getBlockState(ground).isAir() && world.getBlockState(ground.up()).isAir() && world.getBlockState(ground.up(2)).isAir())return ground.up();
            }
            return null;
        }
        void fillInventory(ServerPlayerEntity p) {
            p.changeGameMode(GameMode.SURVIVAL);BenchmarkAbilities.grant(p);
            List<Item> items=new ArrayList<>();Set<String> namespaces=new HashSet<>();
            for(Identifier id:Registries.ITEM.getIds().stream().sorted(Comparator.comparing(Identifier::toString)).toList())if(!id.getNamespace().equals("minecraft") && namespaces.add(id.getNamespace()))items.add(Registries.ITEM.get(id));
            for(Item item:List.of(Items.DIAMOND_SWORD,Items.DIAMOND_PICKAXE,Items.IRON_INGOT,Items.OAK_LOG,Items.REDSTONE,Items.BOW,Items.BOOK,Items.GOLD_INGOT))items.add(item);
            if(items.isEmpty())items.add(Items.STONE);
            for(int i=0;i<36;i++){ItemStack stack=new ItemStack(items.get(i%items.size()));stack.setCustomName(Text.literal("Smooth Fix benchmark item "+i));p.getInventory().setStack(i,stack);}
            p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();result().put("inventoryFixtureSlots",36);
        }
        void spawn(EntityType<?> type,int count) {
            for(int i=0;i<count;i++) {
                Entity entity=type.create(world);if(!(entity instanceof MobEntity mob))throw new IllegalArgumentException("Entity scenario requires a mob: "+type);
                double angle=i*2.39996323,ex=x+Math.cos(angle)*(8+i%8),ez=z+Math.sin(angle)*(8+i%8);world.getChunk(MathHelper.floor(ex)>>4,MathHelper.floor(ez)>>4);int ey=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,MathHelper.floor(ex),MathHelper.floor(ez));
                mob.refreshPositionAndAngles(ex,ey,ez,0,0);mob.initialize(world,world.getLocalDifficulty(mob.getBlockPos()),SpawnReason.COMMAND,null,null);mob.setPersistent();mob.addCommandTag(tag());
                if(mob instanceof WitherEntity wither)wither.onSummoned();actors.add(mob);if(!world.spawnEntity(mob))throw new IllegalStateException("Spawn rejected");
            }
        }
        void tick() throws Exception {
            ServerPlayerEntity p=player();long now=System.nanoTime();
            if(p==null){finish("owner_missing");return;}
            if(server.getPlayerManager().getPlayerList().size()!=1){finish("another_player_joined");return;}
            if(!p.isAlive()){
                if(((Number)result().getOrDefault("playerDeathsRecovered",0)).intValue()>=3){complete("failed_repeated_player_deaths");p=BenchmarkRespawn.replace(p);p.teleport(world,x,y,z,0,20);next();return;}
                send(7);BenchmarkAbilities.revoke(p);p=BenchmarkRespawn.replace(p);
                if(continuationInventory!=null){p.getInventory().clear();p.getInventory().readNbt(continuationInventory);p.getInventory().selectedSlot=continuationSelected;}
                result().put("playerDeathsRecovered",((Number)result().getOrDefault("playerDeathsRecovered",0)).intValue()+1);
                resumePlayer(p,"Player died: automatic respawn; resuming the workload");return;
            }
            if(continuing){if(readyAt==0){if(now-preparedAt>120_000_000_000L){complete("failed_recovery_loading_timeout");next();}return;}continuing=false;}
            if(p.getServerWorld()!=world){finish("owner_left_dimension");return;}
            if(AdvancedStageInfo.walking(stage.mode) && structureSearch==null && readyAt!=0){
                if(safePosition!=null && p.getY()<safePosition.y-6 && p.getVelocity().y<-.4){
                    result().put("fallRescues",((Number)result().getOrDefault("fallRescues",0)).intValue()+1);send(7);resumePlayer(p,"Ground lost: returning to the last safe position and resuming");return;
                }
                if(p.isOnGround() && world.isChunkLoaded(p.getBlockPos()) && new GroundCollisionTerrain(world,p).step(new GroundRoutePlanner.Point(p.getBlockX(),p.getY(),p.getBlockZ()),0,0)!=null)safePosition=p.getPos();
            }
            captureContinuation(p);
            var heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            if(heap.getMax()>0 && heap.getUsed()>heap.getMax()*0.95){if(pressureAt==0)pressureAt=now;else if(now-pressureAt>10_000_000_000L){finish("sustained_heap_pressure");return;}}else pressureAt=0;
            if(structureSearch!=null){
                if(optionalStructures.contains(stage) && now-preparationAt>=plan.optionalModStructureSearchSeconds*1_000_000_000L){result().put("optionalSearchBudgetSeconds",plan.optionalModStructureSearchSeconds);skip("skipped_optional_structure_search_budget");return;}
                if(now-heartbeat>30_000_000_000L){finish("client_heartbeat_timeout_during_structure_search");return;}
                structureSearch.tick();result().put("structureSearch",structureSearch.progress());
                if(structureSearch.unavailable()!=null){skip(structureSearch.unavailable());return;}
                var located=structureSearch.found();
                if(located!=null){lastStructure=located.position();x=lastStructure.getX();z=lastStructure.getZ();result().put("locatedStructure",located.structure().getKey().orElseThrow().getValue().toString());result().put("structureLocateMs",(System.nanoTime()-preparationAt)/1e6);structureSearch.close();structureSearch=null;readyAt=0;prepareStage(p);return;}
                if(now-searchProgressAt>=2_000_000_000L){searchProgressAt=now;send(6);checkpoint();}return;
            }
            if(readyAt==0){if(now-preparedAt>120_000_000_000L)finish("client_loading_timeout");return;}
            if(now-heartbeat>30_000_000_000L){finish("client_heartbeat_timeout");return;}
            ticks++;
            if(stage.mode.equals("inventory") && !ItemStack.areEqual(p.getInventory().getStack(0),lastHotbar)){lastHotbar=p.getInventory().getStack(0).copy();ChunkMetrics.count(world,"inventoryHotbarChanges",1);}
            if(Set.of("chest","lootr").contains(stage.mode)){int items=inventoryItems(p);if(items>lastInventoryItems)ChunkMetrics.count(world,"lootItemsReceived",items-lastInventoryItems);lastInventoryItems=items;}
            if(p.currentScreenHandler!=p.playerScreenHandler && p.currentScreenHandler.syncId!=lastContainerId){lastContainerId=p.currentScreenHandler.syncId;ChunkMetrics.count(world,"containerSessionsOpened",1);}
            if(ticks%20==0) {
                if(Set.of("combat","effects","bloodmoon","wither").contains(stage.mode)){int alive=(int)actors.stream().filter(e->e.getType().equals(Registries.ENTITY_TYPE.get(new Identifier(stage.target))) && e.isAlive()).count();if(alive<stage.count)spawn(Registries.ENTITY_TYPE.get(new Identifier(stage.target)),Math.min(8,stage.count-alive));actors.removeIf(Entity::isRemoved);send(3);}

                int owned=0;for(Entity entity:world.iterateEntities())if(entity.getCommandTags().contains(tag()))owned++;
                if(owned>384){finish("test_entity_limit_384");return;}
                result().put("peakOwnedEntities",Math.max(owned,((Number)result().getOrDefault("peakOwnedEntities",0)).intValue()));
                if(stage.mode.equals("effects"))for(Entity entity:actors)if(entity instanceof MobEntity mob && mob.isAlive()){mob.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED,80));mob.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.REGENERATION,80));ChunkMetrics.count(world,"benchmarkEffectApplications",2);}
                if(stage.mode.equals("bloodmoon")){boolean moon=BloodMoonAdapter.isActiveInWorld(world);result().put("bloodMoonActiveObserved",Boolean.TRUE.equals(result().get("bloodMoonActiveObserved")) || moon);}
                if(stage.mode.equals("tnt") && ticks<=stage.count*20){BlockPos site=tntSites.get(ticks/20-1);TntEntity tnt=new TntEntity(world,site.getX()+.5,site.getY(),site.getZ()+.5,p);tnt.setFuse(40);tnt.addCommandTag(tag());actors.add(tnt);world.spawnEntity(tnt);}
                if(stage.mode.equals("teleport") && ticks%100==0){p.teleport(world,x+(ticks%200==0?0:384),y,z,0,20);ChunkMetrics.count(world,"nativeTeleports",1);send(3);}
            }
            if(stage.mode.equals("save") && ticks==40){long at=System.nanoTime();server.save(false,true,true);result().put("explicitWorldSaveMs",(System.nanoTime()-at)/1e6);ChunkMetrics.count(world,"explicitWorldSaves",1);}
            result().put("status","measuring");
            if(now-readyAt>=stage.seconds*1_000_000_000L){complete("measured");next();}
        }
        void captureContinuation(ServerPlayerEntity p){
            if(!p.isAlive())return;
            continuationSelected=p.getInventory().selectedSlot;
            boolean changed=continuationStacks==null || continuationStacks.size()!=p.getInventory().size();
            if(!changed)for(int slot=0;slot<continuationStacks.size();slot++)
                if(!ItemStack.areEqual(continuationStacks.get(slot),p.getInventory().getStack(slot))){changed=true;break;}
            if(!changed)return;
            continuationStacks=new ArrayList<>(p.getInventory().size());
            for(int slot=0;slot<p.getInventory().size();slot++)continuationStacks.add(p.getInventory().getStack(slot).copy());
            continuationInventory=p.getInventory().writeNbt(new NbtList());
        }
        void resumePlayer(ServerPlayerEntity p,String message){
            continuationAt=readyAt==0?0:System.nanoTime()-readyAt;readyAt=0;continuing=true;preparedAt=heartbeat=System.nanoTime();
            Vec3d anchor=safePosition==null?new Vec3d(x,y,z):safePosition;x=anchor.x;y=anchor.y;z=anchor.z;
            p.closeHandledScreen();p.changeGameMode(GameMode.SURVIVAL);p.teleport(world,x,y,z,0,20);p.setVelocity(Vec3d.ZERO);p.fallDistance=0;p.setHealth(p.getMaxHealth());p.setAir(p.getMaxAir());
            BenchmarkAbilities.grant(p,!AdvancedStageInfo.walking(stage.mode));p.getInventory().markDirty();p.playerScreenHandler.syncState();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(p.getInventory().selectedSlot));
            p.sendMessage(Text.literal("[Smooth Fix Advanced] "+message+"; the recovery event remains recorded."),false);send(8);checkpoint();
        }
        void skip(String reason){complete(reason);try{next();}catch(Exception e){throw new IllegalStateException(e);}}
        void complete(String status) {
            result().put("status",status);result().put("elapsedSeconds",(System.nanoTime()-phaseAt)/1e9);result().put("actionWindowSeconds",readyAt==0?0:(System.nanoTime()-readyAt)/1e9);result().put("worldAtEnd",snapshot());
            if(profiling){result().put("server",ServerDiagnostics.finishRecording());profiling=false;}
            if(chunks!=null){result().put("chunkAndMechanicMetrics",chunks.finish());chunks=null;}
            result().put("serverWorkloadValidation",status.startsWith("skipped")?"not_exercised_skipped":validate());result().put("measurementStatus",status);
            ServerPlayerEntity notified=player();if(notified!=null)notified.sendMessage(Text.literal("[Smooth Fix Advanced "+(phase+1)+"/"+stages.size()+"] "+stage.name+" — "+status+"; server validation: "+result().get("serverWorkloadValidation")),false);
            if(status.equals("measured"))result().put("status","measured_pending_client_report");send(2);classify(result());checkpoint();
        }
        String validate() {
            if(stage.mode.equals("bloodmoon"))return Boolean.TRUE.equals(result().get("bloodMoonActiveObserved"))?"event_active_observed":"event_not_observed_active";
            Map<?,?> metrics=(Map<?,?>)result().get("chunkAndMechanicMetrics");Map<?,?> counts=metrics==null?Map.of():(Map<?,?>)metrics.get("mechanicCounters");
            if(stage.mode.equals("cold_route")){Map<?,?> unique=metrics==null?Map.of():(Map<?,?>)metrics.get("uniqueChunksByHook");return unique.get("featureGeneration") instanceof Number n && n.intValue()>0?"feature_generation_observed":"not_validated_as_new_chunks";}
            if(Set.of("chest","lootr").contains(stage.mode))return counts.containsKey("lootItemsReceived")?"native_open_and_loot_received":counts.containsKey("containerSessionsOpened")?"container_opened_no_loot_received":"container_not_opened";
            if(stage.mode.equals("structure")){Object located=result().get("locatedStructure");Map<?,?> context=(Map<?,?>)result().get("worldAtEnd");Map<?,?> starts=context==null?null:(Map<?,?>)context.get("nearbyLoadedStructureStarts");return located!=null && starts!=null && starts.containsKey(located)?"structure_start_loaded":"located_only_structure_not_loaded";}
            if(stage.mode.equals("combat"))return counts.containsKey("playerHitsAccepted")?"player_damage_accepted":"no_player_damage_observed";
            if(stage.mode.equals("tnt"))return counts.containsKey("explosions") && counts.containsKey("explosionDestroyedBlocks")?"native_explosion_and_terrain_destruction_observed":"explosion_or_terrain_destruction_not_observed";
            if(stage.mode.equals("wither"))return counts.containsKey("explosions")?"native_explosions_observed":"explosions_not_observed";
            if(stage.mode.equals("break"))return counts.containsKey("playerBlocksBroken")?"native_block_break_observed":"no_block_break_observed";
            if(stage.mode.equals("inventory"))return counts.containsKey("inventoryHotbarChanges")?"inventory_changes_observed":"no_inventory_changes_observed";
            if(stage.mode.equals("effects"))return counts.containsKey("benchmarkEffectApplications")?"native_status_effects_observed":"status_effects_not_observed";
            if(stage.mode.equals("save"))return counts.containsKey("explicitWorldSaves")?"native_world_save_observed":"world_save_not_observed";
            if(stage.mode.equals("teleport"))return counts.containsKey("nativeTeleports")?"native_teleports_observed":"teleports_not_observed";
            if(stage.mode.equals("weather"))return world.isRaining()?"native_rain_observed":"rain_not_observed";
            return "see_stage_context_and_client_actions";
        }
        void classify(Map<String,Object> result) {
            String status=String.valueOf(result.get("status"));
            if(status.startsWith("skipped") || !"measured".equals(result.get("measurementStatus")))return;
            if(!(result.get("client") instanceof JsonObject client)){result.put("status","measured_pending_client_report");return;}
            String serverValidation=String.valueOf(result.get("serverWorkloadValidation"));
            boolean serverPassed=!Set.of("not_validated_as_new_chunks","container_opened_no_loot_received","container_not_opened","located_only_structure_not_loaded","no_player_damage_observed","explosions_not_observed","explosion_or_terrain_destruction_not_observed","no_block_break_observed","no_inventory_changes_observed","event_not_observed_active","status_effects_not_observed","world_save_not_observed","teleports_not_observed","rain_not_observed").contains(serverValidation);
            boolean clientPassed=client.has("workloadValidated") && client.get("workloadValidated").getAsBoolean();
            result.put("status",result.containsKey("playerDeathsRecovered")?"failed_recovered_player_death":result.containsKey("fallRescues")?"failed_recovered_fall":!serverPassed?"failed_server_workload_verification":!clientPassed?"failed_client_workload_verification":"passed");
        }
        void refreshOutcome(){
            report.put("summary",BenchmarkOutcome.counts(results));
            if("completed".equals(terminationReason))report.put("status",BenchmarkOutcome.status(results));
        }
        Map<String,Object> snapshot() {
            Map<String,Integer> entities=new TreeMap<>(),blockEntities=new TreeMap<>(),structures=new TreeMap<>();
            for(Entity entity:world.iterateEntities())entities.merge(Registries.ENTITY_TYPE.getId(entity.getType()).toString(),1,Integer::sum);
            int cx=MathHelper.floor(x)>>4,cz=MathHelper.floor(z)>>4;var registry=world.getRegistryManager().get(RegistryKeys.STRUCTURE);
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){Chunk chunk=world.getChunkManager().getChunk(cx+dx,cz+dz,ChunkStatus.FULL,false);if(chunk instanceof WorldChunk full){for(BlockEntity be:full.getBlockEntities().values())blockEntities.merge(String.valueOf(Registries.BLOCK_ENTITY_TYPE.getId(be.getType())),1,Integer::sum);full.getStructureStarts().forEach((type,start)->{if(start.hasChildren())structures.merge(String.valueOf(registry.getId(type)),1,Integer::sum);});}}
            Map<String,Object> out=new LinkedHashMap<>();out.put("dimension",world.getRegistryKey().getValue().toString());out.put("loadedChunks",world.getChunkManager().getLoadedChunkCount());out.put("entitiesByType",entities);out.put("nearbyLoadedBlockEntities",blockEntities);out.put("nearbyLoadedStructureStarts",structures);out.put("contextRadiusChunks",4);out.put("timeOfDay",world.getTimeOfDay());out.put("raining",world.isRaining());
            ServerPlayerEntity p=player();if(p!=null)out.put("playerPosition",Map.of("x",p.getX(),"y",p.getY(),"z",p.getZ()));return out;
        }
        int inventoryItems(ServerPlayerEntity p){int count=0;for(int i=0;i<36;i++)count+=p.getInventory().getStack(i).getCount();return count;}
        void cleanup() {
            if(structureSearch!=null){result().put("structureSearch",structureSearch.progress());structureSearch.close();structureSearch=null;}
            ServerPlayerEntity p=player();if(p!=null){p.closeHandledScreen();BenchmarkAbilities.revoke(p);}
            for(ServerWorld w:server.getWorlds()){List<Entity> owned=new ArrayList<>();for(Entity entity:w.iterateEntities())for(String t:entity.getCommandTags())if(t.startsWith(TAG+id+":")){owned.add(entity);break;}for(Entity e:owned)e.discard();}
            actors.clear();tntSites.clear();if(fixture!=null && world!=null){world.setBlockState(fixture,fixtureOriginal);fixture=null;fixtureOriginal=null;}
        }
        void checkpoint(){try{BenchmarkRecovery.atomic(file,GSON.toJson(report));}catch(Exception e){throw new IllegalStateException("Checkpoint write failed",e);}}
        void finish(String reason){finish(reason,player());}
        void finish(String reason,ServerPlayerEntity recovering) {
            if(ending)return;ending=true;
            try{if(phase>=0 && phase<results.size() && profiling)complete(reason);}catch(Exception e){SmoothFix.LOGGER.error("Could not finalize advanced stage",e);}
            try{send(0);cleanup();}catch(Exception e){SmoothFix.LOGGER.error("Could not clean advanced actors",e);}
            if(profiling){ServerDiagnostics.finishRecording();profiling=false;}if(chunks!=null){chunks.finish();chunks=null;}
            terminationReason=reason;report.put("terminationReason",reason);report.put("status",reason);refreshOutcome();report.put("elapsedSeconds",(System.nanoTime()-started)/1e9);
            report.put("playerRestored",recovering!=null && BenchmarkRecovery.restore(recovering));report.put("recoveryVerification",BenchmarkRecovery.lastVerification(owner));report.put("environmentRestored",AdvancedWorldRecovery.restore(server));
            BenchmarkFinalization.send(recovering,id,report,this::checkpoint);
            if(recovering!=null)recovering.sendMessage(Text.literal("[Smooth Fix Advanced] "+report.get("status")+". Server recovery: "+report.get("playerRestored")+". Report: "+file.getFileName()),false);
            try{checkpoint();SmoothFix.LOGGER.info("Advanced benchmark {}: {}",reason,file);}catch(Exception e){SmoothFix.LOGGER.error("Final advanced checkpoint failed",e);}
            completed=this;completedAt=System.nanoTime();active=null;
        }
    }
}
