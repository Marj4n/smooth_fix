package org.marj4n.smooth_fix.diagnostics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.marj4n.smooth_fix.SmoothFix;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/** Passive bounded tick timings and an opt-in sampler for ticks exceeding the 50 ms tick budget. */
public final class ServerDiagnostics {
    private static final long[] RECENT_TICKS = new long[1200];
    private static int tickCount;
    private static int tickIndex;
    private static volatile long tickStart;
    private static volatile boolean inTick;
    private static volatile Thread serverThread;
    private static volatile Profile activeProfile;

    private ServerDiagnostics() { }

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> reset());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopProfile());
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            serverThread = Thread.currentThread();
            tickStart = System.nanoTime();
            inTick = true;
            Profile profile = activeProfile;
            if (profile != null) profile.cadence.beginTick(tickStart);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long elapsed = System.nanoTime() - tickStart;
            inTick = false;
            recordTick(elapsed);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("smoothfix").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("report").executes(context -> writeReport(context.getSource())))
                        .then(CommandManager.literal("profile")
                                .executes(context -> beginProfile(context.getSource(), 120))
                                .then(CommandManager.argument("seconds", IntegerArgumentType.integer(10, 120))
                                        .executes(context -> beginProfile(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(CommandManager.literal("stopprofile").executes(context -> {
                            stopProfile();
                            context.getSource().sendFeedback(() -> Text.literal("Smooth Fix profile will be saved; check server log for its path."), false);
                            return 1;
                        }))));
    }

    private static int writeReport(ServerCommandSource source) {
        try {
            String path = MemoryReport.write("server", snapshot()).toString();
            source.sendFeedback(() -> Text.literal("Smooth Fix report: " + path), false);
            return 1;
        } catch (Exception exception) {
            SmoothFix.LOGGER.error("Cannot write Smooth Fix server report", exception);
            source.sendError(Text.literal("Could not write report; check server log."));
            return 0;
        }
    }

    private static int beginProfile(ServerCommandSource source, int seconds) {
        synchronized (ServerDiagnostics.class) {
            if (activeProfile != null) {
                source.sendError(Text.literal("A Smooth Fix profile is already running."));
                return 0;
            }
            Profile profile = new Profile(seconds, true);
            activeProfile = profile;
            Thread worker = new Thread(profile, "Smooth Fix Lag Sampler");
            worker.setDaemon(true);
            worker.start();
        }
        source.sendFeedback(() -> Text.literal("Smooth Fix recording slow-tick stacks for " + seconds
                + " seconds. Play until lag appears; report path will be logged automatically."), false);
        return 1;
    }

    private static void stopProfile() {
        Profile profile = activeProfile;
        if (profile != null) {
            profile.stop = true;
        }
    }

    private static synchronized void reset() {
        stopProfile();
        Arrays.fill(RECENT_TICKS, 0);
        tickCount = 0;
        tickIndex = 0;
        inTick = false;
        InvalidRotationTracker.reset();
    }

    private static synchronized void recordTick(long elapsed) {
        Profile p = activeProfile;
        if (p != null) p.timings.add(elapsed);
        RECENT_TICKS[tickIndex] = elapsed;
        tickIndex = (tickIndex + 1) % RECENT_TICKS.length;
        tickCount = Math.min(tickCount + 1, RECENT_TICKS.length);
    }

    public static synchronized Map<String, Object> snapshot() {
        Map<String, Object> report = MemoryReport.snapshot("server");
        long[] sorted = Arrays.copyOf(RECENT_TICKS, tickCount);
        Arrays.sort(sorted);
        double sum = 0;
        int slow = 0, overBudget = 0;
        for (long tick : sorted) {
            sum += tick / 1_000_000.0;
            if (tick > 100_000_000L) slow++;
            if (tick > 50_000_000L) overBudget++;
        }
        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("samples", tickCount);
        timings.put("meanMs", tickCount == 0 ? 0 : sum / tickCount);
        timings.put("p95Ms", tickCount == 0 ? 0 : sorted[(int) Math.ceil(tickCount * 0.95) - 1] / 1_000_000.0);
        timings.put("maxMs", tickCount == 0 ? 0 : sorted[tickCount - 1] / 1_000_000.0);
        timings.put("ticksOver100Ms", slow);
        timings.put("ticksOver50Ms", overBudget);
        timings.put("measurement", "Interval between this mod's Fabric START/END tick callbacks; excludes sleep between ticks.");
        report.put("recentTickTimings", timings);
        report.put("invalidRotations", InvalidRotationTracker.snapshot());
        return report;
    }

    public static synchronized boolean isProfiling() { return activeProfile != null; }
    public static synchronized boolean startRecording() {
        if (activeProfile != null) return false;
        Profile p = new Profile(0, false); activeProfile = p;
        Thread t = new Thread(p, "Smooth Fix Benchmark Sampler"); t.setDaemon(true); t.start(); return true;
    }
    public static synchronized Map<String,Object> finishRecording() {
        Profile p = activeProfile;
        if (p == null) return Map.of("error", "no active recording");
        p.stop = true; activeProfile = null; return p.report();
    }
    private static final class Profile implements Runnable {
        private final int seconds;
        private final boolean autoSave;
        private final StackSamples stacks = new StackSamples();
        private final TimingSamples timings = new TimingSamples(50_000_000L);
        private final TickCadence cadence = new TickCadence();
        private final StackSamples boundaryStacks = new StackSamples();
        private volatile boolean stop;
        private final Map<String,Object> memoryAtStart = MemoryReport.snapshot("server");
        private final long started = System.nanoTime();
        private Profile(int seconds, boolean autoSave) { this.seconds=seconds; this.autoSave=autoSave; }
        private Map<String,Object> report() {
            Map<String,Object> report = snapshot();
            report.put("memoryAtStart", memoryAtStart);
            MemoryReport.addCollectorDeltas(report, memoryAtStart);
            report.put("elapsedSeconds", (System.nanoTime()-started)/1_000_000_000.0);
            report.put("profileTickTimings", timings.snapshot());
            report.put("tickStartIntervals", cadence.snapshot());
            report.put("sampler", Map.of("requestedSeconds",seconds,"intervalMs",10,"tickThresholdMs",50));
            report.put("slowTickStacks", stacks.snapshot());
            report.put("delayedTickBoundaryStacks", boundaryStacks.snapshot());
            report.put("measurementNote", "profileTickTimings measures only this mod's Fabric START/END callbacks and can miss pauses outside them. tickStartIntervals measures wall-clock cadence including normal sleep. Boundary stacks sample a runnable server thread more than 100ms after its last tick start, outside callbacks; scheduled waits are excluded. GC deltas are collector counters, not exact pause attribution. Sampling adds overhead.");
            return report;
        }
        @Override public void run() {
            long deadline = seconds == 0 ? Long.MAX_VALUE : started + seconds*1_000_000_000L;
            try {
                while (!stop && System.nanoTime()<deadline) {
                    Thread target=serverThread; long start=tickStart;
                    if(target!=null && inTick && System.nanoTime()-start>50_000_000L) {
                        StackTraceElement[] trace=target.getStackTrace();
                        if(!stop && inTick && tickStart==start) stacks.add(trace);
                    } else if(target!=null && !inTick && start!=0 && System.nanoTime()-start>100_000_000L
                            && target.getState()==Thread.State.RUNNABLE) {
                        StackTraceElement[] trace=target.getStackTrace();
                        if(!stop && !inTick && tickStart==start && target.getState()==Thread.State.RUNNABLE) boundaryStacks.add(trace);
                    }
                    LockSupport.parkNanos(10_000_000L);
                }
                if(autoSave) SmoothFix.LOGGER.info("Smooth Fix lag profile saved: {}",MemoryReport.write("server_profile",report()));
            } catch(Exception e) { SmoothFix.LOGGER.error("Could not finish Smooth Fix lag profile",e); }
            finally { synchronized(ServerDiagnostics.class) { if(activeProfile==this) activeProfile=null; } }
        }
    }
}
