import com.google.gson.*;
import org.marj4n.smooth_fix.benchmark.*;
import java.nio.file.*;
import java.util.*;

/** Behavioral file regression: one path, all stages, failed timeout samples, late server results. */
public final class BatchReportCheck {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("smoothfix-report-check-");UUID id=UUID.randomUUID();
        BenchmarkClientReport report=new BenchmarkClientReport(id,dir,"advanced","1.0.5-test");
        for(int phase=0;phase<21;phase++){
            Map<String,Object> stage=new LinkedHashMap<>();stage.put("phase",phase);stage.put("status",phase==8?"failed_action_timeout":"passed");stage.put("samples",List.of(1,2,3));report.stage(phase,stage);
        }
        report.finish("completed");JsonObject json=JsonParser.parseString(Files.readString(Path.of(report.path()))).getAsJsonObject();
        if(!json.get("status").getAsString().equals("completed_with_failures"))throw new AssertionError("failure masked by completed");
        if(json.getAsJsonArray("stages").size()!=21)throw new AssertionError("stage lost");
        if(json.getAsJsonArray("stages").get(8).getAsJsonObject().getAsJsonArray("samples").size()!=3)throw new AssertionError("timeout discarded samples");
        try(var files=Files.list(dir)){if(files.count()!=1)throw new AssertionError("more than one report per run");}
        Map<String,Object> late=new LinkedHashMap<>();late.put("status","completed_with_failures");late.put("serverStageOutcomes",List.of(Map.of("phase",8,"status","failed_server_workload_verification")));report.serverResult(late);
        report.recovery(Map.of("all",true));json=JsonParser.parseString(Files.readString(Path.of(report.path()))).getAsJsonObject();
        if(!json.getAsJsonObject("clientRecoveryVerification").get("all").getAsBoolean())throw new AssertionError("recovery lost");
        if(!json.getAsJsonArray("stages").get(8).getAsJsonObject().get("status").getAsString().equals("failed_server_workload_verification"))throw new AssertionError("late outcome lost");
        if(!BenchmarkOutcome.status(List.of(Map.of("status","measured_pending_client_report"))).equals("completed_pending_client_reports"))throw new AssertionError("missing client passed");
        if(!BenchmarkOutcome.status(List.of(Map.of("status","skipped_mod_unavailable"))).equals("completed_with_skips"))throw new AssertionError("skip passed");
        System.out.println("PASS one client file for 21 stage checkpoints; failure aggregation; retained timeout samples; late server outcome and recovery update; pending/skipped never silently pass");
    }
}
