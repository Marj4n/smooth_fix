package org.marj4n.smooth_fix.performance;
import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.SmoothFix;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
/** Exact installed source guard; default OFF, no entity/world reference or per-call counter. */
public final class HorsemanBoundsAudit {
 public static final String SHA="c524fbc187ff81230041c2a79d7a651fd5d17953f595b917b6a7c4f0b6e06616";
 private static volatile boolean supported;private static volatile int patched;
 public static boolean supported(){
  var mod=FabricLoader.getInstance().getModContainer("horseman");
  if(mod.isEmpty()||!mod.get().getMetadata().getVersion().getFriendlyString().equals("1.3.17"))return false;
  try{
   var path=mod.get().findPath("io/github/mortuusars/horseman/mixin/fits_in_boat/EntityMixin.class");if(path.isEmpty())return false;
   supported=SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path.get()))));
   if(!supported)SmoothFix.LOGGER.warn("Horseman non-horse bounds fast path refused: installed source differs");return supported;
  }catch(Exception e){SmoothFix.LOGGER.warn("Horseman bounds audit failed; original behavior retained",e);return false;}
 }
 public static void applied(int count){patched+=count;}
 public static Map<String,Object> snapshot(){return Map.of("requested",Boolean.getBoolean("smoothfix.experimental.horsemanNonHorseBoundsFastPath"),"installedSourceAudited",supported,"patchedMethods",patched,"scope","No per-entity counter/cache; original horse/boat path retained. Runtime/performance acceptance separate.");}
 private HorsemanBoundsAudit(){}
}
