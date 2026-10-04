package org.marj4n.smooth_fix.benchmark;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.Optional;
/** Uses Enhanced Celestials 2's own forecast API only in our dedicated lunar dimension. */
public final class BloodMoonAdapter {
    private static final Identifier BLOOD_MOON=new Identifier("enhancedcelestials2defaultlunarevents","blood_moon");
    private BloodMoonAdapter() { }
    public static boolean available(){var loader=FabricLoader.getInstance();return loader.isModLoaded("enhancedcelestials2core") && loader.isModLoaded("enhancedcelestials2defaultlunarevents");}
    private static Object forecast(ServerWorld world)throws ReflectiveOperationException {
        if(!world.getRegistryKey().equals(ServerBenchmark.LUNAR))throw new IllegalArgumentException("Blood Moon adapter is restricted to the benchmark lunar dimension");
        Class<?> api=Class.forName("dev.corgitaco.enhancedcelestials2core.EnhancedCelestials");
        Optional<?> value=(Optional<?>)api.getMethod("lunarForecastWorldData",World.class).invoke(null,world);
        return value.orElseThrow(()->new IllegalStateException("Lunar forecast unavailable in benchmark dimension"));
    }
    public static void enable(ServerWorld world)throws ReflectiveOperationException {
        Object data=forecast(world);
        Registry<Object> registry=world.getRegistryManager().get(RegistryKey.ofRegistry(new Identifier("enhancedcelestials2core","lunar/event")));
        Object event=registry.getOrEmpty(BLOOD_MOON).orElseThrow(()->new IllegalStateException("Blood Moon registry entry missing"));
        RegistryEntry<?> entry=registry.getEntry(registry.getRawId(event)).orElseThrow();
        data.getClass().getMethod("setLunarEventTonight",RegistryEntry.class).invoke(data,entry);
    }
    public static boolean isActive(ServerWorld world)throws ReflectiveOperationException {
        Object data=forecast(world);RegistryEntry<?> event=(RegistryEntry<?>)data.getClass().getMethod("currentLunarEventHolder").invoke(data);
        return event.matchesId(BLOOD_MOON) && (boolean)data.getClass().getMethod("isEventActive").invoke(data);
    }
    @SuppressWarnings("unchecked")
    public static void reset(ServerWorld world) {
        if(!available() || world==null)return;
        try {
            Object data=forecast(world);Object settings=data.getClass().getMethod("getDimensionSettings").invoke(data);
            RegistryKey<?> defaultKey=(RegistryKey<?>)settings.getClass().getMethod("defaultEvent").invoke(settings);
            Registry<Object> registry=world.getRegistryManager().get(RegistryKey.ofRegistry(defaultKey.getRegistry()));
            RegistryEntry<?> entry=registry.entryOf((RegistryKey<Object>)defaultKey);
            data.getClass().getMethod("setLunarEventTonight",RegistryEntry.class).invoke(data,entry);
        }catch(Exception e){org.marj4n.smooth_fix.SmoothFix.LOGGER.warn("Could not reset isolated benchmark lunar forecast",e);}
    }
}
