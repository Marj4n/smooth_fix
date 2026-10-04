package org.marj4n.smooth_fix.diagnostics;

import java.util.Map;

/** Start-to-start wall clock: captures delays outside the Fabric tick callbacks too. */
public final class TickCadence {
    private final TimingSamples intervals = new TimingSamples(50_000_000L);
    private long previousStart;

    public synchronized void beginTick(long now) {
        if (previousStart != 0) intervals.add(now - previousStart);
        previousStart = now;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> report = intervals.snapshot();
        report.put("measurement", "Server tick start-to-start wall clock, including scheduled sleep, queued tasks and JVM pauses. Normal pacing is about 50ms; values just above 50ms can be scheduler jitter. This is not tick CPU work. First/last partial intervals are excluded.");
        return report;
    }
}
