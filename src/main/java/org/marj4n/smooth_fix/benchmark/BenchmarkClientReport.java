package org.marj4n.smooth_fix.benchmark;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One durable client report per run. Stage checkpoints replace the same file atomically. */
public final class BenchmarkClientReport {
    private final UUID run;
    private final Path file;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final SortedMap<Integer, Map<String, Object>> stages = new TreeMap<>();

    public BenchmarkClientReport(UUID run, Path directory, String suite, String build) throws IOException {
        this.run = run;
        file = directory.resolve("benchmark_client_" + run + ".json");
        report.put("schemaVersion", 2);
        report.put("runId", run.toString());
        report.put("suite", suite);
        report.put("build", build);
        report.put("status", "running");
        report.put("startedUtc", java.time.Instant.now().toString());
        checkpoint();
    }

    public boolean belongsTo(UUID id) { return run.equals(id); }
    public String path() { return file.toAbsolutePath().toString(); }
    public boolean isFinished() { return report.containsKey("finishedUtc"); }

    public void stage(int phase, Map<String, Object> stage) throws IOException {
        stages.put(phase, new LinkedHashMap<>(stage));
        checkpoint();
    }

    public void finish(String reason) throws IOException {
        report.put("terminationReason", reason);
        report.put("status", reason.equals("completed") ? BenchmarkOutcome.status(new ArrayList<>(stages.values())) : reason);
        report.put("finishedUtc", java.time.Instant.now().toString());
        report.put("summary", BenchmarkOutcome.counts(new ArrayList<>(stages.values())));
        checkpoint();
    }

    public void serverResult(Map<String,Object> server) throws IOException {
        report.put("serverStatus",server.get("status"));report.put("serverSummary",server.get("serverSummary"));
        if(server.get("serverStageOutcomes") instanceof List<?> outcomes)for(Object entry:outcomes)if(entry instanceof Map<?,?> outcome && outcome.get("phase") instanceof Number phase){Map<String,Object> stage=stages.get(phase.intValue());if(stage!=null){stage.putIfAbsent("clientStatus",stage.get("status"));for(String key:List.of("status","serverWorkloadValidation","actionWindowSeconds"))if(outcome.containsKey(key))stage.put(key,outcome.get(key));}}
        finish(String.valueOf(server.get("status")));
    }
    public void recovery(Map<String, Object> recovery) throws IOException {
        report.put("clientRecoveryVerification", recovery);
        checkpoint();
    }

    private void checkpoint() throws IOException {
        report.put("stages", new ArrayList<>(stages.values()));
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report), StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
