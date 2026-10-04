package org.marj4n.smooth_fix.benchmark;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.diagnostics.*;
import java.util.*;
/** Deterministic route/camera/input driver, using ordinary player movement and attack packets. */
public final class ClientBenchmark {
    private static final Gson GSON=new Gson();
    private static Session active;
    private ClientBenchmark() { }
    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.CONTROL,(client,handler,buf,sender)->{
            int action=buf.readUnsignedByte();UUID run=buf.readUuid();int phase=buf.readInt();String name=buf.readString(),mode=buf.readString(),dimension=buf.readString();double x=buf.readDouble(),y=buf.readDouble(),z=buf.readDouble();
            client.execute(()->{
                if(action==1) {
                    if(active!=null){active.finishStage(client);release(client);}
                    if(ClientFrameProfiler.isActive()){respond(run,phase,0,"manual frame profile is running");return;}
                    active=new Session(run,phase,name,mode,dimension,x,y,z);
                } else if(active!=null && active.run.equals(run)) {
                    if(action==0){active.finishStage(client);release(client);active=null;return;}
                    if(active.phase!=phase)return;
                    if(action==2){if(!active.startup.isReady() || !ClientFrameProfiler.start(client,600)){active.abort(client,"frame profiler could not start");return;}active.measuring=true;}
                    if(action==3){active.x=x;active.y=y;active.z=z;}
                }
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(ClientBenchmark::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(active!=null){active.finishStage(client);release(client);active=null;}});
    }
    public static boolean stop(MinecraftClient client) {
        if(active==null)return false;active.abort(client,"stopped on client",4);return true;
    }
    private static void respond(UUID run,int phase,int action,String data) {
        if(!ClientPlayNetworking.canSend(BenchmarkProtocol.RESPONSE))return;
        var buf=PacketByteBufs.create();buf.writeUuid(run);buf.writeInt(phase);buf.writeByte(action);buf.writeString(data,24000);ClientPlayNetworking.send(BenchmarkProtocol.RESPONSE,buf);
    }
    private static void tick(MinecraftClient client) {
        Session s=active;if(s==null)return;
        long now=System.nanoTime();
        if(now-s.lastHeartbeat>1_000_000_000L){s.lastHeartbeat=now;respond(s.run,s.phase,3,"");}
        boolean worldReady=client.player!=null && client.world!=null
                && client.player.getWorld()==client.world
                && client.world.getRegistryKey().getValue().toString().equals(s.dimension);
        BenchmarkStartupGate.Decision decision=s.startup.update(worldReady,client.currentScreen!=null,client.isWindowFocused());
        if(decision==BenchmarkStartupGate.Decision.WAIT)return;
        if(decision==BenchmarkStartupGate.Decision.ABORT){
            String actual=client.world==null?"no_world":client.world.getRegistryKey().getValue().toString();
            String screen=client.currentScreen==null?"none":client.currentScreen.getClass().getName();
            s.abort(client,"interrupted after readiness: phase="+s.phase+", expected="+s.dimension+", actual="+actual+", screen="+screen+", focused="+client.isWindowFocused()+"; frame results invalidated");return;
        }
        if(decision==BenchmarkStartupGate.Decision.START){s.started=now;respond(s.run,s.phase,1,"");}
        double t=(now-s.started)/1e9;
        // Fixed route, independent of recorded FPS. The server enforces normal movement limits.
        double targetX,targetZ;
        if(s.mode.equals("explore")){targetX=s.x+t*8;targetZ=s.z+Math.sin(t/8)*24;}
        else {double radius=s.mode.equals("combat") || s.mode.equals("effects") || s.mode.equals("bloodmoon")?12:22;targetX=s.x+Math.cos(t*0.25)*radius;targetZ=s.z+Math.sin(t*0.25)*radius;}
        double dx=targetX-client.player.getX(),dz=targetZ-client.player.getZ();
        client.player.setYaw((float)(Math.toDegrees(Math.atan2(dz,dx))-90));client.player.setPitch(s.mode.equals("explore")?20:25);
        client.options.forwardKey.setPressed(dx*dx+dz*dz>2);client.options.sprintKey.setPressed(true);
        client.options.jumpKey.setPressed(client.player.getY()<s.y-0.5);client.options.sneakKey.setPressed(client.player.getY()>s.y+0.5);
        if(s.mode.equals("combat") && client.interactionManager!=null && client.player.getAttackCooldownProgress(0)>=1) {
            MobEntity target=null;double nearest=9;
            for(Entity entity:client.world.getEntities())if(entity instanceof MobEntity mob && mob.isAlive() && !(mob instanceof net.minecraft.entity.passive.IronGolemEntity)) {
                double distance=mob.squaredDistanceTo(client.player);if(distance<nearest){target=mob;nearest=distance;}
            }
            if(target!=null){client.interactionManager.attackEntity(client.player,target);client.player.swingHand(Hand.MAIN_HAND);}
        }
    }
    private static void release(MinecraftClient client) {
        // Restore keys to the actual physical input, including remapped bindings.
        KeyBinding.updatePressedStates();
    }
    private static final class Session {
        final UUID run;final int phase;final String name,mode,dimension;
        final BenchmarkStartupGate startup=new BenchmarkStartupGate();
        double x,y,z;long started,lastHeartbeat;boolean measuring;
        Session(UUID run,int phase,String name,String mode,String dimension,double x,double y,double z){this.run=run;this.phase=phase;this.name=name;this.mode=mode;this.dimension=dimension;this.x=x;this.y=y;this.z=z;}
        void abort(MinecraftClient client,String reason){abort(client,reason,0);}
        void abort(MinecraftClient client,String reason,int action){respond(run,phase,action,reason);finishStage(client);release(client);active=null;if(client.player!=null)client.player.sendMessage(Text.literal("Smooth Fix benchmark stopped: "+reason),false);}
        void finishStage(MinecraftClient client) {
            if(!measuring)return;measuring=false;
            Map<String,Object> report=new LinkedHashMap<>(ClientFrameProfiler.finishNow());report.put("runId",run.toString());report.put("phase",phase);report.put("stage",name);report.put("controller",mode);
            try {
                String file=MemoryReport.write("benchmark_client",report).toString();
                Map<String,Object> summary=new LinkedHashMap<>();summary.put("localReportFile",file);
                for(String key:List.of("elapsedSeconds","frameIntervals","frameWorkBeforePresent","heapBytes","garbageCollectors","garbageCollectorDeltas","memoryAtStart","sceneAtStart","sceneAtEnd","error"))if(report.containsKey(key))summary.put(key,report.get(key));
                String data=GSON.toJson(summary);if(data.length()<=24000)respond(run,phase,2,data);
                SmoothFix.LOGGER.info("Smooth Fix benchmark client phase {} report: {}",phase,file);
            }catch(Exception e){SmoothFix.LOGGER.error("Could not save client benchmark stage",e);}
        }
    }
}
