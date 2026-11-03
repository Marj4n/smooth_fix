package org.marj4n.smooth_fix.performance;

import java.util.concurrent.atomic.LongAdder;

/** Counts deferred eager calls and successfully completed native ModernFix invalidations. */
public final class KiltRegistryCacheCounters {
    private static final LongAdder DEFERRED = new LongAdder(), INVALIDATIONS = new LongAdder();

    private KiltRegistryCacheCounters() {}

    public static void deferred(Object state) {
        DEFERRED.increment();
    }

    public static void invalidated() {
        INVALIDATIONS.increment();
    }

    public static long deferredStates() {
        return DEFERRED.sum();
    }

    public static long completedInvalidations() {
        return INVALIDATIONS.sum();
    }
}
