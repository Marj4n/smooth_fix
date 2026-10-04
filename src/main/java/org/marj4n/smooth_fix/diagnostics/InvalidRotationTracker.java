package org.marj4n.smooth_fix.diagnostics;

import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import org.marj4n.smooth_fix.SmoothFix;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded counters and one caller trace per entity type, instead of a log per bad value. */
public final class InvalidRotationTracker {
    private static final int MAX_TYPES = 32;
    private static final Map<String, Long> COUNTS = new LinkedHashMap<>();
    private static long total;

    private InvalidRotationTracker() { }

    public static synchronized void record(Entity entity, String axis, float value) {
        total++;
        String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        if (COUNTS.containsKey(type)) {
            COUNTS.put(type, COUNTS.get(type) + 1);
        } else if (COUNTS.size() < MAX_TYPES) {
            COUNTS.put(type, 1L);
            String caller = Arrays.stream(Thread.currentThread().getStackTrace())
                    .filter(frame -> !frame.getClassName().equals(Thread.class.getName())
                            && !frame.getClassName().startsWith("org.marj4n.smooth_fix."))
                    .limit(12).map(StackTraceElement::toString).reduce("", (a, b) -> a + "\n  at " + b);
            SmoothFix.LOGGER.warn("[invalid_entity_rotation] {} ({}) received {}={}; kept previous valid orientation. First caller:{}",
                    type, entity.getClass().getName(), axis, value, caller);
        }
    }

    public static synchronized Map<String, Object> snapshot() {
        return Map.of("invalidInputs", total, "byEntityType", new LinkedHashMap<>(COUNTS),
                "trackedTypeLimit", MAX_TYPES);
    }

    public static synchronized void reset() {
        total = 0;
        COUNTS.clear();
    }
}
