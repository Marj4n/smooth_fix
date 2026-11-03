import org.marj4n.smooth_fix.performance.BoundedReportQueue;
import org.marj4n.smooth_fix.diagnostics.TimingSamples;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Behavioral tests of the actual production queue, without a Minecraft bootstrap. */
public final class OwnedReportQueueCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("smoothfix-report-queue-");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<Object> written = new CopyOnWriteArrayList<>();
        var queue =
                new BoundedReportQueue(
                        2,
                        1024 * 1024,
                        0,
                        0,
                        (path, value) -> {
                            if (path.getFileName().toString().equals("block")) {
                                entered.countDown();
                                try {
                                    release.await();
                                } catch (InterruptedException e) {
                                    throw new IOException(e);
                                }
                            }
                            written.add(value);
                            Files.writeString(path, value.toString());
                        });
        var first = queue.submit(dir.resolve("block"), Map.of("v", 0), true, 1000);
        check(entered.await(1, TimeUnit.SECONDS), "real writer did not start");
        Map<String, Object> source = new LinkedHashMap<>();
        List<Object> nested = new ArrayList<>(List.of(7));
        source.put("nested", nested);
        var immutable = queue.submit(dir.resolve("immutable"), source, false, 1000);
        nested.set(0, 99);
        source.put("after", true);
        var older = queue.submit(dir.resolve("coalesced"), Map.of("stage", 1), false, 1000);
        var latest = queue.submit(dir.resolve("coalesced"), Map.of("stage", 100), true, 1000);
        check(older == latest, "same-file checkpoints did not share durable completion");
        check(queue.metrics().get("pending") == 2, "queue exceeded expected distinct files");
        boolean timedOut = false;
        try {
            queue.submit(dir.resolve("overflow"), Map.of("v", 1), false, 10);
        } catch (IOException e) {
            timedOut = true;
        }
        check(timedOut, "full queue silently accepted unbounded work");
        release.countDown();
        first.get(1, TimeUnit.SECONDS);
        immutable.get(1, TimeUnit.SECONDS);
        latest.get(1, TimeUnit.SECONDS);
        queue.awaitIdle(1000);
        check(
                Files.readString(dir.resolve("immutable")).equals("{nested=[7]}"),
                "mutable producer state escaped snapshot");
        check(
                Files.readString(dir.resolve("coalesced")).equals("{stage=100}"),
                "latest checkpoint missing or overwritten by stale write");
        check(written.size() == 3, "coalescing failed to eliminate redundant write");
        check(queue.metrics().get("coalesced") == 1, "coalescing counter wrong");
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        boolean rejected = false;
        try {
            queue.submit(dir.resolve("cycle"), cyclic, false, 100);
        } catch (IOException e) {
            rejected = true;
        }
        check(rejected, "cycle accepted");
        rejected = false;
        try {
            queue.submit(dir.resolve("mutable"), Map.of("world", new Object()), false, 100);
        } catch (IOException e) {
            rejected = true;
        }
        check(rejected, "arbitrary mutable world-like object accepted");
        queue.close(1000);
        rejected = false;
        try {
            queue.submit(dir.resolve("closed"), Map.of(), false, 100);
        } catch (IOException e) {
            rejected = true;
        }
        check(rejected, "shutdown admitted work");
        AtomicInteger attempts = new AtomicInteger();
        var failure =
                new BoundedReportQueue(
                        1,
                        4096,
                        0,
                        0,
                        (path, value) -> {
                            attempts.incrementAndGet();
                            throw new IOException("intentional disk failure");
                        });
        boolean propagated = false;
        try {
            failure.submit(dir.resolve("failure"), Map.of("v", 1), true, 1000)
                    .get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            propagated = e.getCause() instanceof IOException;
        }
        check(propagated, "I/O error masked as success");
        check(failure.metrics().get("failed") == 1, "I/O failure not counted");
        failure.close(1000);
        AtomicLong executed = new AtomicLong();
        var adaptive =
                new BoundedReportQueue(
                        2,
                        4096,
                        60_000_000,
                        1_000_000,
                        (path, value) -> executed.set(System.nanoTime()));
        adaptive.observeFrame(100_000_000);
        long start = System.nanoTime();
        adaptive.submit(dir.resolve("deferred"), Map.of("v", 1), false, 1000)
                .get(2, TimeUnit.SECONDS);
        check(executed.get() - start >= 30_000_000, "background pressure never deferred");
        check(executed.get() - start < 1_000_000_000, "background task starved");
        check(adaptive.metrics().get("pressureDeferrals") > 0, "deferral not measured");
        adaptive.observeFrame(100_000_000);
        start = System.nanoTime();
        adaptive.submit(dir.resolve("urgent"), Map.of("v", 2), true, 1000).get(1, TimeUnit.SECONDS);
        check(executed.get() - start < 500_000_000, "urgent report blocked on pressure");
        adaptive.close(1000);
        var bound = new BoundedReportQueue(1, 256, 0, 0, (path, value) -> {});
        rejected = false;
        try {
            bound.submit(dir.resolve("huge"), Map.of("v", "x".repeat(1024)), false, 100);
        } catch (IOException e) {
            rejected = true;
        }
        check(rejected, "snapshot byte bound ignored");
        bound.close(1000);
        var stats = new TimingSamples(10_000_000);
        for (int i = 1; i <= 100; i++) stats.add(i * 1_000_000L);
        Map<String, Object> distribution = stats.snapshot();
        Map<String, Object> fps = TimingSamples.frameRates(distribution);
        check(((Number) distribution.get("p50Ms")).doubleValue() == 50, "median changed");
        check(((Number) distribution.get("p999Ms")).doubleValue() == 100, "p99.9 wrong");
        check(
                ((Number) fps.get("onePercentLowFps")).doubleValue() == 10,
                "1% low is not slowest mean");
        check(
                Math.abs(((Number) fps.get("averageFps")).doubleValue() - 1000 / 50.5) < 1e-9,
                "average FPS wrong");
        check(
                TimingSamples.frameRates(new TimingSamples(1).snapshot()).get("averageFps") == null,
                "empty data invented FPS");
        System.out.println(
                "PASS "
                        + checks
                        + " production queue/statistics checks: immutable snapshots, bounded admission, coalescing, durable latest document, I/O errors, lifecycle, frame pressure/starvation and FPS definitions");
    }
}
