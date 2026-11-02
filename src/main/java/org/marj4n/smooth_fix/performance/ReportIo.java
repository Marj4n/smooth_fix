package org.marj4n.smooth_fix.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

/** Bounded I/O for Smooth Fix diagnostic documents; never used for Minecraft world saves. */
public final class ReportIo {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static volatile BoundedReportQueue queue;
    private ReportIo() {}
    private static synchronized BoundedReportQueue queue() {
        if(queue==null) {
            var config=SmoothFixConfig.get();
            queue=new BoundedReportQueue(Math.max(1,Math.min(16,config.reportQueueCapacity)),
                    Math.max(1,Math.min(128,config.reportQueueMiB))*1024L*1024,
                    Math.max(0,Math.min(1000,config.reportMaximumDeferralMillis))*1_000_000L,
                    config.adaptiveReportScheduling?8_333_333L:0,ReportIo::writeAtomic);
        }
        return queue;
    }
    public static void checkpoint(Path path,Map<String,Object> report,boolean durable)throws IOException {
        if(!SmoothFixConfig.get().memoryOptimization || !SmoothFixConfig.get().backgroundReportIo){writeAtomic(path,report);return;}
        var ownedQueue=queue();
        CompletableFuture<Void> future;
        try { future=ownedQueue.submit(path,report,durable,5000); }
        catch(BoundedReportQueue.SnapshotLimitException oversized) {
            // Preserve complete diagnostics without allowing an unbounded retained queue.
            // All callers own the report on the client/server thread; drain older writes
            // before synchronous replacement so an older snapshot cannot overwrite this one.
            ownedQueue.awaitIdle(5000);
            writeAtomic(path,report);
            return;
        }
        future.whenComplete((ignored,error)->{if(error!=null)SmoothFix.LOGGER.error("Diagnostic report write failed for {}",path,error);});
        if(durable)try {future.get(5,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Interrupted report checkpoint",e);}
        catch(ExecutionException|TimeoutException e){throw new IOException("Report checkpoint could not become durable",e);}
    }
    public static void observeFrame(long nanos){var active=queue;if(active!=null)active.observeFrame(nanos);}
    public static boolean active(){return queue!=null && SmoothFixConfig.get().adaptiveReportScheduling;}
    public static Map<String,Long> metrics(){var active=queue;return active==null?Map.of():active.metrics();}
    public static void drain(){var active=queue;if(active!=null)try{active.awaitIdle(5000);}catch(IOException e){SmoothFix.LOGGER.error("Diagnostic queue drain failed",e);}}
    public static void shutdown(){var active=queue;if(active!=null)try{active.close(5000);}catch(IOException e){SmoothFix.LOGGER.error("Diagnostic queue shutdown failed",e);}}
    private static void writeAtomic(Path file,Object snapshot)throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temporary,GSON.toJson(snapshot),StandardCharsets.UTF_8);
        AtomicFilePublication.publish(temporary,file);
    }
}
