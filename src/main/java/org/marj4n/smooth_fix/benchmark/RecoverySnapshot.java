package org.marj4n.smooth_fix.benchmark;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Fields available on both peers. Exact inventory hash avoids oversized final packets. */
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
        out.put("inventorySha256",inventoryHash(player));
        return out;
    }
    public static String inventoryHash(PlayerEntity player) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(player.getInventory().writeNbt(new NbtList()).toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static Map<String,Object> compare(Map<String,Object> expected,Map<String,Object> actual) {
        Map<String,Object> result=new LinkedHashMap<>();
        expected.forEach((key,value)->{
            Object now=actual.get(key);boolean equal;
            if(value instanceof Number a && now instanceof Number b){double tolerance=key.equals("x")||key.equals("y")||key.equals("z")?.05:key.equals("yaw")||key.equals("pitch")?.05:.0001;equal=Math.abs(a.doubleValue()-b.doubleValue())<=tolerance;}
            else equal=Objects.equals(value,now);
            result.put(key,equal);
        });
        result.put("all",!expected.isEmpty() && result.values().stream().allMatch(Boolean.TRUE::equals));
        return result;
    }
}
