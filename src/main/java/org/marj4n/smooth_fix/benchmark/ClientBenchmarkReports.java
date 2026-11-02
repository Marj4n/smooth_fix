package org.marj4n.smooth_fix.benchmark;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
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
    private static final Type RESULT_MAP_TYPE = new TypeToken<Map<String,Object>>(){}.getType();
    private static BenchmarkClientReport report;
    private static Map<String,Object> expected,serverExpected;
    private static final Map<UUID,Map<String,Object>> originalClients=new LinkedHashMap<>();
    private static boolean originalClientRequired,originalClientAvailable;
    private static UUID recovering;
    private static long recoveryDeadline;
    private static long nextRecoveryCheck;
    private static boolean installed;
    private ClientBenchmarkReports() { }
    public static void install(){
        if(installed)return;installed=true;
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.BASELINE_REQUEST,(client,handler,buf,sender)->{
            UUID run=buf.readUuid();client.execute(()->{
                Map<String,Object> response=new LinkedHashMap<>();
                try{
                    if(client.player==null||client.world==null||client.interactionManager==null||org.marj4n.smooth_fix.diagnostics.ClientFrameProfiler.isActive())throw new IllegalStateException("Client unavailable or another frame recorder active");
                    var snapshot=RecoverySnapshot.captureLocal(client.player,client.interactionManager.getCurrentGameMode().getId());
                    if(!Boolean.TRUE.equals(snapshot.get("inventoryProjectionAvailable")))throw new IllegalStateException("Native item codec unsupported");
                    originalClients.clear();originalClients.put(run,snapshot);response.put("ready",true);response.put("snapshot",snapshot);response.put("entityRenderDistanceMultiplier",net.minecraft.entity.Entity.getRenderDistanceMultiplier());
                }catch(Exception failure){response.put("ready",false);response.put("error",failure.getClass().getSimpleName());}
                if(ClientPlayNetworking.canSend(BenchmarkProtocol.BASELINE_RESPONSE)){var reply=PacketByteBufs.create();reply.writeUuid(run);reply.writeString(new Gson().toJson(response),24000);ClientPlayNetworking.send(BenchmarkProtocol.BASELINE_RESPONSE,reply);}
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.RUN_RESULT,(client,handler,buf,sender)->{
            String json=buf.readString(24000);client.execute(()->{
                try{
                    JsonObject packet=JsonParser.parseString(json).getAsJsonObject();UUID id=UUID.fromString(packet.get("runId").getAsString());
                    if(report==null||!report.belongsTo(id))return;
                    report.serverResult(new Gson().fromJson(packet,RESULT_MAP_TYPE));
                    if(!packet.has("expectedRecovery"))return;
                    serverExpected=new Gson().fromJson(packet.get("expectedRecovery"),RESULT_MAP_TYPE);
                originalClientRequired=packet.has("originalClientBaselineRequired")&&packet.get("originalClientBaselineRequired").getAsBoolean();
                originalClientAvailable=!originalClientRequired||originalClients.containsKey(id);
                expected=originalClientRequired?originalClients.remove(id):serverExpected;
                if(expected==null)expected=Map.of("requiredOriginalClientSnapshotMissing",true);
                recovering=id;recoveryDeadline=System.nanoTime()+10_000_000_000L;nextRecoveryCheck=0;
                    report.recovery(Map.of("all",false,"status","pending"));
                }catch(Exception e){SmoothFix.LOGGER.error("Final benchmark result could not be stored",e);}
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientBenchmarkReports::verifyRecovery);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{end("disconnected");report=null;expected=null;recovering=null;serverExpected=null;originalClients.clear();});
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
        long now=System.nanoTime();if(now<nextRecoveryCheck)return;nextRecoveryCheck=now+100_000_000L;
        Map<String,Object> result;
        Map<String,Object> actual=Map.of();
        if(client.player==null||client.world==null||client.interactionManager==null)result=new LinkedHashMap<>(Map.of("all",false,"status","player_not_ready"));
        else{
            actual=originalClientRequired?RecoverySnapshot.captureLocal(client.player,client.interactionManager.getCurrentGameMode().getId()):RecoverySnapshot.capture(client.player,client.interactionManager.getCurrentGameMode().getId());
            result=RecoverySnapshot.compare(expected,actual);
            result.put("originalClientBaselineAvailable",originalClientAvailable);if(!originalClientAvailable)result.put("all",false);
            result.put("verificationBasis",originalClientRequired?"same_client_full_original_snapshot_before_server_mutation":"legacy_server_peer_snapshot");
            // Retain cross-peer differences as evidence; they can predate the benchmark.
            if(originalClientRequired&&serverExpected!=null)result.put("serverPeerComparison",RecoverySnapshot.compare(serverExpected,RecoverySnapshot.capture(client.player,client.interactionManager.getCurrentGameMode().getId())));
            result.put("handledScreenClosed",client.currentScreen==null);if(client.currentScreen!=null)result.put("all",false);
        }
        if(!Boolean.TRUE.equals(result.get("all"))&&System.nanoTime()<recoveryDeadline)return;
        result.put("status",Boolean.TRUE.equals(result.get("all"))?"verified":"failed_or_timed_out");
        if(!Boolean.TRUE.equals(result.get("all"))){result.put("expectedSnapshot",expected);result.put("observedSnapshot",actual);}
        try{
            if(report!=null&&report.belongsTo(recovering))report.recovery(result);
            if(ClientPlayNetworking.canSend(BenchmarkProtocol.RUN_ACK)){var b=PacketByteBufs.create();b.writeUuid(recovering);b.writeString(new Gson().toJson(result),24000);ClientPlayNetworking.send(BenchmarkProtocol.RUN_ACK,b);}
        }catch(Exception e){SmoothFix.LOGGER.error("Client recovery verification could not be stored",e);}
        expected=null;recovering=null;
    }
}
