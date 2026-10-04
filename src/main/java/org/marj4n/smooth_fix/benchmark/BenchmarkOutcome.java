package org.marj4n.smooth_fix.benchmark;

import java.util.*;

/** Run completion and workload success are separate. Missing reports never count as passes. */
public final class BenchmarkOutcome {
    private BenchmarkOutcome() { }

    public static String status(List<? extends Map<String, Object>> stages) {
        Map<String, Integer> counts = counts(stages);
        if (counts.get("failed") > 0) return "completed_with_failures";
        if (counts.get("pending") > 0) return "completed_pending_client_reports";
        if (counts.get("skipped") > 0) return "completed_with_skips";
        return "completed";
    }

    public static Map<String, Integer> counts(List<? extends Map<String, Object>> stages) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : List.of("passed", "failed", "skipped", "pending")) counts.put(key, 0);
        for (Map<String, Object> stage : stages) {
            String status = String.valueOf(stage.getOrDefault("status", "pending"));
            String kind = status.startsWith("skipped") ? "skipped" :
                    status.equals("passed") || status.equals("complete") ? "passed" :
                    status.equals("measured") || status.contains("pending") || status.equals("running") ? "pending" : "failed";
            counts.merge(kind, 1, Integer::sum);
        }
        counts.put("total", stages.size());
        return counts;
    }
}
