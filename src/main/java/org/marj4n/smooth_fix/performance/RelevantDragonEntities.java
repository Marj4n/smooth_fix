package org.marj4n.smooth_fix.performance;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Loaded dragons only. Upstream distance, ability, sound and cleanup logic still runs. */
public final class RelevantDragonEntities {
    private static final String DRAGON_PACKAGE = "com.leon.saintsdragons.server.entity.dragons.";
    private static final String IGNIVORUS = DRAGON_PACKAGE + "ignivorus.Ignivorus";
    private static final ClassValue<Set<String>> TYPE_NAMES = new ClassValue<>() {
        @Override
        protected Set<String> computeValue(Class<?> entityClass) {
            Set<String> names = new HashSet<>();
            for (Class<?> type = entityClass; type != null; type = type.getSuperclass()) {
                if (type.getName().startsWith(DRAGON_PACKAGE)) names.add(type.getName());
            }
            return Set.copyOf(names);
        }
    };
    private static ClientWorld indexedWorld;
    private static boolean installed;
    private static final Set<Entity> dragons = new LinkedHashSet<>();
    private static final Map<String, Set<Entity>> typedDragons = new HashMap<>();

    private RelevantDragonEntities() { }

    public static void install() {
        if (installed) return;
        installed = true;
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world != indexedWorld) return;
            Set<String> types = TYPE_NAMES.get(entity.getClass());
            if (types.isEmpty()) return;
            dragons.add(entity);
            typedDragons.forEach((type, entities) -> { if (types.contains(type)) entities.add(entity); });
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (world != indexedWorld) return;
            dragons.remove(entity);
            typedDragons.values().forEach(entities -> entities.remove(entity));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static Iterable<Entity> entities(ClientWorld world) {
        return entities(world, IGNIVORUS);
    }

    public static Iterable<Entity> entities(ClientWorld world, String binaryType) {
        if (indexedWorld != world) {
            clear();
            indexedWorld = world;
            for (Entity entity : world.getEntities()) {
                if (!TYPE_NAMES.get(entity.getClass()).isEmpty()) dragons.add(entity);
            }
        }
        return typedDragons.computeIfAbsent(binaryType, type -> {
            Set<Entity> matches = new LinkedHashSet<>();
            for (Entity entity : dragons) {
                if (TYPE_NAMES.get(entity.getClass()).contains(type)) matches.add(entity);
            }
            return matches;
        });
    }

    private static void clear() {
        indexedWorld = null;
        dragons.clear();
        typedDragons.clear();
    }
}
