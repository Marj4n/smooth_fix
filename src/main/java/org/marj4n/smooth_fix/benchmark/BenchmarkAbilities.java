package org.marj4n.smooth_fix.benchmark;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
/** Optional PAL grants prevent temporary survival UI flight from fighting the mod's trackers. */
public final class BenchmarkAbilities {
    private BenchmarkAbilities() { }
    private static void pal(ServerPlayerEntity player,boolean grant,boolean flying)throws Exception {
        Class<?> pal=Class.forName("io.github.ladysnake.pal.Pal"),ability=Class.forName("io.github.ladysnake.pal.PlayerAbility"),vanilla=Class.forName("io.github.ladysnake.pal.VanillaAbilities");
        Object source=pal.getMethod("getAbilitySource",String.class,String.class).invoke(null,"smooth_fix","advanced_benchmark");
        var method=source.getClass().getMethod(grant?"grantTo":"revokeFrom",PlayerEntity.class,ability);
        for(String name:new String[]{"ALLOW_FLYING","INVULNERABLE"})method.invoke(source,player,vanilla.getField(name).get(null));
        source.getClass().getMethod(grant && flying?"grantTo":"revokeFrom",PlayerEntity.class,ability).invoke(source,player,vanilla.getField("FLYING").get(null));
    }
    public static void grant(ServerPlayerEntity player) {grant(player,true);}
    public static void grant(ServerPlayerEntity player,boolean flying) {
        try {if(FabricLoader.getInstance().isModLoaded("playerabilitylib"))pal(player,true,flying);else {player.getAbilities().allowFlying=true;player.getAbilities().flying=flying;player.getAbilities().invulnerable=true;}if(!flying)player.getAbilities().flying=false;player.sendAbilitiesUpdate();}
        catch(Exception e){throw new IllegalStateException("Temporary benchmark ability grant failed",e);}
    }
    public static void revoke(ServerPlayerEntity player) {
        try {if(FabricLoader.getInstance().isModLoaded("playerabilitylib"))pal(player,false,false);}
        catch(Exception e){throw new IllegalStateException("Temporary benchmark ability recovery failed",e);}
    }
}
