package org.marj4n.smooth_fix.diagnostics;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.config.SmoothFixConfig;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.util.Util;

/** On-demand JVM measurements. No forced GC, heap dump, launch args, or authentication tokens. */
public final class MemoryReport {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
            .withZone(ZoneOffset.UTC);

    private MemoryReport() { }

    public static Map<String, Object> snapshot(String side) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timeUtc", Instant.now().toString());
        report.put("side", side);
        report.put("pid", ProcessHandle.current().pid());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("smoothFixBuild", org.marj4n.smooth_fix.SmoothFix.BUILD_ID);
        SmoothFixConfig config = SmoothFixConfig.get();
        report.put("performanceOptions", Map.of("backgroundWorkerLimit",config.backgroundWorkerLimit,
                "modelRotationAllocationFix",config.modelRotationAllocationFix,
                "confirmedTeleportAnchorFix",config.confirmedTeleportAnchorFix,
                "trinketsSnapshotAllocationFix",config.trinketsSnapshotAllocationFix,
                "accessoriesEmptyBufferFlushFix",config.accessoriesEmptyBufferFlushFix,
                "emiSearchSafetyFix",config.emiSearchSafetyFix));
        report.put("heapBytes", usage(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()));
        report.put("nonHeapBytes", usage(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()));
        List<Map<String, Object>> pools = new ArrayList<>();
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            pools.add(Map.of("name", pool.getName(), "count", pool.getCount(),
                    "memoryUsedBytes", pool.getMemoryUsed(), "totalCapacityBytes", pool.getTotalCapacity()));
        }
        report.put("bufferPools", pools);
        report.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        if (Util.getMainWorkerExecutor() instanceof ForkJoinPool pool) {
            report.put("mainBackgroundPool", Map.of("targetParallelism",pool.getParallelism(),"poolSize",pool.getPoolSize(),
                    "activeThreads",pool.getActiveThreadCount(),"runningThreads",pool.getRunningThreadCount(),
                    "queuedSubmissions",pool.getQueuedSubmissionCount(),"queuedTasks",pool.getQueuedTaskCount()));
        }
        List<Map<String, Object>> collectors = new ArrayList<>();
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> collectors.add(
                Map.of("name", gc.getName(), "collections", gc.getCollectionCount(), "timeMs", gc.getCollectionTime())));
        report.put("garbageCollectors", collectors);
        report.put("totalResidentProcessBytes", null);
        report.put("memoryNote", "Heap, non-heap and buffer pools do not account for all native allocations. "
                + "Buffer pools can overlap; do not add these fields to estimate Task Manager memory. "
                + "Compare the PID with Task Manager Working Set at the same time.");
        return report;
    }

    public static void addCollectorDeltas(Map<String, Object> end, Map<String, Object> start) {
        List<Map<String, Object>> deltas = new ArrayList<>();
        if (end.get("garbageCollectors") instanceof List<?> after && start.get("garbageCollectors") instanceof List<?> before) {
            for (Object value : after) if (value instanceof Map<?, ?> current) {
                for (Object previous : before) if (previous instanceof Map<?, ?> original
                        && current.get("name").equals(original.get("name"))) {
                    Map<String, Object> delta = new LinkedHashMap<>();
                    delta.put("name", current.get("name"));
                    delta.put("collections", counterDelta(current.get("collections"), original.get("collections")));
                    delta.put("timeMs", counterDelta(current.get("timeMs"), original.get("timeMs")));
                    deltas.add(delta);
                    break;
                }
            }
        }
        end.put("garbageCollectorDeltas", deltas);
        end.put("garbageCollectorDeltaNote", "Differences over this recording only. Collector time is not a pause timeline; concurrent collector time must not be summed as stop-the-world duration. Null means unsupported/reset counter.");
    }

    private static Long counterDelta(Object after, Object before) {
        if (!(after instanceof Number end) || !(before instanceof Number start)) return null;
        long a = end.longValue(), b = start.longValue();
        return a < 0 || b < 0 || a < b ? null : a - b;
    }

    private static Map<String, Long> usage(MemoryUsage usage) {
        return Map.of("used", usage.getUsed(), "committed", usage.getCommitted(), "max", usage.getMax());
    }

    public static Path write(String kind, Map<String, Object> report) throws IOException {
        Path folder = FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/reports");
        Files.createDirectories(folder);
        Path file = folder.resolve(kind + "_" + FILE_TIME.format(Instant.now()) + ".json");
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report),
                StandardCharsets.UTF_8);
        return file.toAbsolutePath();
    }
}
