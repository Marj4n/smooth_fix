package org.marj4n.smooth_fix.performance;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Diagnostic totals only: no streams, resources or world references are retained. */
public final class SortilegeResourceCounters {
    private static final AtomicLong CLOSED = new AtomicLong(),
            BYTES = new AtomicLong(),
            FAILED = new AtomicLong();

    private SortilegeResourceCounters() {}

    public static void closed(int bytes) {
        CLOSED.incrementAndGet();
        BYTES.addAndGet(bytes);
    }

    public static void failed() {
        FAILED.incrementAndGet();
    }

    public static Map<String, Long> snapshot() {
        return Map.of(
                "successfulClosedReads",
                CLOSED.get(),
                "consumedBytes",
                BYTES.get(),
                "failedReadOrCloseAttempts",
                FAILED.get());
    }
}
