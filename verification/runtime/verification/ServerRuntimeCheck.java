package verification;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import java.lang.reflect.Field;
import java.util.UUID;

/** All checks run on the real server thread against the transformed production classes. */
public class ServerRuntimeCheck implements ModInitializer {
    @Override public void onInitialize() {
        if(FabricLoader.getInstance().getEnvironmentType()!=EnvType.SERVER || Boolean.getBoolean("smoothfix.verify.advanced"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(this::run);
    }
    private void run(MinecraftServer server) {
        try {
            SnapshotCheck.run();
            org.marj4n.smooth_fix.diagnostics.TickCadence cadence=new org.marj4n.smooth_fix.diagnostics.TickCadence();
            cadence.beginTick(1_000_000_000L);cadence.beginTick(1_050_000_000L);cadence.beginTick(1_539_000_000L);
            java.util.Map<String,Object> cadenceReport=cadence.snapshot();
            if(((Number)cadenceReport.get("maxMs")).doubleValue()!=489 || ((Number)cadenceReport.get("samplesOver100Ms")).longValue()!=1)throw new AssertionError("outside-callback wall-clock pause lost");
            java.util.Map<String,Object> before=java.util.Map.of("garbageCollectors",java.util.List.of(java.util.Map.of("name","test","collections",3L,"timeMs",150L)));
            java.util.Map<String,Object> after=new java.util.LinkedHashMap<>(java.util.Map.of("garbageCollectors",java.util.List.of(java.util.Map.of("name","test","collections",4L,"timeMs",589L))));
            org.marj4n.smooth_fix.diagnostics.MemoryReport.addCollectorDeltas(after,before);
            java.util.Map<?,?> delta=(java.util.Map<?,?>)((java.util.List<?>)after.get("garbageCollectorDeltas")).get(0);
            if(!Long.valueOf(439).equals(delta.get("timeMs")) || !Long.valueOf(1).equals(delta.get("collections")))throw new AssertionError("GC cumulative counters were not subtracted");
            System.out.println("SMOOTHFIX_VERIFY_PASS synthetic outside-callback 439ms stall captured as 489ms cadence; phase GC counters use deltas");
            if(!(net.minecraft.util.Util.getMainWorkerExecutor() instanceof java.util.concurrent.ForkJoinPool pool)
                    || pool.getParallelism()!=2)throw new AssertionError("final server worker budget not applied");
            System.out.println("SMOOTHFIX_VERIFY_PASS final server main worker parallelism=2");
            ServerPlayerEntity player = new ServerPlayerEntity(server,server.getOverworld(),new GameProfile(UUID.randomUUID(),"SmoothFixFixture"));
            ServerPlayNetworkHandler handler = new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),player);
            player.networkHandler=handler;
            player.setPosition(0,200,0);
            server.getOverworld().onPlayerConnected(player);
            handler.syncWithPlayerPosition();
            field("ticks","field_14118").setInt(handler,1);
            handler.requestTeleport(500,200,500,0,0);
            int id=field("requestedTeleportId","field_14123").getInt(handler);
            handler.onTeleportConfirm(new TeleportConfirmC2SPacket(id-1));
            if(field("lastTickX","field_14130").getDouble(handler)!=0)throw new AssertionError("invalid confirmation reset anchor");
            handler.onTeleportConfirm(new TeleportConfirmC2SPacket(id));
            if(field("lastTickX","field_14130").getDouble(handler)!=500
                    || field("lastTickZ","field_14128").getDouble(handler)!=500)throw new AssertionError("valid confirmation did not reset anchor");
            handler.onPlayerMove(new PlayerMoveC2SPacket.Full(500.1,200,500,0,0,false));
            if(java.lang.Math.abs(player.getX()-500.1)>0.0001)throw new AssertionError("ordinary movement rejected");
            handler.onPlayerMove(new PlayerMoveC2SPacket.Full(900,200,500,0,0,false));
            if(player.getX()>501)throw new AssertionError("movement validation was bypassed");
            server.getOverworld().removePlayer(player,net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            System.out.println("SMOOTHFIX_VERIFY_PASS accepted teleport anchor, bad confirmation ignored, ordinary movement accepted, excessive movement rejected");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"smoothfix report");
        } catch(Throwable error) {
            System.out.println("SMOOTHFIX_VERIFY_FAIL "+error);
            error.printStackTrace();
        }
    }
    private static Field field(String named,String intermediary)throws Exception {
        String name=FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace().equals("named")?named:intermediary;
        Field field=ServerPlayNetworkHandler.class.getDeclaredField(name);field.setAccessible(true);return field;
    }
}
