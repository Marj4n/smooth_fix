package verification;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.world.GameMode;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.marj4n.smooth_fix.benchmark.*;
import java.lang.reflect.*;
import java.util.*;
/** Headless fixture drives the real commands/events and advances only test timestamps, never distributed. */
public final class BenchmarkRuntimeCheck implements ModInitializer {
    private ServerPlayerEntity player;
    private final RecordingConnection connection=new RecordingConnection();
    private int step=0,phaseSeen=-1,phaseTicks;
    private long deadline;
    private double originalX,originalY,originalZ;
    @Override public void onInitialize(){
        if(FabricLoader.getInstance().getEnvironmentType()!=EnvType.SERVER)return;
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object run()throws Exception{return field(ServerBenchmark.class,"active").get(null);}
    private void start(MinecraftServer server){try{
        deadline=System.nanoTime()+120_000_000_000L;
        if(server.getWorld(ServerBenchmark.ARENA)==null || server.getWorld(ServerBenchmark.TERRAIN)==null || server.getWorld(ServerBenchmark.LUNAR)==null)throw new AssertionError("bundled benchmark dimensions missing");
        System.out.println("SMOOTHFIX_VERIFY_PASS three bundled benchmark dimensions loaded");
        player=new ServerPlayerEntity(server,server.getOverworld(),new GameProfile(UUID.randomUUID(),"BenchmarkFixture"));
        server.getPlayerManager().onPlayerConnect(connection,player);
        player.changeGameMode(GameMode.ADVENTURE);player.teleport(server.getOverworld(),123.5,200,321.5,30,15);
        Object addon=Class.forName("net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl").getMethod("getAddon",ServerPlayNetworkHandler.class).invoke(null,player.networkHandler);
        Class<?> base=addon.getClass().getSuperclass();Field channels=field(base,"sendableChannels");((Set<Identifier>)channels.get(addon)).add(BenchmarkProtocol.CONTROL);
        BenchmarkRecovery.save(player);player.changeGameMode(GameMode.CREATIVE);player.teleport(server.getWorld(ServerBenchmark.ARENA),0,70,0,0,0);
        if(!BenchmarkRecovery.restore(player) || player.getWorld()!=server.getOverworld() || player.interactionManager.getGameMode()!=GameMode.ADVENTURE || player.getX()!=123.5)throw new AssertionError("persisted recovery changed state");
        System.out.println("SMOOTHFIX_VERIFY_PASS persisted recovery restores dimension, location, mode and abilities");
        originalX=player.getX();originalY=player.getY();originalZ=player.getZ();
        if(server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress start")!=1 || run()==null)throw new AssertionError("stress command failed");
        Object pending=run();long earlier=System.nanoTime()-45_000_000_000L;
        field(pending.getClass(),"stageStarted").setLong(pending,earlier);field(pending.getClass(),"heartbeat").setLong(pending,earlier);step=4;
    }catch(Throwable t){fail(server,t);}}
    private void tick(MinecraftServer server){if(step==0)return;try{
        if(System.nanoTime()>deadline)throw new AssertionError("Runtime fixture timed out");
        Object run=run();
        if(step==4){if(run==null)throw new AssertionError("45-second startup loading was incorrectly aborted");System.out.println("SMOOTHFIX_VERIFY_PASS initial loading survives 45 seconds without premature heartbeat abort");step=1;}
        if(run!=null && step==3) {
            int withers=0;for(Entity e:server.getWorld(ServerBenchmark.ARENA).iterateEntities())if(e.getType()==net.minecraft.entity.EntityType.WITHER)withers++;
            if(withers<10)return;if(withers!=10)throw new AssertionError("expected 10 real Withers, got "+withers);
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress stop");assertClean(server);
            if(player.getWorld()!=server.getOverworld() || player.interactionManager.getGameMode()!=GameMode.ADVENTURE)throw new AssertionError("stop did not restore owner");
            System.out.println("SMOOTHFIX_VERIFY_PASS 10 actual Withers spawned; operator stop cleans actors and restores owner");
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress soak 5");
            Object active=run();field(active.getClass(),"readyAt").setLong(active,System.nanoTime());field(active.getClass(),"heartbeat").setLong(active,System.nanoTime()-31_000_000_000L);step=2;return;
        }
        if(run!=null){
            Class<?> type=run.getClass();long now=System.nanoTime();field(type,"heartbeat").setLong(run,now);field(type,"readyAt").setLong(run,now-11_000_000_000L);
            int phase=field(type,"phase").getInt(run);
            if(phase!=phaseSeen){
                phaseSeen=phase;phaseTicks=0;System.out.println("SMOOTHFIX_VERIFY_STAGE "+phase+" "+field(type,"stage").get(run));
                if(phase==6 && BloodMoonAdapter.available()){
                    connection.assertControlBeforeRespawn(phase,ServerBenchmark.LUNAR.getValue().toString());
                    accept(run,phase-1,0,"delayed old-phase dimension abort");
                    if(run()!=run)throw new AssertionError("Old-stage abort cancelled new Blood Moon stage");
                    System.out.println("SMOOTHFIX_VERIFY_PASS delayed previous-stage abort cannot cancel new stage");
                }
                if(phase==7 && BloodMoonAdapter.available())connection.assertControlBeforeRespawn(phase,ServerBenchmark.ARENA.getValue().toString());
                if(phase==9)connection.assertControlBeforeRespawn(phase,ServerBenchmark.TERRAIN.getValue().toString());
            }
            phaseTicks++;
            if(player.getWorld().getRegistryKey().equals(ServerBenchmark.LUNAR) && !BloodMoonAdapter.isActive(player.getServerWorld())){field(type,"readyAt").setLong(run,now);return;}
            if(field(type,"measuring").getBoolean(run) && phaseTicks>=25){
                if(player.getWorld().getRegistryKey().equals(ServerBenchmark.LUNAR) && !BloodMoonAdapter.isActive(player.getServerWorld()))throw new AssertionError("real Blood Moon not active");
                field(type,"measurementAt").setLong(run,now-61_000_000_000L);
            }
            return;
        }
        if(step==1){
            Object completed=field(ServerBenchmark.class,"completed").get(null);
            Map<?,?> report=(Map<?,?>)field(completed.getClass(),"report").get(completed);
            if(!"completed".equals(report.get("status")))throw new AssertionError("Suite aborted: "+report.get("status"));
            List<Map<String,Object>> stages=(List<Map<String,Object>>)report.get("stages");
            if(stages.size()!=10)throw new AssertionError("Expected ten automatic stages");
            for(Map<String,Object> stage:stages) {
                if(!"complete".equals(stage.get("status")))continue;
                Map<?,?> start=(Map<?,?>)stage.get("worldAtStart"),end=(Map<?,?>)stage.get("worldAtEnd"),profile=(Map<?,?>)stage.get("server");
                if(start==null || end==null || !profile.containsKey("tickStartIntervals") || !profile.containsKey("garbageCollectorDeltas"))throw new AssertionError("missing stage diagnostics");
                int total=((Map<?,?>)end.get("loadedEntitiesByType")).values().stream().mapToInt(value->((Number)value).intValue()).sum();
                if(total!=((Number)stage.get("loadedEntitiesAtEnd")).intValue())throw new AssertionError("entity breakdown total differs");
                if(((Number)start.get("loadedChunkCount")).intValue()<0)throw new AssertionError("bad chunk snapshot");
            }
            System.out.println("SMOOTHFIX_VERIFY_PASS per-stage chunk/entity breakdown, cadence and GC deltas present; entity totals reconcile");
            if(BloodMoonAdapter.available() && stages.stream().noneMatch(s->"blood_moon".equals(s.get("name")) && Boolean.TRUE.equals(s.get("lunarEventActive")) && "complete".equals(s.get("status"))))throw new AssertionError("Blood Moon was not measured");
            if(player.getWorld()!=server.getOverworld() || player.interactionManager.getGameMode()!=GameMode.ADVENTURE || Math.abs(player.getX()-originalX)>0.1 || Math.abs(player.getZ()-originalZ)>0.1)throw new AssertionError("suite did not restore owner");
            assertClean(server);System.out.println("SMOOTHFIX_VERIFY_PASS automatic suite stages, real Blood Moon (when installed), checkpoints and cleanup");
            if(server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress wither 10")!=1)throw new AssertionError("wither command");
            step=3;

        }else if(step==2){
            assertClean(server);if(player.getWorld()!=server.getOverworld())throw new AssertionError("timeout recovery");System.out.println("SMOOTHFIX_VERIFY_PASS stalled-client abort cleans/restores owner");
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress start");
            Object pending=run();field(pending.getClass(),"stageStarted").setLong(pending,System.nanoTime()-121_000_000_000L);step=5;
        }else if(step==5){
            Object completed=field(ServerBenchmark.class,"completed").get(null);Map<?,?> report=(Map<?,?>)field(completed.getClass(),"report").get(completed);
            if(!"client_loading_timeout".equals(report.get("status")))throw new AssertionError("Missing bounded startup loading timeout");
            assertClean(server);if(player.getWorld()!=server.getOverworld())throw new AssertionError("loading timeout recovery");
            System.out.println("SMOOTHFIX_VERIFY_PASS 120-second loading timeout cleans/restores owner");
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress wither 10");
            accept(run(),-1,4,"explicit user stop across transition");
            if(run()!=null)throw new AssertionError("Explicit user stop was ignored due to phase mismatch");assertClean(server);
            System.out.println("SMOOTHFIX_VERIFY_PASS explicit client stop applies across stage transition");
            step=0;server.getPlayerManager().remove(player);server.stop(false);
        }
    }catch(Throwable t){fail(server,t);}}
    private static void assertClean(MinecraftServer server){for(var key:List.of(ServerBenchmark.ARENA,ServerBenchmark.TERRAIN,ServerBenchmark.LUNAR))for(Entity e:server.getWorld(key).iterateEntities())if(!(e instanceof net.minecraft.entity.player.PlayerEntity))throw new AssertionError("test actors retained in "+key);}
    private void fail(MinecraftServer server,Throwable t){step=0;System.out.println("SMOOTHFIX_VERIFY_FAIL benchmark "+t);t.printStackTrace();server.stop(false);}
    private void accept(Object run,int phase,int action,String data)throws Exception{
        Method receiver=ServerBenchmark.class.getDeclaredMethod("acceptResponse",ServerPlayerEntity.class,UUID.class,int.class,int.class,String.class);receiver.setAccessible(true);
        receiver.invoke(null,player,field(run.getClass(),"id").get(run),phase,action,data);
    }
    private static final class RecordingConnection extends ClientConnection {
        private final List<String> events=new ArrayList<>();
        RecordingConnection(){super(NetworkSide.SERVERBOUND);}
        @Override public void send(Packet<?> packet){record(packet);}
        @Override public void send(Packet<?> packet,PacketCallbacks callbacks){record(packet);}
        private void record(Packet<?> packet){
            if(packet instanceof CustomPayloadS2CPacket payload && payload.getChannel().equals(BenchmarkProtocol.CONTROL)){
                PacketByteBuf data=payload.getData();int offset=data.readerIndex();
                events.add("control:"+data.getUnsignedByte(offset)+":"+data.getInt(offset+17));
            }else if(packet instanceof PlayerRespawnS2CPacket respawn)events.add("respawn:"+respawn.getDimension().getValue());
        }
        void assertControlBeforeRespawn(int phase,String dimension){
            int control=events.lastIndexOf("control:1:"+phase),respawn=events.lastIndexOf("respawn:"+dimension);
            if(control<0 || respawn<0 || control>=respawn)throw new AssertionError("Stage control did not precede dimension respawn: "+events);
            System.out.println("SMOOTHFIX_VERIFY_PASS phase "+phase+" control precedes vanilla respawn packet");
        }
    }
}
