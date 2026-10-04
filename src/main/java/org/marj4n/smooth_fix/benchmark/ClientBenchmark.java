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
    public static boolean controlsMovement(){return active!=null;}
    public static Boolean controlledKey(KeyBinding key){
        Session s=active;if(s==null || s.forward==null)return null;
        if(key==s.forward)return s.input.forward();if(key==s.jump)return s.input.jump();if(key==s.sprint)return s.input.sprint();if(key==s.sneak)return s.descend;
        if(key==s.back || key==s.left || key==s.right || key==s.attack || key==s.use)return false;return null;
    }
    public static void install() {
        ClientBenchmarkReports.install();
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.CONTROL,(client,handler,buf,sender)->{
            int action=buf.readUnsignedByte();UUID run=buf.readUuid();int phase=buf.readInt();String name=buf.readString(),mode=buf.readString(),dimension=buf.readString();double x=buf.readDouble(),y=buf.readDouble(),z=buf.readDouble();
            int total=buf.readInt(),cycle=buf.readInt();boolean soak=buf.readBoolean();String next=buf.readString();double actionElapsed=buf.readDouble();
            client.execute(()->{
                if(action==1) {
                    if(active!=null){active.finishStage(client);release(client);}
                    if(ClientFrameProfiler.isActive()){respond(run,phase,0,"manual frame profile is running");return;}
                    try{ClientBenchmarkReports.begin(run,"legacy");}catch(Exception e){respond(run,phase,0,"run report could not start: "+e);return;}
                    active=new Session(run,phase,name,mode,dimension,x,y,z);active.bind(client);active.total=total;active.cycle=cycle;active.soak=soak;active.next=next;
                } else if(active!=null && active.run.equals(run)) {
                    if(action==0){active.finishStage(client);release(client);active=null;ClientBenchmarkReports.end("awaiting_server_result");return;}
                    if(active.phase!=phase)return;
                    if(action==2){if(!active.startup.isReady() || !ClientFrameProfiler.startBenchmark(client,600)){active.abort(client,"frame profiler could not start");return;}active.measuring=true;active.measuringAt=System.nanoTime();}
                    if(action==3 || action==8){active.x=x;active.y=y;active.z=z;}
                    if(action==7)active.recovering=true;
                    if(action==8){active.recovering=false;active.resumeElapsed=actionElapsed;active.startup=new BenchmarkStartupGate();if(client.player!=null && client.player.isAlive() && client.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen)client.setScreen(null);}
                }
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(ClientBenchmark::tick);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context,delta)->{Session s=active;if(s!=null)s.hud.render(context);});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(active!=null){active.finishStage(client);release(client);active=null;}ClientBenchmarkReports.end("disconnected");});
    }
    public static boolean stop(MinecraftClient client) {
        if(AdvancedClient.stop(client))return true;
        if(active==null)return false;active.abort(client,"stopped on client",4);return true;
    }
    private static void respond(UUID run,int phase,int action,String data) {
        if(!ClientPlayNetworking.canSend(BenchmarkProtocol.RESPONSE))return;
        var buf=PacketByteBufs.create();buf.writeUuid(run);buf.writeInt(phase);buf.writeByte(action);buf.writeString(data,24000);ClientPlayNetworking.send(BenchmarkProtocol.RESPONSE,buf);
    }
    private static void tick(MinecraftClient client) {
        Session s=active;if(s==null)return;
        long now=System.nanoTime();s.input=GroundNavigator.Input.NONE;s.descend=false;
        String state=s.recovering?"Automatic respawn; waiting for the player":!s.startup.isReady()?"Waiting for the world, closed screens and window focus":s.measuring?"Stress: "+Math.max(0,60-(int)((now-s.measuringAt)/1e9))+" seconds remaining":"Warm-up: "+Math.max(0,10-(int)((now-s.started)/1e9))+" seconds remaining";
        s.hud.update(client,now,List.of("Smooth Fix Stress "+(s.phase%s.total+1)+"/"+s.total+" : "+s.name+" | cycle "+(s.cycle+1),AdvancedStageInfo.legacyTask(s.name,s.mode),state,"Next: "+s.next+" | /smoothfixc stopstress"));
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen || client.player!=null && !client.player.isAlive()){s.recovering=true;return;}
        if(s.recovering)return;
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
        if(decision==BenchmarkStartupGate.Decision.START){s.started=now-(long)(s.resumeElapsed*1e9);if(s.measuring)s.measuringAt=now-(long)(s.resumeElapsed*1e9);respond(s.run,s.phase,1,"");}
        double t=(now-s.started)/1e9;
        // Fixed route, independent of recorded FPS. The server enforces normal movement limits.
        double targetX,targetZ;
        if(s.mode.equals("explore")){targetX=s.x+t*8;targetZ=s.z+Math.sin(t/8)*24;}
        else {double radius=s.mode.equals("combat") || s.mode.equals("effects") || s.mode.equals("bloodmoon")?12:22;targetX=s.x+Math.cos(t*0.25)*radius;targetZ=s.z+Math.sin(t*0.25)*radius;}
        double dx=targetX-client.player.getX(),dz=targetZ-client.player.getZ();
        client.player.setYaw((float)(Math.toDegrees(Math.atan2(dz,dx))-90));client.player.setPitch(s.mode.equals("explore")?20:25);
        s.input=new GroundNavigator.Input(dx*dx+dz*dz>2,client.player.getY()<s.y-.5,true);s.descend=client.player.getY()>s.y+.5;
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
        if(client.player!=null)client.player.setSprinting(false);
    }
    private static final class Session {
        final UUID run;final int phase;final String name,mode,dimension;
        BenchmarkStartupGate startup=new BenchmarkStartupGate();final BenchmarkHud hud=new BenchmarkHud();
        GroundNavigator.Input input=GroundNavigator.Input.NONE;boolean descend,recovering,soak;double resumeElapsed;int total=1,cycle;String next="Finish and restore player";long measuringAt;
        KeyBinding forward,back,left,right,jump,sneak,sprint,attack,use;
        void bind(MinecraftClient client){var o=client.options;forward=o.forwardKey;back=o.backKey;left=o.leftKey;right=o.rightKey;jump=o.jumpKey;sneak=o.sneakKey;sprint=o.sprintKey;attack=o.attackKey;use=o.useKey;}
        double x,y,z;long started,lastHeartbeat;boolean measuring;
        Session(UUID run,int phase,String name,String mode,String dimension,double x,double y,double z){this.run=run;this.phase=phase;this.name=name;this.mode=mode;this.dimension=dimension;this.x=x;this.y=y;this.z=z;}
        void abort(MinecraftClient client,String reason){abort(client,reason,0);}
        void abort(MinecraftClient client,String reason,int action){respond(run,phase,action,reason);finishStage(client);release(client);active=null;ClientBenchmarkReports.end(reason);if(client.player!=null)client.player.sendMessage(Text.literal("Smooth Fix benchmark stopped: "+reason),false);}
        void finishStage(MinecraftClient client) {
            if(!measuring)return;measuring=false;
            Map<String,Object> report=new LinkedHashMap<>(ClientFrameProfiler.finishNow());report.put("runId",run.toString());report.put("phase",phase);report.put("stage",name);report.put("controller",mode);
            try {
                String file=ClientBenchmarkReports.stage(run,phase,report);
                Map<String,Object> summary=new LinkedHashMap<>();summary.put("localReportFile",file);
                for(String key:List.of("elapsedSeconds","frameIntervals","frameWorkBeforePresent","heapBytes","garbageCollectors","garbageCollectorDeltas","memoryAtStart","sceneAtStart","sceneAtEnd","error"))if(report.containsKey(key))summary.put(key,report.get(key));
                String data=GSON.toJson(summary);if(data.length()<=24000)respond(run,phase,2,data);
                SmoothFix.LOGGER.info("Smooth Fix benchmark client phase {} report: {}",phase,file);
            }catch(Exception e){SmoothFix.LOGGER.error("Could not save client benchmark stage",e);}
        }
    }
}
