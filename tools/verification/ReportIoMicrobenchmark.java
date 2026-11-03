import com.google.gson.*;
import org.marj4n.smooth_fix.performance.BoundedReportQueue;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;

public class ReportIoMicrobenchmark {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final com.sun.management.ThreadMXBean ALLOC =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long writes, bytes;

    static void write(Path path, Object value) throws java.io.IOException {
        String data = JSON.toJson(value);
        byte[] encoded = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path, encoded);
        writes++;
        bytes += encoded.length;
    }

    static Map<String, Object> run(Path dir, boolean async) throws Exception {
        writes = bytes = 0;
        long id = Thread.currentThread().threadId();
        long allocated = ALLOC.getThreadAllocatedBytes(id), start = System.nanoTime();
        var q =
                async
                        ? new BoundedReportQueue(
                                4, 32 * 1024 * 1024, 0, 0, ReportIoMicrobenchmark::write)
                        : null;
        var report = new LinkedHashMap<String, Object>();
        var stages = new ArrayList<Object>();
        report.put("stages", stages);
        long caller = 0;
        Path file = dir.resolve(async ? "candidate.json" : "baseline.json");
        for (int i = 0; i < 100; i++) {
            stages.add(
                    Map.of(
                            "stage",
                            i,
                            "frames",
                            Collections.nCopies(100, 1234567L),
                            "notes",
                            "Fixed synthetic diagnostic payload"));
            long t = System.nanoTime();
            if (async) q.submit(file, report, false, 5000);
            else write(file, report);
            caller += System.nanoTime() - t;
        }
        long callerAllocated = ALLOC.getThreadAllocatedBytes(id) - allocated;
        if (async) {
            q.awaitIdle(10000);
            q.close(1000);
        }
        long elapsed = System.nanoTime() - start;
        if (!JsonParser.parseString(Files.readString(file)).equals(JSON.toJsonTree(report)))
            throw new AssertionError("Final JSON differs");
        return Map.of(
                "mode",
                async ? "bounded_snapshot_queue" : "synchronous_pretty_json",
                "checkpoints",
                100,
                "caller_nanos",
                caller,
                "caller_allocated_bytes",
                callerAllocated,
                "elapsed_nanos",
                elapsed,
                "writes",
                writes,
                "bytes_written",
                bytes,
                "final_json_equal",
                true);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        var result = new LinkedHashMap<String, Object>();
        result.put(
                "scope",
                "Synthetic diagnostics-only microbenchmark; not gameplay FPS/TPS or full report class A/B. Caller allocation excludes worker. Warmup then 5 alternating paired trials.");
        run(dir, false);
        run(dir, true);
        var trials = new ArrayList<Object>();
        for (int i = 0; i < 5; i++) {
            trials.add(run(dir, false));
            trials.add(run(dir, true));
        }
        result.put("trials", trials);
        Files.writeString(dir.resolve("microbenchmark.json"), JSON.toJson(result));
        System.out.println(
                "PASS 10 measured trials; all final JSON matched; see microbenchmark.json");
    }
}
