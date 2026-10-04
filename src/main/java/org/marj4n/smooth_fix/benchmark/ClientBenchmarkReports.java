package org.marj4n.smooth_fix.benchmark;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.marj4n.smooth_fix.SmoothFix;
import java.util.*;
import java.io.IOException;

/** Shared writer for legacy and advanced controllers; a failed/partial stage retains its samples. */
public final class ClientBenchmarkReports {
    private static BenchmarkClientReport report;
    private static Map<String,Object> expected;
    private static UUID recovering;
    private static long recoveryDeadline;
    private static boolean installed;
    private ClientBenchmarkReports() { }
    public static void install(){
        if(installed)return;installed=true;
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.RUN_RESULT,(client,handler,buf,sender)->{
            String json=buf.readString(24000);client.execute(()->{
                try{
                    JsonObject packet=JsonParser.parseString(json).getAsJsonObject();UUID id=UUID.fromString(packet.get("runId").getAsString());
                    if(report==null||!report.belongsTo(id))return;
                    report.serverResult(new Gson().fromJson(packet,Map.class));
                    if(!packet.has("expectedRecovery"))return;
                    expected=new Gson().fromJson(packet.get("expectedRecovery"),Map.class);recovering=id;recoveryDeadline=System.nanoTime()+10_000_000_000L;
                    report.recovery(Map.of("all",false,"status","pending"));
                }catch(Exception e){SmoothFix.LOGGER.error("Final benchmark result could not be stored",e);}
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientBenchmarkReports::verifyRecovery);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{end("disconnected");expected=null;recovering=null;});
    }
    public static void begin(UUID run,String suite)throws IOException{
        if(report!=null&&report.belongsTo(run))return;
        if(report!=null&&!report.isFinished())report.finish("superseded_by_new_run");
        expected=null;recovering=null;
        report=new BenchmarkClientReport(run,FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/reports"),suite,SmoothFix.BUILD_ID);
    }
    public static String stage(UUID run,int phase,Map<String,Object> stage)throws IOException{
        if(report==null||!report.belongsTo(run))begin(run,"benchmark");
        if(!stage.containsKey("status")){
            String error=String.valueOf(stage.getOrDefault("error",""));
            stage.put("status",error.startsWith("skipped:")?"skipped_client_adapter":Boolean.FALSE.equals(stage.get("workloadValidated"))||stage.get("error")!=null?"failed_workload_verification":"passed");
        }
        report.stage(phase,stage);return report.path();
    }
    public static void end(String reason){if(report!=null&&!report.isFinished())try{report.finish(reason);}catch(IOException e){SmoothFix.LOGGER.error("Benchmark run checkpoint failed",e);}}
    private static void verifyRecovery(MinecraftClient client){
        if(expected==null||recovering==null)return;
        Map<String,Object> result;
        if(client.player==null||client.world==null||client.interactionManager==null)result=new LinkedHashMap<>(Map.of("all",false,"status","player_not_ready"));
        else{
            result=RecoverySnapshot.compare(expected,RecoverySnapshot.capture(client.player,client.interactionManager.getCurrentGameMode().getId()));
            result.put("handledScreenClosed",client.currentScreen==null);if(client.currentScreen!=null)result.put("all",false);
        }
        if(!Boolean.TRUE.equals(result.get("all"))&&System.nanoTime()<recoveryDeadline)return;
        result.put("status",Boolean.TRUE.equals(result.get("all"))?"verified":"failed_or_timed_out");
        try{
            if(report!=null&&report.belongsTo(recovering))report.recovery(result);
            if(ClientPlayNetworking.canSend(BenchmarkProtocol.RUN_ACK)){var b=PacketByteBufs.create();b.writeUuid(recovering);b.writeString(new Gson().toJson(result),24000);ClientPlayNetworking.send(BenchmarkProtocol.RUN_ACK,b);}
        }catch(Exception e){SmoothFix.LOGGER.error("Client recovery verification could not be stored",e);}
        expected=null;recovering=null;
    }
}
