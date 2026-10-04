package org.marj4n.smooth_fix.benchmark;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.*;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import org.marj4n.smooth_fix.SmoothFix;
/** Original overworld daylight/weather and full EC forecast saved before any test mutation. */
public final class AdvancedWorldRecovery {
    private static Path path(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("smooth_fix/advanced_environment.snbt");}
    public static void save(ServerWorld world)throws Exception {
        NbtCompound data=new NbtCompound();data.putLong("dayTime",world.getTimeOfDay());
        var props=world.getServer().getSaveProperties().getMainWorldProperties();
        data.putInt("clear",props.getClearWeatherTime());data.putInt("rainTime",props.getRainTime());data.putInt("thunderTime",props.getThunderTime());data.putBoolean("rain",props.isRaining());data.putBoolean("thunder",props.isThundering());data.putFloat("rainGradient",world.getRainGradient(1));data.putFloat("thunderGradient",world.getThunderGradient(1));
        if(BloodMoonAdapter.available())try{Object forecast=BloodMoonAdapter.forecastForWorld(world);data.put("forecast",(NbtCompound)forecast.getClass().getMethod("save").invoke(forecast));}catch(Exception e){SmoothFix.LOGGER.warn("Advanced benchmark: forecast backup unavailable; Blood Moon forcing will be skipped",e);}
        BenchmarkRecovery.atomic(path(world.getServer()),data.toString());
    }
    public static boolean canForceMoon(MinecraftServer server){try{return StringNbtReader.parse(Files.readString(path(server))).contains("forecast");}catch(Exception e){return false;}}
    public static boolean restore(MinecraftServer server) {
        Path path=path(server);if(!Files.exists(path))return true;
        try {
            NbtCompound data=StringNbtReader.parse(Files.readString(path));ServerWorld world=server.getOverworld();
            world.setTimeOfDay(data.getLong("dayTime"));var props=server.getSaveProperties().getMainWorldProperties();
            props.setClearWeatherTime(data.getInt("clear"));props.setRainTime(data.getInt("rainTime"));props.setThunderTime(data.getInt("thunderTime"));props.setRaining(data.getBoolean("rain"));props.setThundering(data.getBoolean("thunder"));
            if(data.contains("rainGradient"))world.setRainGradient(data.getFloat("rainGradient"));
            if(data.contains("thunderGradient"))world.setThunderGradient(data.getFloat("thunderGradient"));
            if(data.contains("forecast")){Object forecast=BloodMoonAdapter.forecastForWorld(world);forecast.getClass().getMethod("load",NbtCompound.class).invoke(forecast,data.getCompound("forecast"));var sync=forecast.getClass().getDeclaredMethod("saveAndSync");sync.setAccessible(true);sync.invoke(forecast);}
            Files.delete(path);return true;
        }catch(Exception e){SmoothFix.LOGGER.error("Advanced environment recovery retained for retry",e);return false;}
    }
}
