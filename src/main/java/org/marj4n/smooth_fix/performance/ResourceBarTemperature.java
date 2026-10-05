package org.marj4n.smooth_fix.performance;

import java.lang.reflect.Method;

/** Cache optional Thermoo discovery by player class, including an absent method. */
public final class ResourceBarTemperature {
    private static final ClassValue<Lookup> TEMPERATURE = new ClassValue<>() {
        @Override
        protected Lookup computeValue(Class<?> playerClass) {
            try {
                return new Lookup(playerClass.getMethod("thermoo$getTemperatureScale"));
            } catch (NoSuchMethodException exception) {
                return new Lookup(null);
            }
        }
    };

    private ResourceBarTemperature() { }

    public static float scale(Object player) {
        try {
            Method method = TEMPERATURE.get(player.getClass()).method();
            if (method == null) return 0.0F;
            // Read the current value each frame; only discovery is cached.
            return (float) method.invoke(player);
        } catch (Exception exception) {
            return 0.0F;
        }
    }

    private record Lookup(Method method) { }
}
