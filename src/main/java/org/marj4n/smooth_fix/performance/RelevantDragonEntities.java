package org.marj4n.smooth_fix.performance;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import java.util.LinkedHashSet;
import java.util.Set;
/** Loaded-entity index only: the original camera/ability/distance math still runs on each invocation. */
public final class RelevantDragonEntities {
    private static final String DRAGON="com.leon.saintsdragons.server.entity.dragons.ignivorus.Ignivorus";
    private static ClientWorld indexedWorld;
    private static final Set<Entity> dragons=new LinkedHashSet<>();
    private RelevantDragonEntities() { }
    public static void install() {
        ClientEntityEvents.ENTITY_LOAD.register((entity,world)->{if(world==indexedWorld && matches(entity))dragons.add(entity);});
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(world==indexedWorld)dragons.remove(entity);});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
    }
    private static boolean matches(Entity entity) {
        for(Class<?> c=entity.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals(DRAGON))return true;
        return false;
    }
    public static Iterable<Entity> entities(ClientWorld world) {
        if(indexedWorld!=world) {
            clear();indexedWorld=world;
            for(Entity entity:world.getEntities())if(matches(entity))dragons.add(entity);
        }
        return dragons;
    }
    private static void clear(){indexedWorld=null;dragons.clear();}
}
