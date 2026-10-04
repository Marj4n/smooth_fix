package org.marj4n.smooth_fix.benchmark;

import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.GameMode;
import org.marj4n.smooth_fix.SmoothFix;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Journal before mutation; retained until the server verifies every journaled field. */
public final class BenchmarkRecovery {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID,Map<String,Object>> verifications=new HashMap<>();
    private BenchmarkRecovery() { }
    // selectedSlot/inventorySnbt allow recovery of journals from the uploaded advanced1 build.
    public record State(String dimension,double x,double y,double z,float yaw,float pitch,int gameMode,
                        boolean flying,boolean allowFlying,boolean invulnerable,float flySpeed,float walkSpeed,
                        String extras,Integer selectedSlot,String inventorySnbt) { }
    private static Path file(MinecraftServer server,UUID player) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("smooth_fix/benchmark_recovery/"+player+".json");
    }
    public static Map<String,Object> lastVerification(UUID id){return verifications.getOrDefault(id,Map.of("all",false,"reason","no_restore_verification"));}
    public static void save(ServerPlayerEntity player)throws IOException{save(player,true);}
    public static void saveFull(ServerPlayerEntity player)throws IOException{save(player,true);}
    private static void save(ServerPlayerEntity player,boolean full)throws IOException{
        if(Files.exists(file(player.getServer(),player.getUuid())))throw new IOException("Unresolved recovery journal must not be overwritten");
        var a=player.getAbilities();NbtCompound extras=new NbtCompound();
        if(full){extras.put("inventory",player.getInventory().writeNbt(new NbtList()));extras.putInt("selected",player.getInventory().selectedSlot);extras.putFloat("health",player.getHealth());player.getHungerManager().writeNbt(extras);extras.putInt("xpLevel",player.experienceLevel);extras.putFloat("xpProgress",player.experienceProgress);extras.putInt("xpTotal",player.totalExperience);}
        State state=new State(player.getWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch(),player.interactionManager.getGameMode().getId(),a.flying,a.allowFlying,a.invulnerable,a.getFlySpeed(),a.getWalkSpeed(),full?extras.toString():null,null,null);
        atomic(file(player.getServer(),player.getUuid()),GSON.toJson(state));
    }
    public static void atomic(Path target,String text)throws IOException{
        Files.createDirectories(target.getParent());Path temp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(temp,text,StandardCharsets.UTF_8);
        try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    public static boolean restore(ServerPlayerEntity player){
        Path path=file(player.getServer(),player.getUuid());if(!Files.exists(path))return true;
        try{
            State state=GSON.fromJson(Files.readString(path),State.class);if(state==null)throw new IOException("Invalid recovery journal");
            var world=player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(state.dimension)));
            if(world==null)throw new IOException("Original dimension is unavailable: "+state.dimension);
            // Parse all inventory data before changing anything, including older advanced1 journals.
            NbtCompound extras=state.extras==null?new NbtCompound():StringNbtReader.parse(state.extras);
            if(state.inventorySnbt!=null){extras.put("inventory",StringNbtReader.parse(state.inventorySnbt).getList("Inventory",10));extras.putInt("selected",state.selectedSlot==null?0:state.selectedSlot);}
            BenchmarkAbilities.revoke(player);player.closeHandledScreen();player.changeGameMode(GameMode.byId(state.gameMode));
            player.teleport(world,state.x,state.y,state.z,state.yaw,state.pitch);player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);player.fallDistance=0;
            var a=player.getAbilities();a.flying=state.flying;a.allowFlying=state.allowFlying;a.invulnerable=state.invulnerable;a.setFlySpeed(state.flySpeed);a.setWalkSpeed(state.walkSpeed);
            if(extras.contains("inventory")){player.getInventory().clear();player.getInventory().readNbt(extras.getList("inventory",10));player.getInventory().selectedSlot=extras.getInt("selected");}
            if(extras.contains("health")){player.setHealth(extras.getFloat("health"));player.getHungerManager().readNbt(extras);player.experienceLevel=extras.getInt("xpLevel");player.experienceProgress=extras.getFloat("xpProgress");player.totalExperience=extras.getInt("xpTotal");}
            player.getInventory().markDirty();player.playerScreenHandler.syncState();player.sendAbilitiesUpdate();
            if(player.networkHandler!=null){player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(player.getInventory().selectedSlot));if(extras.contains("health"))player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(player.experienceProgress,player.totalExperience,player.experienceLevel));}
            Map<String,Object> expected=new LinkedHashMap<>();expected.put("dimension",state.dimension);expected.put("x",state.x);expected.put("y",state.y);expected.put("z",state.z);expected.put("yaw",state.yaw);expected.put("pitch",state.pitch);expected.put("gameMode",state.gameMode);expected.put("flying",state.flying);expected.put("allowFlying",state.allowFlying);expected.put("invulnerable",state.invulnerable);expected.put("flySpeed",state.flySpeed);expected.put("walkSpeed",state.walkSpeed);
            Map<String,Object> result=RecoverySnapshot.compare(expected,RecoverySnapshot.capture(player,player.interactionManager.getGameMode().getId()));result.remove("all");
            if(extras.contains("inventory")){result.put("inventory",extras.getList("inventory",10).equals(player.getInventory().writeNbt(new NbtList())));result.put("selectedHotbarSlot",extras.getInt("selected")==player.getInventory().selectedSlot);}
            if(extras.contains("health")){
                NbtCompound hunger=new NbtCompound();player.getHungerManager().writeNbt(hunger);boolean hungerMatches=true;for(String key:hunger.getKeys())if(!Objects.equals(hunger.get(key),extras.get(key)))hungerMatches=false;
                result.put("health",Math.abs(player.getHealth()-extras.getFloat("health"))<.0001);result.put("hunger",hungerMatches);result.put("experience",player.experienceLevel==extras.getInt("xpLevel")&&player.totalExperience==extras.getInt("xpTotal")&&Math.abs(player.experienceProgress-extras.getFloat("xpProgress"))<.0001);
            }
            result.put("all",result.values().stream().allMatch(Boolean.TRUE::equals));verifications.put(player.getUuid(),result);
            if(!Boolean.TRUE.equals(result.get("all")))throw new IOException("Recovery equality failed: "+result);
            Files.delete(path);return true;
        }catch(Exception e){SmoothFix.LOGGER.error("Benchmark recovery retained for {}",player.getUuid(),e);verifications.put(player.getUuid(),Map.of("all",false,"reason",e.toString()));return false;}
    }
}
