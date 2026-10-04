package org.marj4n.smooth_fix.benchmark;

import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import org.marj4n.smooth_fix.SmoothFix;
import java.util.*;

/** Final peer acknowledgement is separate from verified server recovery. */
public final class BenchmarkFinalization {
    private record Pending(UUID owner,Map<String,Object> report,Runnable checkpoint,long expires) { }
    private static final Map<UUID,Pending> pending=new LinkedHashMap<>();
    private BenchmarkFinalization() { }
    public static void install(){
        ServerPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.RUN_ACK,(server,player,handler,buf,sender)->{
            UUID run=buf.readUuid();String data=buf.readString(24000);server.execute(()->{
                Pending p=pending.get(run);if(p==null||!p.owner.equals(player.getUuid()))return;pending.remove(run);
                try{JsonObject verification=JsonParser.parseString(data).getAsJsonObject();p.report.put("clientRecoveryVerification",verification);p.report.put("recoveryVerifiedBothSides",Boolean.TRUE.equals(p.report.get("playerRestored"))&&verification.has("all")&&verification.get("all").getAsBoolean());p.checkpoint.run();}catch(Exception e){SmoothFix.LOGGER.warn("Final recovery acknowledgement rejected",e);}
            });
        });
    }
    private static List<Map<String,Object>> stageOutcomes(Map<String,Object> report){
        List<Map<String,Object>> out=new ArrayList<>();
        if(report.get("stages") instanceof List<?> stages)for(Object entry:stages)if(entry instanceof Map<?,?> stage){Map<String,Object> data=new LinkedHashMap<>();for(String key:List.of("phase","status","serverWorkloadValidation","actionWindowSeconds"))if(stage.containsKey(key))data.put(key,stage.get(key));out.add(data);}
        return out;
    }
    public static void update(ServerPlayerEntity player,UUID run,Map<String,Object> report){
        if(player==null || !ServerPlayNetworking.canSend(player,BenchmarkProtocol.RUN_RESULT))return;
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("runId",run.toString());packet.put("status",report.get("status"));packet.put("serverSummary",report.get("summary"));packet.put("serverStageOutcomes",stageOutcomes(report));var buf=PacketByteBufs.create();buf.writeString(new Gson().toJson(packet),24000);ServerPlayNetworking.send(player,BenchmarkProtocol.RUN_RESULT,buf);
    }
    public static void send(ServerPlayerEntity player,UUID run,Map<String,Object> report,Runnable checkpoint){
        pending.entrySet().removeIf(e->e.getValue().expires<System.nanoTime());
        report.put("recoveryVerifiedBothSides",false);
        if(player==null || !ServerPlayNetworking.canSend(player,BenchmarkProtocol.RUN_RESULT)){report.put("clientRecoveryVerification",Map.of("all",false,"status","unavailable_disconnect_or_protocol"));return;}
        report.put("clientRecoveryVerification",Map.of("all",false,"status","pending"));
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("runId",run.toString());packet.put("status",report.get("status"));packet.put("terminationReason",report.getOrDefault("terminationReason",report.get("status")));packet.put("serverSummary",report.getOrDefault("summary",Map.of()));packet.put("serverStageOutcomes",stageOutcomes(report));packet.put("expectedRecovery",RecoverySnapshot.capture(player,player.interactionManager.getGameMode().getId()));
        var buf=PacketByteBufs.create();buf.writeString(new Gson().toJson(packet),24000);ServerPlayNetworking.send(player,BenchmarkProtocol.RUN_RESULT,buf);
        if(pending.size()>=8)pending.remove(pending.keySet().iterator().next());pending.put(run,new Pending(player.getUuid(),report,checkpoint,System.nanoTime()+120_000_000_000L));
    }
}
