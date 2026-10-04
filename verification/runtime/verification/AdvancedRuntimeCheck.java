package verification;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.item.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.marj4n.smooth_fix.benchmark.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
/** Real production server mechanics with simulated client responses; no FPS or graphical claims. */
public final class AdvancedRuntimeCheck implements ModInitializer {
    private ServerPlayerEntity player;
    private final RecordingConnection connection=new RecordingConnection();
    private int step,phaseSeen=-1,phaseTicks;
    private long deadline;
    private String originalInventory;
    private static Field field(Class<?> t,String n)throws Exception{Field f=t.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object run()throws Exception{return field(AdvancedBenchmark.class,"active").get(null);}
    @Override public void onInitialize(){if(FabricLoader.getInstance().getEnvironmentType()!=EnvType.SERVER || !Boolean.getBoolean("smoothfix.verify.advanced"))return;ServerLifecycleEvents.SERVER_STARTED.register(this::start);ServerTickEvents.END_SERVER_TICK.register(this::tick);}
    private void plan(String... modes)throws Exception {
        AdvancedPlan p=new AdvancedPlan();p.automaticallySelectedModStructures=0;p.stages.clear();
        for(String mode:modes){AdvancedPlan.Stage s=new AdvancedPlan.Stage(mode,mode,mode.equals("combat")?"minecraft:husk":"",mode.equals("combat")?4:mode.equals("tnt")?1:0);s.seconds=10;p.stages.add(s);}
        Path f=FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/advanced_benchmark.json");Files.createDirectories(f.getParent());Files.writeString(f,new Gson().toJson(p));
    }
    private void start(MinecraftServer server){try{
        deadline=System.nanoTime()+420_000_000_000L;
        player=new ServerPlayerEntity(server,server.getOverworld(),new GameProfile(UUID.randomUUID(),"AdvancedFixture"));server.getPlayerManager().onPlayerConnect(connection,player);
        Object addon=Class.forName("net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl").getMethod("getAddon",ServerPlayNetworkHandler.class).invoke(null,player.networkHandler);
        ((Set<Identifier>)field(addon.getClass().getSuperclass(),"sendableChannels").get(addon)).add(BenchmarkProtocol.ADVANCED_CONTROL);
        player.changeGameMode(GameMode.ADVENTURE);player.teleport(server.getOverworld(),-123.5,150,-321.5,30,15);
        player.getInventory().setStack(0,new ItemStack(Items.DIAMOND,7));player.getInventory().setStack(35,new ItemStack(Items.APPLE,13));player.getInventory().selectedSlot=3;
        originalInventory=player.getInventory().writeNbt(new net.minecraft.nbt.NbtList()).toString();
        plan("panorama","chest","lootr","inventory","combat","tnt","weather","save");
        startCommand(server);Object r=run();field(r.getClass(),"phaseAt").setLong(r,System.nanoTime()-45_000_000_000L);step=1;
    }catch(Throwable t){fail(server,t);}}
    private void startCommand(MinecraftServer server)throws Exception{if(server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress advanced start world-copy")!=1 || run()==null)throw new AssertionError("advanced start failed");}
    private void accept(Object r,int phase,int action,String data)throws Exception{Method m=AdvancedBenchmark.class.getDeclaredMethod("response",ServerPlayerEntity.class,UUID.class,int.class,int.class,String.class);m.setAccessible(true);m.invoke(null,player,field(r.getClass(),"id").get(r),phase,action,data);}
    private void tick(MinecraftServer server){if(step==0)return;try{
        if(System.nanoTime()>deadline)throw new AssertionError("advanced fixture timeout");Object r=run();
        if(step==1){if(r==null)throw new AssertionError("loading prematurely aborted");pass("advanced startup tolerates 45 seconds without client readiness");step=2;}
        if((step==2 || step==5) && r!=null){
            int phase=field(r.getClass(),"phase").getInt(r);AdvancedPlan.Stage stage=(AdvancedPlan.Stage)field(r.getClass(),"stage").get(r);
            if(phase!=phaseSeen){if(phaseSeen>=0)accept(r,phaseSeen,2,"{\"workloadValidated\":true,\"fixtureClient\":true}");phaseSeen=phase;phaseTicks=0;System.out.println("SMOOTHFIX_VERIFY_STAGE advanced "+phase+" "+stage.mode);accept(r,phase-1,0,"stale phase abort");if(run()!=r)throw new AssertionError("stale abort cancelled next phase");}
            phaseTicks++;int cx=player.getChunkPos().x,cz=player.getChunkPos().z;for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)player.getServerWorld().setChunkForced(cx+dx,cz+dz,true);accept(r,phase,1,"");accept(r,phase,3,"");
            if(Set.of("chest","lootr").contains(stage.mode) && phaseTicks==2){BlockPos pos=(BlockPos)field(r.getClass(),"container").get(r);var world=player.getServerWorld();world.getBlockState(pos).onUse(world,player,Hand.MAIN_HAND,new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false));if(player.currentScreenHandler==player.playerScreenHandler)throw new AssertionError(stage.mode+" did not open native handler");for(int i=0;i<player.currentScreenHandler.slots.size()-36;i++)if(player.currentScreenHandler.getSlot(i).hasStack()){player.currentScreenHandler.onSlotClick(i,0,SlotActionType.QUICK_MOVE,player);break;}}
            if(stage.mode.equals("inventory") && phaseTicks==2)player.playerScreenHandler.onSlotClick(9,0,SlotActionType.SWAP,player);
            if(stage.mode.equals("combat") && phaseTicks==1){for(Entity e:(List<Entity>)field(r.getClass(),"actors").get(r))if(e instanceof MobEntity mob)mob.setAiDisabled(true);}
            if(stage.mode.equals("break") && phaseTicks==2){var w=player.getServerWorld();int y=w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,player.getBlockX(),player.getBlockZ());BlockPos pos=new BlockPos(player.getBlockX(),y-1,player.getBlockZ());player.teleport(w,pos.getX()+.5,y+1,pos.getZ()+.5,0,65);if(!player.interactionManager.tryBreakBlock(pos))throw new AssertionError("native terrain break refused");}
            if(stage.mode.equals("combat") && phaseTicks==15){List<Entity> actors=(List<Entity>)field(r.getClass(),"actors").get(r);for(Entity e:actors)if(e instanceof MobEntity && e.getType()==EntityType.HUSK){player.attack(e);break;}}
            // Weather visibility and Wither charge-up must finish before fixture timestamps advance.
            boolean nativeWitherReady=true;
            if(stage.mode.equals("wither")){Object recording=field(r.getClass(),"chunks").get(r);nativeWitherReady=((Map<?,?>)field(recording.getClass(),"counters").get(recording)).containsKey("explosions");}
            if(field(r.getClass(),"structureSearch").get(r)==null && nativeWitherReady && phaseTicks>=(stage.mode.equals("tnt")?90:stage.mode.equals("weather")?120:stage.mode.equals("save")?45:stage.mode.equals("teleport")?105:stage.mode.equals("wither")?260:Set.of("combat","effects","bloodmoon","wither").contains(stage.mode)?65:6))field(r.getClass(),"readyAt").setLong(r,System.nanoTime()-(stage.seconds+1)*1_000_000_000L);
            return;
        }
        if(step==5 && r==null){
            Object c=field(AdvancedBenchmark.class,"completed").get(null);accept(c,phaseSeen,2,"{\"workloadValidated\":true,\"fixtureClient\":true}");Map<?,?> report=(Map<?,?>)field(c.getClass(),"report").get(c);List<Map<String,Object>> stages=(List<Map<String,Object>>)report.get("stages");
            if(stages.size()!=21)throw new AssertionError("full transition coverage: "+stages.size());
            for(Map<String,Object> stage:stages){String status=String.valueOf(stage.get("status"));if(!status.equals("passed") && !status.startsWith("skipped"))throw new AssertionError("full transition failed: "+stage.get("name")+" "+status+" "+stage.get("serverWorkloadValidation"));}
            assertRestored(server);pass("all 21 default advanced server transitions completed or explicitly skipped; client responses simulated; native terrain, actors, dimensions, weather and save exercised");
            plan("weather");startCommand(server);r=run();accept(r,0,1,"");field(r.getClass(),"heartbeat").setLong(r,System.nanoTime()-31_000_000_000L);step=4;return;
        }
        if(step==2){
            Object c=field(AdvancedBenchmark.class,"completed").get(null);accept(c,phaseSeen,2,"{\"workloadValidated\":true,\"fixtureClient\":true}");Map<?,?> report=(Map<?,?>)field(c.getClass(),"report").get(c);
            if(!String.valueOf(report.get("status")).startsWith("completed"))throw new AssertionError("advanced suite failed: "+report);
            List<Map<String,Object>> stages=(List<Map<String,Object>>)report.get("stages");if(stages.size()!=8)throw new AssertionError("stage count");
            for(Map<String,Object> s:stages){String mode=(String)s.get("mode");if(!s.containsKey("server") || !s.containsKey("chunkAndMechanicMetrics"))throw new AssertionError("missing stage recorder");
                String expected=switch(mode){case "chest","lootr"->"native_open_and_loot_received";case "combat"->"player_damage_accepted";case "inventory"->"inventory_changes_observed";case "tnt"->"native_explosion_and_terrain_destruction_observed";default->null;};if(expected!=null && !expected.equals(s.get("serverWorkloadValidation")))throw new AssertionError(mode+" missing successful action: "+s);
            }
            if(!Boolean.TRUE.equals(report.get("environmentRestored")) || !Boolean.TRUE.equals(report.get("playerRestored")))throw new AssertionError("recovery failed");if(!"completed".equals(report.get("status")))throw new AssertionError("incorrect aggregate after client reports: "+report.get("status"));if(!Boolean.TRUE.equals(((Map<?,?>)report.get("recoveryVerification")).get("all")))throw new AssertionError("server recovery verification missing");assertRestored(server);
            pass("eight actual-world stages complete; native chest + Lootr loot, inventory swap, accepted combat damage, TNT explosion and world save observed");
            player=ContinuationCheck.run(server,player);assertRestored(server);
            verifyStructureEdges(server);
            plan("end");startCommand(server);r=run();connection.assertControlBeforeRespawn();accept(r,-1,0,"old phase abort");if(run()!=r)throw new AssertionError("stale response aborted End");accept(r,-1,4,"explicit stop across transition");if(run()!=null)throw new AssertionError("explicit stop ignored");assertRestored(server);pass("End transition control precedes respawn; stale abort rejected and intentional stale-phase stop restores inventory");
            plan("inventory");startCommand(server);r=run();field(r.getClass(),"preparedAt").setLong(r,System.nanoTime()-121_000_000_000L);step=3;return;
        }
        if(step==3){if(r!=null)return;assertRestored(server);pass("advanced loading timeout restores full inventory and original player state");
            AdvancedPlan full=new AdvancedPlan();full.automaticallySelectedModStructures=0;full.structureRadiusChunks=2;
            for(var stage:full.stages){stage.seconds=stage.mode.equals("wither")?30:10;if(Set.of("combat","effects","bloodmoon").contains(stage.mode))stage.count=4;if(stage.mode.equals("wither"))stage.count=1;if(stage.mode.equals("tnt"))stage.count=1;}
            Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/advanced_benchmark.json"),new Gson().toJson(full));phaseSeen=-1;startCommand(server);step=5;return;}
        if(step==4){if(r!=null)return;assertRestored(server);pass("advanced heartbeat timeout cleans actors and restores player/environment");plan("inventory");startCommand(server);net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.networkHandler,server);if(run()!=null)throw new AssertionError("disconnect callback did not stop run");assertRestored(server);pass("disconnect callback restores inventory/hotbar and original player state");step=0;server.getPlayerManager().remove(player);server.stop(false);}
    }catch(Throwable t){fail(server,t);}}
    private void assertRestored(MinecraftServer server){if(player.getServerWorld()!=server.getOverworld() || player.interactionManager.getGameMode()!=GameMode.ADVENTURE || player.getX()!=-123.5 || player.getInventory().selectedSlot!=3 || !originalInventory.equals(player.getInventory().writeNbt(new net.minecraft.nbt.NbtList()).toString()))throw new AssertionError("original player/inventory state changed");for(var world:server.getWorlds())for(Entity e:world.iterateEntities())for(String tag:e.getCommandTags())if(tag.startsWith("smoothfix_advanced:"))throw new AssertionError("owned actor left behind");}
    private void structurePlan(String target,boolean panorama)throws Exception {
        AdvancedPlan p=new AdvancedPlan();p.automaticallySelectedModStructures=0;p.stages.clear();p.stages.add(new AdvancedPlan.Stage("structure_regression","structure",target,0));if(panorama)p.stages.add(new AdvancedPlan.Stage("after_structure","panorama","",0));
        Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/advanced_benchmark.json"),new Gson().toJson(p));
    }
    private void verifyStructureEdges(MinecraftServer server)throws Exception {
        var targets=VillageStructureTargets.resolve(server.getOverworld());Set<String> ids=new HashSet<>();for(var entry:targets)ids.add(entry.getKey().orElseThrow().getValue().toString());
        if(!ids.contains("smoothfix_fixture:replacement") || ids.contains("smoothfix_fixture:outpost"))throw new AssertionError("replacement village target resolver: "+ids);
        if(!VillageStructureTargets.villagePath("small/village_swamp") || !VillageStructureTargets.villagePath("oasis_village") || VillageStructureTargets.villagePath("pillager_outpost"))throw new AssertionError("village identifier classification");
        pass("village structure-set replacement without vanilla village tag is resolved; unrelated outpost excluded; CTOV-style and Integrated-style village IDs recognized");
        ContainerCheck.run(player);GroundNavigationCheck.run(player);
        verifyNativeSearchBands(server);
        structurePlan("#minecraft:village",true);startCommand(server);Object r=run();Object search=field(r.getClass(),"structureSearch").get(r);if(search==null)throw new AssertionError("village search not incremental");
        Method skip=r.getClass().getDeclaredMethod("skip",String.class);skip.setAccessible(true);skip.invoke(r,"skipped_structure_not_found_in_radius");
        if(run()!=r || field(r.getClass(),"phase").getInt(r)!=1)throw new AssertionError("missing village aborted instead of continuing");
        var results=(List<Map<String,Object>>)field(r.getClass(),"results").get(r);if(!"not_exercised_skipped".equals(results.get(0).get("serverWorkloadValidation")) || !results.get(0).containsKey("measurementStatus"))throw new AssertionError("skip report incomplete");
        if(!field(search.getClass(),"closed").getBoolean(search))throw new AssertionError("skip did not close search");AdvancedBenchmark.stop();assertRestored(server);
        pass("original missing-village skip with empty structure map retains recording, continues to panorama and restores player without NullPointerException");
        structurePlan("smooth_fix:missing_structure",true);startCommand(server);r=run();if(field(r.getClass(),"phase").getInt(r)!=1)throw new AssertionError("unregistered structure did not skip");AdvancedBenchmark.stop();assertRestored(server);
        pass("unregistered structure skip safely advances to next stage");
        structurePlan("#minecraft:village",true);startCommand(server);r=run();((Set)field(r.getClass(),"optionalStructures").get(r)).add(field(r.getClass(),"stage").get(r));field(r.getClass(),"preparationAt").setLong(r,System.nanoTime()-91_000_000_000L);
        Method budgetTick=r.getClass().getDeclaredMethod("tick");budgetTick.setAccessible(true);budgetTick.invoke(r);if(field(r.getClass(),"phase").getInt(r)!=1)throw new AssertionError("optional structure budget did not advance");
        var budgetResults=(List<Map<String,Object>>)field(r.getClass(),"results").get(r);if(!"skipped_optional_structure_search_budget".equals(budgetResults.get(0).get("measurementStatus")))throw new AssertionError("optional structure budget not reported");AdvancedBenchmark.stop();assertRestored(server);
        pass("optional mod structure search exceeding 90 seconds skips explicitly and continues; mandatory village search remains unlimited");
        plan("route");startCommand(server);r=run();if(player.getAbilities().flying || !player.getAbilities().allowFlying || !player.getAbilities().invulnerable)throw new AssertionError("walking stage still flies or lacks protection");AdvancedBenchmark.stop();assertRestored(server);
        pass("walking stages use grounded protected survival; stop restores original abilities and player state");
        structurePlan("#minecraft:village",false);startCommand(server);r=run();search=field(r.getClass(),"structureSearch").get(r);field(r.getClass(),"phaseAt").setLong(r,System.nanoTime()-150_000_000_000L);accept(r,0,1,"");
        if(field(r.getClass(),"readyAt").getLong(r)!=0)throw new AssertionError("search prematurely started measurement");Method tick=r.getClass().getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(r);
        if(run()!=r)throw new AssertionError("active search wrongly timed out as client loading");AdvancedBenchmark.stop();assertRestored(server);
        if(field(search.getClass(),"ticketPos").get(search)!=null || !field(search.getClass(),"closed").getBoolean(search))throw new AssertionError("stop leaked search ticket");
        pass("structure search older than 120 seconds remains active with heartbeat; native asynchronous request links; stop releases candidate ticket and restores player");
    }
    private void verifyNativeSearchBands(MinecraftServer server)throws Exception {
        var world=server.getOverworld();var entries=VillageStructureTargets.resolve(world);
        var origin=new BlockPos(-123,80,-321);var search=new IncrementalStructureSearch(world,entries,origin,10000);Class<?> type=search.getClass();Method next=type.getDeclaredMethod("nextCandidate");next.setAccessible(true);
        List<?> groups=(List<?>)field(type,"groups").get(search);Set<Long> all=new HashSet<>();
        for(int band=0;band<3;band++){
            int inner=band*10000,outer=(band+1)*10000;field(type,"inner").setInt(search,inner);field(type,"radius").setInt(search,outer);field(type,"groupIndex").setInt(search,0);field(type,"concentricIndex").setInt(search,0);field(type,"ring").set(search,null);Set<Long> actual=new HashSet<>(),expected=new HashSet<>();
            while(field(type,"groupIndex").getInt(search)<groups.size()){
                Object candidate=next.invoke(search);if(candidate==null)continue;ChunkPos pos=(ChunkPos)field(candidate.getClass(),"position").get(candidate);var placement=(net.minecraft.world.gen.chunk.placement.StructurePlacement)field(candidate.getClass(),"placement").get(candidate);BlockPos locate=placement.getLocatePos(pos);long dx=(long)locate.getX()-origin.getX(),dz=(long)locate.getZ()-origin.getZ();if(StructureSearchRing.inBand(dx*dx+dz*dz,inner,outer))actual.add(pos.toLong());
            }
            for(Object group:groups){var placement=(net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement)field(group.getClass(),"placement").get(group);int spacing=placement.getSpacing(),rx=Math.floorDiv(origin.getX()>>4,spacing),rz=Math.floorDiv(origin.getZ()>>4,spacing),range=(int)Math.ceil(outer/(16.0*spacing))+2;
                for(int dx=-range;dx<=range;dx++)for(int dz=-range;dz<=range;dz++){ChunkPos pos=placement.getStartChunk(world.getChunkManager().getStructurePlacementCalculator().getStructureSeed(),(rx+dx)*spacing,(rz+dz)*spacing);BlockPos locate=placement.getLocatePos(pos);long bx=(long)locate.getX()-origin.getX(),bz=(long)locate.getZ()-origin.getZ(),distance=bx*bx+bz*bz;if(distance<=(long)outer*outer && (inner==0 || distance>(long)inner*inner))expected.add(pos.toLong());}
            }
            if(actual.isEmpty() || !actual.equals(expected))throw new AssertionError("native search missed candidate regions in "+inner+".."+outer+" band: "+actual.size()+" vs "+expected.size());
            for(long pos:actual)if(!all.add(pos))throw new AssertionError("candidate rechecked in expanded radius");
        }
        search.close();pass("actual seeded village candidate bands at 10000, 20000 and 30000 blocks match exhaustive region reference without gaps or duplicate candidate checks");
    }
    private static void pass(String s){System.out.println("SMOOTHFIX_VERIFY_PASS "+s);}
    private void fail(MinecraftServer server,Throwable t){step=0;System.out.println("SMOOTHFIX_VERIFY_FAIL advanced "+t);t.printStackTrace();server.stop(false);}
    private static final class RecordingConnection extends ClientConnection {
        final List<String> events=new ArrayList<>();RecordingConnection(){super(NetworkSide.SERVERBOUND);}
        @Override public void send(Packet<?> packet){record(packet);}@Override public void send(Packet<?> p,PacketCallbacks c){record(p);}
        private void record(Packet<?> p){if(p instanceof CustomPayloadS2CPacket payload && payload.getChannel().equals(BenchmarkProtocol.ADVANCED_CONTROL)){var b=payload.getData();int i=b.readerIndex();try{JsonObject d=JsonParser.parseString(b.readString(24000)).getAsJsonObject();if(!d.has("totalStages") || !d.has("nextStage") || !d.has("optionalSearchSeconds"))throw new AssertionError("stage feedback packet missing total/next/budget");events.add("control:"+d.get("action").getAsInt()+":"+d.get("dimension").getAsString());}finally{b.readerIndex(i);}}else if(p instanceof PlayerRespawnS2CPacket respawn)events.add("respawn:"+respawn.getDimension().getValue());}
        void assertControlBeforeRespawn(){int c=events.lastIndexOf("control:1:minecraft:the_end"),r=events.lastIndexOf("respawn:minecraft:the_end");if(c<0 || r<c)throw new AssertionError("advanced control ordering: "+events);}
    }
}
