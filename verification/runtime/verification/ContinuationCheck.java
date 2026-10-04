package verification;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Vec3d;
import org.marj4n.smooth_fix.benchmark.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
/** Exercise production rescue/respawn, then restore through the original journal. */
public final class ContinuationCheck {
    private static Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object run(Class<?> c)throws Exception{return field(c,"active").get(null);}
    private static void tick(Object r)throws Exception{var m=r.getClass().getDeclaredMethod("tick");m.setAccessible(true);m.invoke(r);}
    private static void ready(Class<?> c,Object r,ServerPlayerEntity p)throws Exception{
        String method=c==AdvancedBenchmark.class?"response":"acceptResponse";
        var m=c.getDeclaredMethod(method,ServerPlayerEntity.class,UUID.class,int.class,int.class,String.class);m.setAccessible(true);
        m.invoke(null,p,field(r.getClass(),"id").get(r),field(r.getClass(),"phase").getInt(r),1,"");
    }
    public static ServerPlayerEntity run(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        UUID owner=player.getUuid();String inventory=player.getInventory().writeNbt(new NbtList()).toString();Vec3d original=player.getPos();
        AdvancedPlan plan=new AdvancedPlan();plan.automaticallySelectedModStructures=0;plan.stages=new ArrayList<>(List.of(new AdvancedPlan.Stage("route","route","",0),new AdvancedPlan.Stage("save","save","",0)));plan.stages.forEach(s->s.seconds=10);
        Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/advanced_benchmark.json"),new com.google.gson.Gson().toJson(plan));
        server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress advanced start world-copy");Object r=run(AdvancedBenchmark.class);if(r==null)throw new AssertionError("rescue run missing");ready(AdvancedBenchmark.class,r,player);
        Vec3d safe=(Vec3d)field(r.getClass(),"safePosition").get(r);player.setPosition(safe.x,safe.y-9,safe.z);player.setVelocity(0,-1,0);tick(r);
        var result=((List<Map<String,Object>>)field(r.getClass(),"results").get(r)).get(0);
        if(run(AdvancedBenchmark.class)!=r || !result.containsKey("fallRescues") || player.getY()!=safe.y)throw new AssertionError("fall rescue aborted / failed");
        ready(AdvancedBenchmark.class,r,player);player.getInventory().setStack(5,new ItemStack(Items.EMERALD,9));player.getInventory().selectedSlot=5;tick(r);String stageInventory=player.getInventory().writeNbt(new NbtList()).toString();
        player.damage(player.getWorld().getDamageSources().outOfWorld(),10000);if(player.isAlive())throw new AssertionError("native death did not occur");tick(r);player=server.getPlayerManager().getPlayer(owner);
        if(run(AdvancedBenchmark.class)!=r || player==null || !player.isAlive() || !result.containsKey("playerDeathsRecovered") || !stageInventory.equals(player.getInventory().writeNbt(new NbtList()).toString()) || player.getInventory().selectedSlot!=5)throw new AssertionError("death continuation lost owner / inventory / stage");
        ready(AdvancedBenchmark.class,r,player);var complete=r.getClass().getDeclaredMethod("complete",String.class);complete.setAccessible(true);complete.invoke(r,"measured");
        var response=AdvancedBenchmark.class.getDeclaredMethod("response",ServerPlayerEntity.class,UUID.class,int.class,int.class,String.class);response.setAccessible(true);response.invoke(null,player,field(r.getClass(),"id").get(r),0,2,"{\"workloadValidated\":true}");
        if(!"failed_recovered_player_death".equals(result.get("status")))throw new AssertionError("recovered death silently passed");
        var next=r.getClass().getDeclaredMethod("next");next.setAccessible(true);next.invoke(r);if(field(r.getClass(),"phase").getInt(r)!=1)throw new AssertionError("did not continue to next stage");AdvancedBenchmark.stop();
        if(!inventory.equals(player.getInventory().writeNbt(new NbtList()).toString()) || player.getPos().squaredDistanceTo(original)>.001 || !Boolean.TRUE.equals(BenchmarkRecovery.lastVerification(owner).get("all")))throw new AssertionError("original journal recovery failed after respawn");
        System.out.println("SMOOTHFIX_VERIFY_PASS native void-fall rescue and actual out-of-world player death respawn continue same advanced run; inventory/hotbar retained, recovered death cannot PASS, next stage and original journal restoration succeed");
        Object addon=Class.forName("net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl").getMethod("getAddon",net.minecraft.server.network.ServerPlayNetworkHandler.class).invoke(null,player.networkHandler);
        ((Set<net.minecraft.util.Identifier>)field(addon.getClass().getSuperclass(),"sendableChannels").get(addon)).add(BenchmarkProtocol.CONTROL);
        server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress wither 1");r=run(ServerBenchmark.class);if(r==null)throw new AssertionError("legacy continuation run missing");ready(ServerBenchmark.class,r,player);field(r.getClass(),"readyAt").setLong(r,System.nanoTime()-11_000_000_000L);tick(r);
        if(!field(r.getClass(),"measuring").getBoolean(r))throw new AssertionError("legacy measuring not started");
        player.damage(player.getWorld().getDamageSources().outOfWorld(),10000);tick(r);player=server.getPlayerManager().getPlayer(owner);
        if(run(ServerBenchmark.class)!=r || !player.isAlive() || !field(r.getClass(),"measuring").getBoolean(r))throw new AssertionError("legacy death force quit benchmark");ready(ServerBenchmark.class,r,player);
        server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"smoothfix stress stop");
        if(!inventory.equals(player.getInventory().writeNbt(new NbtList()).toString()) || !Boolean.TRUE.equals(BenchmarkRecovery.lastVerification(owner).get("all")))throw new AssertionError("legacy original journal not restored");
        System.out.println("SMOOTHFIX_VERIFY_PASS ordinary stress native player death resumes existing recording and restores original inventory/state on stop");return player;
    }
}
