package org.marj4n.smooth_fix.performance;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Reuse only target discovery within a simulation tick; sound generators still run each frame. */
public final class FootstepTargetCache {
    private static final long MAX_AGE_NANOS = 50_000_000L;
    private static final Map<Object, Entry> ENGINES = new WeakHashMap<>();

    private FootstepTargetCache() { }

    public static List<? extends Entity> get(Object engine, Entity camera) {
        Entry entry = ENGINES.get(engine);
        if (entry == null || entry.world() != camera.getWorld() || entry.camera() != camera
                || entry.tick() != camera.getWorld().getTime() || entry.age() != camera.age
                || entry.x() != camera.getX() || entry.y() != camera.getY() || entry.z() != camera.getZ()
                || System.nanoTime() - entry.createdAt() >= MAX_AGE_NANOS) return null;
        return entry.targets();
    }

    public static void put(Object engine, Entity camera, List<? extends Entity> targets) {
        ENGINES.put(engine, new Entry(camera.getWorld(), camera, camera.getWorld().getTime(), camera.age,
                camera.getX(), camera.getY(), camera.getZ(), System.nanoTime(), targets));
    }

    public static void clear() {
        ENGINES.clear();
    }

    private record Entry(World world, Entity camera, long tick, int age, double x, double y, double z,
                         long createdAt, List<? extends Entity> targets) { }
}
