package org.marj4n.smooth_fix.benchmark;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtList;
import java.util.*;

/** Peer fields use the native shared item-wire view; server full saved-NBT equality remains separate. */
public final class RecoverySnapshot {
    private RecoverySnapshot() { }
    public static Map<String,Object> capture(PlayerEntity player, int gameMode) {
        var a=player.getAbilities();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("dimension",player.getWorld().getRegistryKey().getValue().toString());
        out.put("x",player.getX());out.put("y",player.getY());out.put("z",player.getZ());
        out.put("yaw",player.getYaw());out.put("pitch",player.getPitch());
        out.put("selectedSlot",player.getInventory().selectedSlot);out.put("gameMode",gameMode);
        out.put("flying",a.flying);out.put("allowFlying",a.allowFlying);out.put("invulnerable",a.invulnerable);
        out.put("flySpeed",a.getFlySpeed());out.put("walkSpeed",a.getWalkSpeed());
        NbtList inventory;
        try{inventory=NetworkInventoryProjection.capture(player.getInventory());out.put("inventoryProjectionAvailable",true);}
        catch(RuntimeException unsupported){
            out.put("inventoryProjectionAvailable",false);out.put("inventoryProjectionError",unsupported.getClass().getSimpleName());
            out.put("inventorySha256","unavailable");out.put("inventoryHashFormat","network-item-stack-v2");out.put("inventorySlotHashes",Map.of());
            return out;
        }
        out.put("inventorySha256",CanonicalNbtHash.sha256(inventory));out.put("inventoryHashFormat","network-item-stack-v2");
        // Derive diagnostics from the SAME serialization, without exposing item names or NBT values.
        Map<String,String> slots=new LinkedHashMap<>();
        for(int i=0;i<inventory.size();i++){
            var item=inventory.getCompound(i);String key="slot_"+item.getInt("Slot");
            if(slots.containsKey(key))key+="_duplicate_"+i;
            slots.put(key,CanonicalNbtHash.sha256(item));
        }
        out.put("inventorySlotHashes",slots);
        return out;
    }
    /** Hash ALL original client save NBT too; no tag/empty-compound blacklist. */
    public static Map<String,Object> captureLocal(PlayerEntity player,int gameMode){
        var out=capture(player,gameMode);var saved=player.getInventory().writeNbt(new NbtList());
        out.put("localSavedInventorySha256",CanonicalNbtHash.sha256(saved));out.put("localSavedInventoryFormat","canonical-nbt-v1");
        out.put("health",player.getHealth());out.put("foodLevel",player.getHungerManager().getFoodLevel());out.put("saturation",player.getHungerManager().getSaturationLevel());
        out.put("xpLevel",player.experienceLevel);out.put("xpProgress",player.experienceProgress);out.put("xpTotal",player.totalExperience);
        return out;
    }
    public static String inventoryHash(PlayerEntity player) {
        return CanonicalNbtHash.sha256(player.getInventory().writeNbt(new NbtList()));
    }
    public static Map<String,Object> compare(Map<String,Object> expected,Map<String,Object> actual) {
        Map<String,Object> result=new LinkedHashMap<>();
        expected.forEach((key,value)->{
            Object now=actual.get(key);boolean equal;
            if(value instanceof Number a && now instanceof Number b){double tolerance=key.equals("x")||key.equals("y")||key.equals("z")?.05:key.equals("yaw")||key.equals("pitch")?.05:.0001;equal=Math.abs(a.doubleValue()-b.doubleValue())<=tolerance;}
            else equal=Objects.equals(value,now);
            result.put(key,equal);
        });
        // Two unsupported projections can never validate one another as equal.
        if(expected.containsKey("inventoryProjectionAvailable")&&(!Boolean.TRUE.equals(expected.get("inventoryProjectionAvailable"))||!Boolean.TRUE.equals(actual.get("inventoryProjectionAvailable"))))result.put("inventoryProjectionAvailable",false);
        result.put("all",!expected.isEmpty() && result.values().stream().allMatch(Boolean.TRUE::equals));
        return result;
    }
}
