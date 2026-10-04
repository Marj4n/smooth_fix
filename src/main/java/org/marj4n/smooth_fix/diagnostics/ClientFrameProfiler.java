package org.marj4n.smooth_fix.diagnostics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.marj4n.smooth_fix.SmoothFix;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/** No active recorder, timing arrays or sampler thread during normal gameplay. */
public final class ClientFrameProfiler {
    private static volatile Recording active;
    private ClientFrameProfiler() { }

    public static boolean start(MinecraftClient client, int seconds) {
        if (active != null || client.world == null) return false;
        return start(client, seconds, true);
    }

    /** Benchmark controllers own completion and consume the retained recording, including timeout data. */
    public static boolean startBenchmark(MinecraftClient client, int seconds) {
        return start(client, seconds, false);
    }

    private static boolean start(MinecraftClient client, int seconds, boolean automaticSave) {
        if (active != null || client.world == null) return false;
        Recording recording = new Recording(client, seconds, automaticSave);
        active = recording;
        Thread thread = new Thread(recording, "Smooth Fix Frame Sampler");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public static void beginFrame() {
        Recording r = active;
        if (r == null) return;
        long now = System.nanoTime();
        if (r.previousStart != 0){r.intervals.add(now - r.previousStart);r.timeline.add(now-r.started,now-r.previousStart,false);}
        r.previousStart = now;
        r.frameStart = now;
        r.working = true;
    }

    public static void endWork() {
        Recording r = active;
        if (r == null || !r.working) return;
        long elapsed = System.nanoTime() - r.frameStart;
        r.working = false;
        r.work.add(elapsed);
        r.timeline.add(System.nanoTime()-r.started,elapsed,true);
    }

    public static boolean isActive() { return active != null; }
    public static void stop() { Recording r = active; if (r != null) r.stop = true; }
    /** Called on the render thread by the benchmark; stops and returns exactly one recording. */
    public static Map<String,Object> finishNow() {
        Recording r = active;
        return r == null ? Map.of("error", "no active recording") : r.finish(false);
    }

    private static Map<String, Object> scene(MinecraftClient client) {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("renderDistance", client.options.getViewDistance().getValue());
        scene.put("simulationDistance", client.options.getSimulationDistance().getValue());
        scene.put("fpsLimit", client.options.getMaxFps().getValue());
        scene.put("vsync", client.options.getEnableVsync().getValue());
        scene.put("particlesSetting",client.options.getParticles().getValue().toString());
        scene.put("graphicsMode",client.options.getGraphicsMode().getValue().toString());
        scene.put("entityDistanceScaling",client.options.getEntityDistanceScaling().getValue());
        scene.put("biomeBlendRadius",client.options.getBiomeBlendRadius().getValue());
        scene.put("framebufferWidth", client.getWindow().getFramebufferWidth());
        scene.put("framebufferHeight", client.getWindow().getFramebufferHeight());
        if (client.world != null) scene.put("dimension", client.world.getRegistryKey().getValue().toString());
        if (client.player != null) scene.put("position", Map.of("x",client.player.getX(),"y",client.player.getY(),"z",client.player.getZ()));
        return scene;
    }

    private static final class Recording implements Runnable {
        private final MinecraftClient client;
        private final Thread renderThread = Thread.currentThread();
        private final int seconds;
        private final boolean automaticSave;
        private final TimingSamples intervals = new TimingSamples(8_333_333L);
        private final TimingSamples work = new TimingSamples(8_333_333L);
        private final StackSamples stacks = new StackSamples();
        private final FrameTimeline timeline = new FrameTimeline();
        private final Map<String, Object> sceneStart;
        private final Map<String, Object> memoryStart;
        private final long started = System.nanoTime();
        private boolean finished;
        private long previousStart;
        private volatile long frameStart;
        private volatile boolean working;
        private volatile boolean stop;

        private Recording(MinecraftClient client, int seconds, boolean automaticSave) {
            this.automaticSave = automaticSave;
            this.client = client;
            this.seconds = seconds;
            this.sceneStart = scene(client);
            this.memoryStart = MemoryReport.snapshot("client");
        }

        @Override public void run() {
            long deadline = System.nanoTime() + seconds * 1_000_000_000L;
            try {
                while (!stop && (!automaticSave || System.nanoTime() < deadline) && client.isRunning()) {
                    long start = frameStart;
                    if (working && System.nanoTime() - start > 8_333_333L) {
                        StackTraceElement[] trace = renderThread.getStackTrace();
                        if (working && frameStart == start) {
                            stacks.add(trace);
                        }
                    }
                    LockSupport.parkNanos(10_000_000L);
                }
                if (automaticSave) client.execute(() -> finish(true));
            } catch (Exception exception) {
                if (active == this) active = null;
                SmoothFix.LOGGER.error("Could not sample client frames", exception);
            }
        }

        private synchronized Map<String,Object> finish(boolean save) {
            if (finished) return Map.of("error", "recording already completed");
            finished = true; stop = true;
            if (active == this) active = null;
            Map<String,Object> report = MemoryReport.snapshot("client");
            report.put("memoryAtStart", memoryStart);
            MemoryReport.addCollectorDeltas(report, memoryStart);
            report.put("sceneAtStart", sceneStart);
            report.put("sceneAtEnd", scene(client));
            report.put("elapsedSeconds", (System.nanoTime()-started)/1_000_000_000.0);
            report.put("frameIntervals", intervals.snapshot());
            report.put("frameWorkBeforePresent", work.snapshot());
            report.put("frameTimeline",timeline.snapshot());
            report.put("frameTimelineNote","Exact 1-second totals, up to 600 seconds, assigned by sample completion time. Includes loading/preparation in advanced stages. Empty buckets can indicate a long stall; no per-second percentiles or GPU measurements.");
            report.put("sampler", Map.of("requestedSeconds",seconds,"intervalMs",10,"thresholdMs",8.333333));
            report.put("slowFrameStacks", stacks.snapshot());
            report.put("measurementNote", "Entire recording totals; percentile reservoir is bounded to 8192. Work excludes presentation/FPS waits. Intervals include waits. GPU time is not measured. Sampling adds overhead. Stack estimates include error bounds; observations are not CPU percentages.");
            if (save) try {
                String path = MemoryReport.write("client_profile", report).toString();
                SmoothFix.LOGGER.info("Smooth Fix client frame profile saved: {}", path);
                if (client.player != null) client.player.sendMessage(Text.literal("Smooth Fix frame profile: " + path), false);
            } catch (Exception e) { SmoothFix.LOGGER.error("Could not save client frame profile", e); }
            return report;
        }
    }
}
