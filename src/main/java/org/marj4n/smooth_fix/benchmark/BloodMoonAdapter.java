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
        return forecastForWorld(world);
    }
    public static Object forecastForWorld(ServerWorld world)throws ReflectiveOperationException {
        Class<?> api=Class.forName("dev.corgitaco.enhancedcelestials2core.EnhancedCelestials");
        Optional<?> value=(Optional<?>)api.getMethod("lunarForecastWorldData",World.class).invoke(null,world);
        return value.orElseThrow(()->new IllegalStateException("Lunar forecast unavailable in benchmark dimension"));
    }
    public static void enable(ServerWorld world)throws ReflectiveOperationException {
        Object data=forecast(world);
        enableData(world,data);
    }
    public static void enableInWorld(ServerWorld world)throws ReflectiveOperationException {enableData(world,forecastForWorld(world));}
    /** Prepare a real native fade-ready night even when the copied world freezes daylight. */
    public static java.util.Map<String,Object> prepareActiveNight(ServerWorld world)throws ReflectiveOperationException {
        Object data=forecastForWorld(world);Object settings=data.getClass().getMethod("getDimensionSettings").invoke(data);
        long length=((Number)settings.getClass().getMethod("dayLength").invoke(settings)).longValue();
        long night=((Number)settings.getClass().getMethod("nightStartTime").invoke(settings)).longValue();
        // Installed EC2 Core1.0.3.3 getBlend uses a100-world-tick fade. Do not falsify its state.
        if(length<=102||night<0||night>length-103)throw new IllegalStateException("No native100tick fade-ready nighttime window");
        long before=world.getTimeOfDay(),base=Math.floorDiv(before,length)*length;
        long scheduled=Math.max(before,base+night);if(scheduled+101>=base+length)scheduled=base+length+night;
        boolean daylight=world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE);
        world.setTimeOfDay(scheduled);enableData(world,data);world.setTimeOfDay(scheduled+101);
        RegistryEntry<?> current=(RegistryEntry<?>)data.getClass().getMethod("currentLunarEventHolder").invoke(data);
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>();result.put("policy","Native setLunarEventTonight followed by101worldticks of clock phase preparation before timed action; no gamerule, native event field or visual-density changes. Original full forecast/time/weather restored by journal.");
        result.put("clockBefore",before);result.put("nativeScheduledClock",scheduled);result.put("preparedClock",world.getTimeOfDay());result.put("nativeDayLength",length);result.put("nativeNightStart",night);result.put("daylightRuleBefore",daylight);result.put("daylightRuleUnchanged",daylight==world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE));result.put("nativeBlend",data.getClass().getMethod("getBlend").invoke(data));result.put("nativeBloodMoonSelected",current.matchesId(BLOOD_MOON));result.put("nativeEventActive",data.getClass().getMethod("isEventActive").invoke(data));return result;
    }

    private static void enableData(ServerWorld world,Object data)throws ReflectiveOperationException {
        Registry<Object> registry=world.getRegistryManager().get(RegistryKey.ofRegistry(new Identifier("enhancedcelestials2core","lunar/event")));
        Object event=registry.getOrEmpty(BLOOD_MOON).orElseThrow(()->new IllegalStateException("Blood Moon registry entry missing"));
        RegistryEntry<?> entry=registry.getEntry(registry.getRawId(event)).orElseThrow();
        data.getClass().getMethod("setLunarEventTonight",RegistryEntry.class).invoke(data,entry);
    }
    public static boolean isActive(ServerWorld world)throws ReflectiveOperationException {
        Object data=forecast(world);RegistryEntry<?> event=(RegistryEntry<?>)data.getClass().getMethod("currentLunarEventHolder").invoke(data);
        return event.matchesId(BLOOD_MOON) && (boolean)data.getClass().getMethod("isEventActive").invoke(data);
    }
    public static boolean isActiveInWorld(ServerWorld world)throws ReflectiveOperationException {
        Object data=forecastForWorld(world);RegistryEntry<?> event=(RegistryEntry<?>)data.getClass().getMethod("currentLunarEventHolder").invoke(data);
        return event.matchesId(BLOOD_MOON) && (boolean)data.getClass().getMethod("isEventActive").invoke(data);
    }
    public static void reset(ServerWorld world) {
        if(!available() || world==null)return;
        try {
            Object data=forecast(world);Object settings=data.getClass().getMethod("getDimensionSettings").invoke(data);
            RegistryKey<?> defaultKey=(RegistryKey<?>)settings.getClass().getMethod("defaultEvent").invoke(settings);
            Registry<Object> registry=world.getRegistryManager().get(RegistryKey.ofRegistry(defaultKey.getRegistry()));
            Object value=registry.getOrEmpty(defaultKey.getValue()).orElseThrow();RegistryEntry<?> entry=registry.getEntry(registry.getRawId(value)).orElseThrow();
            data.getClass().getMethod("setLunarEventTonight",RegistryEntry.class).invoke(data,entry);
        }catch(Exception e){org.marj4n.smooth_fix.SmoothFix.LOGGER.warn("Could not reset isolated benchmark lunar forecast",e);}
    }
}
