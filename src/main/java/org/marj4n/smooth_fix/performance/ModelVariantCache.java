package org.marj4n.smooth_fix.performance;
import com.google.common.collect.ImmutableMap;import net.minecraft.client.render.block.BlockModels;import net.minecraft.state.property.Property;import java.util.Map;
/** Bounded immutable property serialization, not a model/world/resource cache. Each worker owns its slots. */
public final class ModelVariantCache {
 public static final int CAPACITY=4096;
 private static final ThreadLocal<Slots> LOCAL=ThreadLocal.withInitial(Slots::new);
 private ModelVariantCache() { }
 public static String variant(Map<Property<?>,Comparable<?>> properties){
  if(!(properties instanceof ImmutableMap))return BlockModels.propertyMapToString(properties);
  Slots slots=LOCAL.get();int hash=System.identityHashCode(properties);int index=(hash^(hash>>>16))&(CAPACITY-1);
  if(slots.keys[index]==properties)return slots.values[index];
  String result=BlockModels.propertyMapToString(properties);slots.keys[index]=properties;slots.values[index]=result;return result;
 }
 public static void clearCurrentThread(){LOCAL.remove();}
 private static final class Slots {final Object[] keys=new Object[CAPACITY];final String[] values=new String[CAPACITY];}
}
