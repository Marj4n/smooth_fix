package org.marj4n.smooth_fix.performance;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.marj4n.smooth_fix.SmoothFix;
import java.lang.reflect.Field;
import java.util.*;
/** Only retires stale per-frame owner references, never renderer resources or active-world state. */
public final class FabricRenderContextOwner {
 private static final boolean ENABLED=Boolean.getBoolean("smoothfix.experimental.disconnectedFabricRenderContextCleanup");
 private static final boolean INVENTORIES=Boolean.getBoolean("smoothfix.experimental.closedClientInventoryWrapperCleanup");
 private static final Set<ClientWorld> closed=Collections.newSetFromMap(new WeakHashMap<>());
 private static boolean installed;
 private FabricRenderContextOwner(){}
 public static void request(MinecraftClient client,ClientWorld previous){
  if(!(ENABLED||INVENTORIES||ClosedClientMemoOwners.requested())||previous==null||!client.isOnThread())return;
  closed.add(previous);
  if(installed)return;installed=true;
  ClientTickEvents.END_CLIENT_TICK.register(FabricRenderContextOwner::release);
 }
 private static void release(MinecraftClient client){
  if(closed.isEmpty()||!client.isOnThread())return;
  try{
   if(INVENTORIES)for(ClientWorld previous:closed)if(previous!=client.world)LifecycleRetentionCleanup.closedClientInventories(previous);
   if(ClosedClientMemoOwners.requested())for(ClientWorld previous:closed)ClosedClientMemoOwners.release(client,previous);
   if(!ENABLED)return;
   Class<?> type=Class.forName("net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl");
   if(!LifecycleRetentionCleanup.auditedClass(type,"c112bf54f782c9c9bdb8444b60ec982763dc9939f1d4bdfbefd9cdd5b3581459"))return;
   Field context=WorldRenderer.class.getDeclaredField("context");if(context.getType()!=type)throw new IllegalStateException("Audited frame-context shape changed");context.setAccessible(true);Object owner=context.get(client.worldRenderer);
   Field world=type.getDeclaredField("world"),entity=type.getDeclaredField("entity");if(world.getType()!=ClientWorld.class||entity.getType()!=Entity.class)throw new IllegalStateException("Audited frame owner fields changed");world.setAccessible(true);entity.setAccessible(true);
   Object oldWorld=world.get(owner);if(oldWorld instanceof ClientWorld previous&&client.world!=previous&&closed.contains(previous)){world.set(owner,null);LifecycleRetentionCleanup.recordOwnerRelease("fabric.closedWorldRenderContextReferencesCleared",1);}
   Object oldEntity=entity.get(owner);if(oldEntity instanceof Entity previous&&previous.getWorld() instanceof ClientWorld previousWorld&&client.world!=previousWorld&&closed.contains(previousWorld)){entity.set(owner,null);LifecycleRetentionCleanup.recordOwnerRelease("fabric.closedEntityOutlineContextReferencesCleared",1);}
  }catch(Exception|LinkageError error){LifecycleRetentionCleanup.recordOwnerRelease("fabric.renderContextCleanupRejectedOrFailed",1);SmoothFix.logPatchOnce("r4-fabric-frame-owner-rejected","Closed frame-context cleanup skipped or failed: "+error.getClass().getSimpleName());}
  finally{closed.clear();}
 }
}
