package org.marj4n.smooth_fix.performance;

import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
import java.util.concurrent.ForkJoinPool;

public final class WorkerPoolBudget {
    private WorkerPoolBudget() { }
    public static ForkJoinPool create(int upstream, ForkJoinPool.ForkJoinWorkerThreadFactory factory,
                                     Thread.UncaughtExceptionHandler handler, boolean async) {
        int limit = SmoothFixConfig.get().backgroundWorkerLimit;
        // Preserve the JDK's upstream validation for invalid pool sizes.
        int result = limit <= 0 || upstream <= 0 || upstream > 32767 ? upstream : Math.min(upstream, Math.max(1, Math.min(limit, 64)));
        if (result != upstream) SmoothFix.LOGGER.info("Background worker parallelism: {} -> {} (Smooth Fix budget)",upstream,result);
        return new ForkJoinPool(result,factory,handler,async);
    }
}
