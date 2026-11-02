package org.marj4n.smooth_fix.performance;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.marj4n.smooth_fix.SmoothFix;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Exact installed-source, opt-in retirement of two obsolete client-world memos. */
public final class ClosedClientMemoOwners {
 private static final boolean PING=Boolean.getBoolean("smoothfix.experimental.closedPingWheelWorldMemoCleanup");
 private static final boolean GLOAM=Boolean.getBoolean("smoothfix.experimental.closedSimplySwordsWorldMemoCleanup");
 private ClosedClientMemoOwners(){}
 static boolean requested(){return PING||GLOAM;}
 static void release(MinecraftClient client,ClientWorld previous){
  if(previous==null||client.world==previous||!client.isOnThread())return;
  if(PING)attempt("pingwheel",()->ping(previous));
  if(GLOAM)attempt("simplyswords",()->gloam(previous));
 }
 private interface Action{void run()throws Exception;}
 private static void attempt(String name,Action work){
  try{work.run();}catch(Exception|LinkageError e){
   LifecycleRetentionCleanup.recordOwnerRelease(name+".closedWorldMemoRejectedOrFailed",1);
   SmoothFix.logPatchOnce("closed-world-memo-"+name,"Closed world memo cleanup skipped or failed: "+name+" ("+e.getClass().getSimpleName()+")");
  }
 }
 private static Class<?> audited(String mod,String version,String owner,String hash)throws Exception{
  var installed=FabricLoader.getInstance().getModContainer(mod);
  if(installed.isEmpty()||!installed.get().getMetadata().getVersion().getFriendlyString().equals(version))throw new IllegalStateException("Unaudited mod version");
  Class<?> type=Class.forName(owner);
  if(!LifecycleRetentionCleanup.auditedClass(type,hash))throw new IllegalStateException("Unaudited class");
  return type;
 }
 private static Field memo(Class<?> type,String name)throws Exception{
  Field field=type.getDeclaredField(name);
  if(!Modifier.isStatic(field.getModifiers())||Modifier.isFinal(field.getModifiers())||field.getType()!=ClientWorld.class)throw new IllegalStateException("Audited memo shape changed");
  field.setAccessible(true);return field;
 }
 private static void ping(ClientWorld previous)throws Exception{
  Class<?> type=audited("pingwheel","1.12.1","nx.pingwheel.common.core.GameContext","2cf01d90b0989a8d11ef65d9fe831c78035934614430544aff4b9184353eb550");
  Field field=memo(type,"lastWorld");
  if(field.get(null)!=previous)return;
  // Native lookup uses MinecraftClient.world; lastWorld only memoizes dimension identity.
  field.set(null,null);
  LifecycleRetentionCleanup.recordOwnerRelease("pingwheel.closedWorldMemosReleased",1);
 }
 private static void gloam(ClientWorld previous)throws Exception{
  Class<?> type=audited("simplyswords","1.70.2-1.20.1","net.sweenus.simplyswords.client.renderer.ObserverStatusVisualRenderer","bf6f1526ca9e838d647c80b519b99b986cb9a930f21661d5be1877aec07fc0ff");
  Field field=memo(type,"gloamWorld");
  if(field.get(null)!=previous)return;
  var nativeReset=type.getMethod("beginWorldFrame");
  if(!Modifier.isStatic(nativeReset.getModifiers())||nativeReset.getReturnType()!=void.class)throw new IllegalStateException("Audited native reset shape changed");
  // Delegate to the mod's existing world-identity transition, including tendril state.
  nativeReset.invoke(null);
  if(field.get(null)==previous)throw new IllegalStateException("Native reset retained closed owner");
  LifecycleRetentionCleanup.recordOwnerRelease("simplyswords.nativeClosedWorldMemoResets",1);
 }
}
