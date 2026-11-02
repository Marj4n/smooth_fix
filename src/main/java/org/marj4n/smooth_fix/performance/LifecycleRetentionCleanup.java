package org.marj4n.smooth_fix.performance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.marj4n.smooth_fix.SmoothFix;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
/** Experimental exact-installed-jar guarded teardown adapters. Never evict data belonging to a live server. */
public final class LifecycleRetentionCleanup {
 private static final boolean WORLDS=Boolean.getBoolean("smoothfix.experimental.stoppedWorldMapCleanup"),PACKETS=Boolean.getBoolean("smoothfix.experimental.stoppedPacketHandlerCleanup"),TOOLTIPS=Boolean.getBoolean("smoothfix.experimental.disconnectedTooltipPlayerCleanup");
 private static final Map<String,Boolean> audits=new ConcurrentHashMap<>();private static final Map<String,LongAdder> counts=new ConcurrentHashMap<>();
 private static final boolean CRITERIA=Boolean.getBoolean("smoothfix.experimental.stoppedAdvancementListenerCleanup"),SIMPLE=Boolean.getBoolean("smoothfix.experimental.stoppedSimpleNetworkingServerCleanup");
 private static final boolean PORTING=Boolean.getBoolean("smoothfix.experimental.stoppedPortingLibServerCleanup");
 private static final java.util.function.Supplier<MinecraftServer> NO_SERVER=()->null;
 private static final boolean FAKE=Boolean.getBoolean("smoothfix.experimental.stoppedFabricFakePlayerCleanup"),CITADEL=Boolean.getBoolean("smoothfix.experimental.stoppedCitadelServerCleanup"),XAERO=Boolean.getBoolean("smoothfix.experimental.stoppedXaeroServerCleanup");
 private static final boolean INVENTORIES=Boolean.getBoolean("smoothfix.experimental.stoppedFabricInventoryWrapperCleanup"),DISENCHANT=Boolean.getBoolean("smoothfix.experimental.stoppedDisenchantingServerCleanup");
 private static final boolean MELODY=Boolean.getBoolean("smoothfix.experimental.stoppedImmersiveMelodiesServerCleanup");
 private static final boolean NERUINA=Boolean.getBoolean("smoothfix.experimental.stoppedNeruinaLevelCleanup");
 private static final boolean BCLIB=Boolean.getBoolean("smoothfix.experimental.stoppedBclibConnectorCleanup"),FAKE_CONNECTION=Boolean.getBoolean("smoothfix.experimental.stoppedFabricFakeConnectionCleanup"),XAERO_CONFIG=Boolean.getBoolean("smoothfix.experimental.stoppedXaeroConfigServerCleanup"),PUFFISH=Boolean.getBoolean("smoothfix.experimental.stoppedPuffishDisposalCleanup"),BIOLITH=Boolean.getBoolean("smoothfix.experimental.stoppedBiolithPlacementStateCleanup");
 private static final boolean ORE=Boolean.getBoolean("smoothfix.experimental.stoppedBetterEndOreFunctionCleanup"),CLIENT_INVENTORIES=Boolean.getBoolean("smoothfix.experimental.closedClientInventoryWrapperCleanup");
 private static final boolean CREATE_DAMAGE=Boolean.getBoolean("smoothfix.experimental.stoppedCreateDamageSourceCleanup");
 private static final boolean QUEST_KILL=Boolean.getBoolean("smoothfix.experimental.stoppedQuestKillTaskCacheCleanup");
 private static final Set<MinecraftServer> stoppedTokens=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
 private static final Object SERVER_LIFECYCLE_LOCK=new Object();private static java.lang.ref.WeakReference<MinecraftServer> newestServer=new java.lang.ref.WeakReference<>(null);
 private LifecycleRetentionCleanup(){}
 private static boolean serverRequested(){return WORLDS||PACKETS||CRITERIA||SIMPLE||PORTING||FAKE||CITADEL||XAERO||INVENTORIES||DISENCHANT||MELODY||NERUINA||BCLIB||FAKE_CONNECTION||XAERO_CONFIG||PUFFISH||BIOLITH||ORE||CREATE_DAMAGE||QUEST_KILL;}
 public static void install(){
  if(!serverRequested())return;
  ServerLifecycleEvents.SERVER_STARTING.register(LifecycleRetentionCleanup::beforeServerThreadStartup);
  // Mark only: other mods may still need stopped-owner pointers in their own callbacks.
  ServerLifecycleEvents.SERVER_STOPPED.register(stoppedTokens::add);
 }
 /** Early high-priority runServer HEAD gate serializes old teardown before new mod start callbacks. */
 public static void beforeServerThreadStartup(MinecraftServer server){if(!serverRequested())return;synchronized(SERVER_LIFECYCLE_LOCK){if(newestServer.get()!=server)newestServer=new java.lang.ref.WeakReference<>(server);}}
 public static void announceServerThreadStartup(MinecraftServer server){if(!serverRequested())return;beforeServerThreadStartup(server);add("nativeEarlyServerThreadEntries",1);}
 /** Called after native shutdown() has returned, including all Fabric stop callbacks. */
 public static void afterServerShutdown(MinecraftServer server){
  if(!serverRequested()||!stoppedTokens.remove(server))return;
  synchronized(SERVER_LIFECYCLE_LOCK){
   MinecraftServer current=newestServer.get();if(current!=null&&current!=server){add("overlappingServerTeardownSkipped",1);return;}
   try{
    add("nativeAfterShutdownCleanupPasses",1);
    if(PUFFISH)guarded("puffish-stopped-category-disposal",()->StoppedOwnerAdapters.puffish(server));
    if(SIMPLE)guarded("simplenetworking-stopped-server",()->simple(server));
    if(PORTING)guarded("portinglib-stopped-server",()->porting(server));
    if(CITADEL)guarded("citadel-stopped-server",()->citadel(server));
    if(XAERO)guarded("xaero-stopped-server",()->xaero(server));
    if(DISENCHANT)guarded("disenchanting-stopped-server",()->disenchant(server));
    if(MELODY)guarded("immersive-melodies-stopped-server",()->melody(server));
    if(NERUINA)guarded("neruina-stopped-level",()->neruina(server));
    if(INVENTORIES)guarded("fabric-stopped-inventory-wrappers",()->inventories(server));
    if(WORLDS){guarded("alex-world-maps",()->alex(server));guarded("simplybows-world-maps",()->bows(server));}
    if(PACKETS)guarded("badpackets-stopped-handler",()->packets(server));
    if(CRITERIA)guarded("native-stopped-advancement-listeners",()->criteria(server));
    if(FAKE)guarded("fabric-stopped-fake-player-cache",()->fakePlayers(server));
    if(BCLIB)guarded("bclib-stopped-connector",()->StoppedOwnerAdapters.bclib(server));
    if(FAKE_CONNECTION)guarded("fabric-stopped-fake-connection",()->StoppedOwnerAdapters.fakeConnection(server));
    if(XAERO_CONFIG)guarded("xaero-stopped-config-server",()->StoppedOwnerAdapters.xaeroConfig(server));
    if(BIOLITH)guarded("biolith-stopped-placement-state",()->StoppedOwnerAdapters.biolith(server));
    if(ORE)guarded("betterend-stopped-ore-function",()->StoppedOwnerAdapters.betterEnd(server));
    if(CREATE_DAMAGE)guarded("create-stopped-damage-source",()->StoppedOwnerAdapters.createDamage(server));
    if(QUEST_KILL)guarded("questkill-stopped-lookup",()->StoppedOwnerAdapters.questKill(server));
   }finally{if(newestServer.get()==server)newestServer.clear();}
  }
 }
 public static void disconnectedClientWorld(World previous){if(TOOLTIPS&&previous!=null)guarded("sortilege-disconnected-client",()->clearPlayer(p->p.getWorld()==previous));}
 public static Map<String,Object> snapshot(){Map<String,Object> out=new LinkedHashMap<>();out.put("experimentalWorldMapsRequested",WORLDS);out.put("experimentalPacketHandlersRequested",PACKETS);out.put("experimentalTooltipPlayerRequested",TOOLTIPS);out.put("experimentalAdvancementListenersRequested",CRITERIA);out.put("experimentalSimpleNetworkingRequested",SIMPLE);out.put("experimentalPortingLibRequested",PORTING);out.put("experimentalFabricFakePlayersRequested",FAKE);out.put("experimentalCitadelRequested",CITADEL);out.put("experimentalXaeroRequested",XAERO);out.put("experimentalFabricRenderContextRequested",Boolean.getBoolean("smoothfix.experimental.disconnectedFabricRenderContextCleanup"));out.put("experimentalFabricInventoryWrappersRequested",INVENTORIES);out.put("experimentalDisenchantingServerRequested",DISENCHANT);out.put("experimentalImmersiveMelodiesServerRequested",MELODY);out.put("experimentalNeruinaLevelRequested",NERUINA);out.put("experimentalBclibConnectorRequested",BCLIB);out.put("experimentalFakeConnectionRequested",FAKE_CONNECTION);out.put("experimentalXaeroConfigRequested",XAERO_CONFIG);out.put("experimentalPuffishDisposalRequested",PUFFISH);out.put("experimentalBiolithPlacementRequested",BIOLITH);out.put("experimentalBetterEndOreRequested",ORE);out.put("experimentalClosedClientInventoriesRequested",CLIENT_INVENTORIES);out.put("experimentalCreateDamageRequested",CREATE_DAMAGE);out.put("experimentalQuestKillCacheRequested",QUEST_KILL);out.put("audits",Map.copyOf(audits));Map<String,Long> result=new TreeMap<>();counts.forEach((k,v)->result.put(k,v.sum()));out.put("counts",result);return out;}
 private interface Work{void run()throws Exception;}
 static void recordOwnerRelease(String name,long n){add(name,n);}
 private static void add(String name,long n){counts.computeIfAbsent(name,k->new LongAdder()).add(n);}
 private static void guarded(String name,Work work){try{work.run();}catch(Exception|LinkageError e){add(name+".rejectedOrFailed",1);SmoothFix.logPatchOnce("r4-"+name+"-rejected","Lifecycle cleanup component skipped or failed: "+name+" ("+e.getClass().getSimpleName()+")");}}
 static boolean auditedJar(String file,String sha)throws Exception{return audit(file,sha);}
 private static boolean audit(String file,String sha)throws Exception{
  Boolean known=audits.get(file);if(known!=null)return known;Path path=FabricLoader.getInstance().getGameDir().resolve("mods").resolve(file).normalize();boolean accepted=false;
  if(Files.isRegularFile(path)){MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}accepted=HexFormat.of().formatHex(digest.digest()).equals(sha);}
  audits.put(file,accepted);if(!accepted)throw new IllegalStateException("Unaudited installed jar: "+file);return true;
 }
 private static Map<?,?> map(String owner,String name)throws Exception{Class<?> c=Class.forName(owner);Field f=c.getDeclaredField(name);if(!Modifier.isStatic(f.getModifiers())||!Map.class.isAssignableFrom(f.getType()))throw new IllegalStateException("Audited map shape changed");f.setAccessible(true);Object value=f.get(null);if(!(value instanceof Map<?,?> result))throw new IllegalStateException("Missing native map");return result;}
 private static List<ServerWorld> ownedWorlds(MinecraftServer ended,Map<?,?> map){List<ServerWorld> worlds=new ArrayList<>();for(Object key:map.keySet().toArray())if(key instanceof ServerWorld w&&w.getServer()==ended)worlds.add(w);return worlds;}
 private static void alex(MinecraftServer ended)throws Exception{
  if(!audit("alexsmobs-1.22.9.jar","16bc5bcc19db9029c24f951346ec71098e6f5b0afef96e61894a2e9407aad37c"))return;
  for(String[] target:new String[][]{{"com.github.alexthe666.alexsmobs.event.ServerEvents","BEACHED_CACHALOT_WHALE_SPAWNER_MAP"},{"com.github.alexthe666.alexsmobs.world.AMWorldData","dataMap"}}){Map<?,?> map=map(target[0],target[1]);int removed=0;for(ServerWorld w:ownedWorlds(ended,map)){map.remove(w);removed++;}add("alex."+target[1]+".stoppedWorldEntriesRemoved",removed);}
 }
 private static void bows(MinecraftServer ended)throws Exception{
  if(!audit("simplybows-0.1.4.jar","f642fbe765dd41075ae6f0dd6f52c203c2755952ee62e796211027495bd8fc03"))return;
  String owner="net.sweenus.simplybows.world.EarthChaosSunderManager";Map<?,?> fields=map(owner,"ACTIVE_FIELDS"),ticks=map(owner,"NEXT_ORPHAN_VISUAL_CLEANUP_TICK");Set<ServerWorld> worlds=Collections.newSetFromMap(new IdentityHashMap<>());worlds.addAll(ownedWorlds(ended,fields));worlds.addAll(ownedWorlds(ended,ticks));Method nativeClear=Class.forName(owner).getMethod("clearWorld",ServerWorld.class);
  for(ServerWorld world:worlds){nativeClear.invoke(null,world);if(fields.containsKey(world)||ticks.containsKey(world))throw new IllegalStateException("Native clearWorld did not retire both owned entries");add("simplybows.nativeClearWorldCalls",1);}
 }
 private static void packets(MinecraftServer ended)throws Exception{
  if(!audit("badpackets-fabric-0.4.3.jar","4a6b84ce20d7a35d17ef86fe29417de6f25704f478ee2f5f92c7c2ee76ea899e"))return;
  Class<?> registryClass=Class.forName("lol.bai.badpackets.impl.registry.ChannelRegistry"),handlerClass=Class.forName("lol.bai.badpackets.impl.handler.ServerPacketHandler");Object registry=registryClass.getField("C2S").get(null);Field handlers=registryClass.getDeclaredField("handlers"),server=handlerClass.getDeclaredField("server");handlers.setAccessible(true);server.setAccessible(true);if(!(handlers.get(registry) instanceof Set<?> set))throw new IllegalStateException("Native handler registry shape changed");Method disconnect=handlerClass.getMethod("onDisconnect");
  for(Object handler:set.toArray())if(handlerClass.isInstance(handler)&&server.get(handler)==ended){disconnect.invoke(handler);if(set.contains(handler))throw new IllegalStateException("Native onDisconnect did not unregister stopped handler");add("badpackets.nativeStoppedHandlerDisconnectCalls",1);}
 }
 private static void criteria(MinecraftServer ended)throws Exception{
  if(!"1.20.1".equals(net.minecraft.SharedConstants.getGameVersion().getName()))throw new IllegalStateException("Unaudited native Minecraft version");
  List<Field> maps=Arrays.stream(net.minecraft.advancement.criterion.AbstractCriterion.class.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())&&Map.class.isAssignableFrom(f.getType())).toList();
  List<Field> managers=Arrays.stream(net.minecraft.advancement.PlayerAdvancementTracker.class.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())&&f.getType()==net.minecraft.server.PlayerManager.class).toList();
  if(maps.size()!=1||managers.size()!=1)throw new IllegalStateException("Audited native criterion/tracker shape changed");
  Field listeners=maps.get(0),manager=managers.get(0);listeners.setAccessible(true);manager.setAccessible(true);
  Set<net.minecraft.advancement.PlayerAdvancementTracker> owned=Collections.newSetFromMap(new IdentityHashMap<>());List<Map<?,?>> nativeMaps=new ArrayList<>();
  for(var criterion:net.minecraft.advancement.criterion.Criteria.getCriteria())if(criterion instanceof net.minecraft.advancement.criterion.AbstractCriterion<?>){
   if(!(listeners.get(criterion) instanceof Map<?,?> progressions))throw new IllegalStateException("Missing native listener map");nativeMaps.add(progressions);
   for(Object key:progressions.keySet().toArray())if(key instanceof net.minecraft.advancement.PlayerAdvancementTracker tracker&&manager.get(tracker) instanceof net.minecraft.server.PlayerManager owners&&owners.getServer()==ended)owned.add(tracker);
  }
  audits.put("minecraft:1.20.1:criterionOwnershipShape",true);
  // The native logout/reload API retires transient listeners, not the saved progress map.
  for(var tracker:owned){tracker.clearCriteria();add("criteria.nativeStoppedTrackerClearCalls",1);}
  for(Map<?,?> map:nativeMaps)for(var tracker:owned)if(map.containsKey(tracker))throw new IllegalStateException("Native clearCriteria left stopped listener references");
  add("criteria.nativeRegisteredMapsInspected",nativeMaps.size());
 }
 static boolean auditedClass(Class<?> type,String expected)throws Exception{
  String resource=type.getName().replace('.','/')+".class",key="class:"+resource;Boolean accepted=audits.get(key);
  if(accepted==null){MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=type.getClassLoader().getResourceAsStream(resource)){if(input==null)throw new IllegalStateException("Missing selected class resource");byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}accepted=HexFormat.of().formatHex(digest.digest()).equals(expected);audits.put(key,accepted);}
  if(!accepted)throw new IllegalStateException("Unknown selected native library class");return true;
 }
 private static java.lang.invoke.VarHandle serverField(Class<?> type,String name,Class<?> fieldType)throws Exception{
  Field f=type.getDeclaredField(name);if(!Modifier.isStatic(f.getModifiers())||f.getType()!=fieldType)throw new IllegalStateException("Audited server reference shape changed");
  return java.lang.invoke.MethodHandles.privateLookupIn(type,java.lang.invoke.MethodHandles.lookup()).findStaticVarHandle(type,name,fieldType);
 }
 private static void simple(MinecraftServer ended)throws Exception{
  Class<?> type=Class.forName("me.pepperbell.simplenetworking.SimpleNetworking");if(!auditedClass(type,"fc7864e13a3516211f36f52e29cd4c05a8d009c04579e8207154d99d52db0db4"))return;
  // Atomic identity comparison cannot overwrite a newer server installed concurrently.
  if(serverField(type,"currentServer",MinecraftServer.class).compareAndSet(ended,null))add("simplenetworking.stoppedServerReferencesCleared",1);
 }
 private static void porting(MinecraftServer ended)throws Exception{
  Class<?> hooks=Class.forName("io.github.fabricators_of_create.porting_lib.util.ServerLifecycleHooks"),provider=Class.forName("io.github.fabricators_of_create.porting_lib.util.LogicalSidedProvider");
  if(!auditedClass(hooks,"2b8e75439e87ad6e3d8898d00c67869b5aca0172e8ffc06303756ad02c5e5f56")||!auditedClass(provider,"c676599f660d45371081c3098aae4814b76a944e9a377941d46b6848f8ec4cd3"))return;
  if(serverField(hooks,"currentServer",MinecraftServer.class).compareAndSet(ended,null))add("portinglib.stoppedServerReferencesCleared",1);
  var field=serverField(provider,"server",java.util.function.Supplier.class);Object previous=field.get();
  if(previous instanceof java.util.function.Supplier<?> supplier&&supplier.get()==ended&&field.compareAndSet(previous,NO_SERVER))add("portinglib.stoppedServerSuppliersReleased",1);
 }
 static void closedClientInventories(World previous){if(CLIENT_INVENTORIES&&previous!=null)guarded("fabric-closed-client-inventory-wrappers",()->releaseInventoryOwners(world->world==previous,"fabric.closedClientInventoryWrapperEntriesRemoved"));}
 private static void inventories(MinecraftServer ended)throws Exception{releaseInventoryOwners(world->world instanceof ServerWorld owner&&owner.getServer()==ended,"fabric.stoppedInventoryWrapperEntriesRemoved");}
 private static void releaseInventoryOwners(java.util.function.Predicate<World> matches,String counter)throws Exception{
  Class<?> type=Class.forName("net.fabricmc.fabric.impl.transfer.item.InventoryStorageImpl");
  if(!auditedClass(type,"6a385fa0f361768083e61cb32c5db11f7744e94718d104ddd63674d777f134f8"))return;
  Field field=type.getDeclaredField("WRAPPERS");if(!Modifier.isStatic(field.getModifiers())||!Map.class.isAssignableFrom(field.getType()))throw new IllegalStateException("Audited inventory cache shape changed");field.setAccessible(true);
  if(!(field.get(null) instanceof ConcurrentMap<?,?> wrappers))throw new IllegalStateException("Audited concurrent inventory cache changed");
  for(Object key:wrappers.keySet().toArray()){
   World world=null;
   if(key instanceof net.minecraft.block.entity.BlockEntity block)world=block.getWorld();
   else if(key instanceof net.minecraft.entity.Entity entity)world=entity.getWorld();
   else if(key instanceof net.minecraft.entity.player.PlayerInventory inventory)world=inventory.player.getWorld();
   else if(key.getClass().getName().equals("dev.shadowsoffire.apotheosis.ench.table.ApothEnchantTile$1")){
    if(!audit("zenith_renewed-2.0.2.jar","e3a589ebc4ca43b4f02a8a18259e8b5fd1e796fbb0557d28edf0461bef11777c"))continue;
    Field parent=key.getClass().getDeclaredField("this$0");if(!parent.getType().getName().equals("dev.shadowsoffire.apotheosis.ench.table.ApothEnchantTile"))throw new IllegalStateException("Audited Zenith inventory owner changed");parent.setAccessible(true);if(!(parent.get(key) instanceof net.minecraft.block.entity.BlockEntity block))throw new IllegalStateException("Missing audited Zenith block owner");world=block.getWorld();
   }
   if(matches.test(world)){Object previous=wrappers.get(key);if(previous!=null&&wrappers.remove(key,previous))add(counter,1);}
  }
 }
 private static void neruina(MinecraftServer ended)throws Exception{
  // Installed Neruina marks its persistent state dirty at native stopServer HEAD.
  // This adapter runs only AFTER native world save and all shutdown callbacks.
  // Never clear its ticking entries, codec, persistent state or saved files.
  if(!audit("neruina-3.3.3+1.20.1-fabric.jar","2f43debd78cd92c704b404474d5dec7bf80381970ba09ead9943cd9705b98adc"))return;
  Class<?> type=Class.forName("com.bawnorton.neruina.handler.PersitanceHandler");
  if(!auditedClass(type,"6785402688433f1f061230664bc11d660922f1ac25b0b11729d6180c7bfe5144"))return;
  var field=serverField(type,"level",ServerWorld.class);Object previous=field.get();
  if(previous instanceof ServerWorld world&&world.getServer()==ended&&field.compareAndSet(previous,(ServerWorld)null))add("neruina.stoppedLevelReferencesCleared",1);
 }
 private static void melody(MinecraftServer ended)throws Exception{
  Class<?> type=Class.forName("immersive_melodies.resources.ServerMelodyManager");
  if(!auditedClass(type,"8995c99b7dc58c7e2a1b843802984f32e86ee809e0ac6d947871bf4eeaedc758"))return;
  // Native SERVER_STARTING restores this pointer on every join. Retire only our
  // fully stopped owner; do not clear melody data, playback, files or settings.
  if(serverField(type,"server",MinecraftServer.class).compareAndSet(ended,(MinecraftServer)null))add("immersiveMelodies.stoppedServerReferencesCleared",1);
 }
 private static void disenchant(MinecraftServer ended)throws Exception{
  Class<?> type=Class.forName("fabric.com.cursee.disenchanting_table.DisenchantingTableServerFabric");if(!auditedClass(type,"d019eb118eb029fe6de7f1b1bf24dfceedbe0029d79458bc0030542cf0270e25"))return;
  if(serverField(type,"SERVER",MinecraftServer.class).compareAndSet(ended,(MinecraftServer)null))add("disenchanting.stoppedServerReferencesCleared",1);
 }
 private static void fakePlayers(MinecraftServer ended)throws Exception{
  Class<?> type=Class.forName("net.fabricmc.fabric.api.entity.FakePlayer"),keyType=Class.forName("net.fabricmc.fabric.api.entity.FakePlayer$FakePlayerKey");
  if(!auditedClass(type,"ebc9d83bb49341cc0bde2d6ad3452ab3596b452ec18a45e904062fa15b773fc2")||!auditedClass(keyType,"1613de039ae408a1a43fbb19444e731baed4d4192f2578adf2071cb6e1759466"))return;
  Field cache=type.getDeclaredField("FAKE_PLAYER_MAP"),world=keyType.getDeclaredField("world");if(!Modifier.isStatic(cache.getModifiers())||world.getType()!=ServerWorld.class)throw new IllegalStateException("Audited fake-player cache shape changed");cache.setAccessible(true);world.setAccessible(true);
  if(!(cache.get(null) instanceof java.util.concurrent.ConcurrentMap<?,?> map))throw new IllegalStateException("Audited concurrent fake-player cache changed");
  for(Object key:map.keySet().toArray())if(keyType.isInstance(key)&&world.get(key) instanceof ServerWorld owner&&owner.getServer()==ended){Object previous=map.get(key);if(previous!=null&&map.remove(key,previous))add("fabric.stoppedFakePlayerWorldEntriesRemoved",1);}
 }
 private static void citadel(MinecraftServer ended)throws Exception{
  if(!audit("smooth_bridge-1.0.0+1.20.1-EXPERIMENTAL.jar","b6d025080b5d732aefb7395d6f49e8f054a24e02fb5048a34c7858a35cbb4e83"))return;
  Class<?> type=Class.forName("com.github.alexthe666.citadel.ServerProxy");if(serverField(type,"minecraftServer",MinecraftServer.class).compareAndSet(ended,(MinecraftServer)null))add("citadel.stoppedServerReferencesCleared",1);
 }
 private static void xaero(MinecraftServer ended)throws Exception{
  Class<?> type=Class.forName("xaero.pac.common.event.CommonEvents");if(!auditedClass(type,"2d4ac8b1799d56d00b7792c8f11d219f6753a020e5b98fb583def57e17499fd8"))return;
  if(serverField(type,"lastServerStarted",MinecraftServer.class).compareAndSet(ended,(MinecraftServer)null))add("xaero.stoppedServerReferencesCleared",1);
 }
 private static void clearPlayer(java.util.function.Predicate<PlayerEntity> owned)throws Exception{
  if(!audit("Sortilege-1.20.1-9.0.jar","846f63f1ffec981d4916c5f3f104ad2e177da7b9a22bee30b25bdafd2b966d10"))return;
  Field player=ItemStack.class.getDeclaredField("sorti_player"),stack=ItemStack.class.getDeclaredField("sorti_stack");if(!Modifier.isStatic(player.getModifiers())||!PlayerEntity.class.isAssignableFrom(player.getType())||!Modifier.isStatic(stack.getModifiers())||stack.getType()!=ItemStack.class)throw new IllegalStateException("Injected Sortilege tooltip shape changed");player.setAccessible(true);stack.setAccessible(true);Object value=player.get(null);if(value instanceof PlayerEntity p&&owned.test(p)){stack.set(null,null);player.set(null,null);add("sortilege.closedPlayerTooltipReferencesCleared",1);}
 }
}
