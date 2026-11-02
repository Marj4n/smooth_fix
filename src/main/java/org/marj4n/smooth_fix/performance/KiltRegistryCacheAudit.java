package org.marj4n.smooth_fix.performance;
import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.SmoothFix;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Explicit experimental opt-in, two raw-class hashes and actual ModernFix enabled-option gate. */
public final class KiltRegistryCacheAudit {
 private KiltRegistryCacheAudit() { }
 public static boolean requested(boolean configured){String v=System.getProperty("smoothfix.experimental.kiltRegistryCache");return v==null?configured:"true".equals(v);}
 private static boolean hash(String path,String expected)throws Exception{
  try(var input=KiltRegistryCacheAudit.class.getClassLoader().getResourceAsStream(path)){
   return input!=null&&expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
  }
 }
 public static boolean supported(){
  var loader=FabricLoader.getInstance();
  if(!loader.isModLoaded("kilt")||!loader.getModContainer("modernfix").map(m->m.getMetadata().getVersion().getFriendlyString().equals("5.25.2+mc1.20.1")).orElse(false))return false;
  try{
   if(!hash("net/minecraftforge/registries/GameData$BlockCallbacks.class","1fab404f3f793c0b6754ded057a0efac02928dd11d7112686c1f6a71af076532")||!hash("org/embeddedt/modernfix/blockstate/BlockStateCacheHandler.class","ddaeaf84eef7c24ee9fb39ffecb6a48185508c336c2c2be72196b6affd584ae9"))return false;
   Class<?> plugin=Class.forName("org.embeddedt.modernfix.core.ModernFixMixinPlugin",false,KiltRegistryCacheAudit.class.getClassLoader());Object instance=plugin.getField("instance").get(null);
   if(instance==null||!Boolean.TRUE.equals(plugin.getMethod("isOptionEnabled",String.class).invoke(instance,"perf.reduce_blockstate_cache_rebuilds.BlockStateBaseMixin")))return false;
   SmoothFix.logPatchOnce("kilt-modernfix-registry-cache","Experimental Kilt/ModernFix Forge registry-cache bridge requested; actual application and lifecycle acceptance remain separately verified");return true;
  }catch(Exception e){SmoothFix.LOGGER.warn("Kilt/ModernFix registry-cache audit unavailable; original registry bake retained",e);return false;}
 }
}
