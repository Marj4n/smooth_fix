package verification;
import verification.mixin.LivingJumpAccess;

import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.util.function.BiConsumer;
import org.marj4n.smooth_fix.benchmark.*;
import java.util.*;

/** Native collision geometry in a disposable, elevated server fixture. */
public final class GroundNavigationCheck {
    private static List<GroundRoutePlanner.Point> path(GroundCollisionTerrain terrain,GroundRoutePlanner.Point start,double x,double z) {
        var search=new GroundRoutePlanner.Search(terrain,start,x,z);
        for(int tick=0;tick<100;tick++)if(search.advance(12,750_000L))return search.path();
        throw new AssertionError("bounded path search did not finish");
    }
    public static void run(ServerPlayerEntity player) {
        var world=player.getServerWorld();int ox=-120,oz=-320,y=240;
        Map<BlockPos,BlockState> original=new LinkedHashMap<>();
        BiConsumer<BlockPos,BlockState> set=(pos,state)->{original.putIfAbsent(pos,world.getBlockState(pos));world.setBlockState(pos,state);};
        try {
        for(int dx=-13;dx<=13;dx++)for(int dz=-13;dz<=13;dz++){
            world.getChunk((ox+dx)>>4,(oz+dz)>>4);set.accept(new BlockPos(ox+dx,y-1,oz+dz),Blocks.STONE.getDefaultState());
            for(int dy=0;dy<4;dy++)set.accept(new BlockPos(ox+dx,y+dy,oz+dz),Blocks.AIR.getDefaultState());
        }
        var terrain=new GroundCollisionTerrain(world,player);var start=new GroundRoutePlanner.Point(ox,y,oz);
        for(int dz=-3;dz<=3;dz++)for(int dy=0;dy<2;dy++)set.accept(new BlockPos(ox+2,y+dy,oz+dz),Blocks.STONE.getDefaultState());
        var around=path(terrain,start,ox+6.5,oz+.5);
        if(around.isEmpty() || around.get(around.size()-1).x()!=ox+6 || around.stream().noneMatch(p->Math.abs(p.z()-oz)>=4))throw new AssertionError("did not detour around tall wall: "+around);
        if(terrain.step(new GroundRoutePlanner.Point(ox+1,y,oz),1,0)!=null)throw new AssertionError("two-block wall treated as one jump");
        set.accept(new BlockPos(ox,y,oz+1),Blocks.STONE.getDefaultState());
        var step=terrain.step(start,0,1);if(step==null || step.y()!=y+1)throw new AssertionError("one-block step rejected");
        set.accept(new BlockPos(ox,y+2,oz+1),Blocks.STONE.getDefaultState());if(terrain.step(start,0,1)!=null)throw new AssertionError("step under low ceiling accepted");
        set.accept(new BlockPos(ox-1,y,oz),Blocks.STONE_SLAB.getDefaultState());var slab=terrain.step(start,-1,0);
        if(slab==null || slab.y()!=y+.5)throw new AssertionError("slab collision height ignored");
        set.accept(new BlockPos(ox,y-1,oz-1),Blocks.AIR.getDefaultState());if(terrain.step(start,0,-1)!=null)throw new AssertionError("unsupported pit accepted");
        set.accept(new BlockPos(ox,y-1,oz-1),Blocks.LAVA.getDefaultState());if(terrain.step(start,0,-1)!=null)throw new AssertionError("lava accepted");
        int chunks=world.getChunkManager().getLoadedChunkCount();if(terrain.step(new GroundRoutePlanner.Point(900000,y,900000),1,0)!=null || chunks!=world.getChunkManager().getLoadedChunkCount())throw new AssertionError("navigation loaded an unavailable chunk");
        // The same production follower drives native PlayerEntity.travel around the wall.
        set.accept(new BlockPos(ox,y,oz+1),Blocks.AIR.getDefaultState());set.accept(new BlockPos(ox,y+2,oz+1),Blocks.AIR.getDefaultState());
        var saved=player.getPos();float savedYaw=player.getYaw(),savedPitch=player.getPitch();boolean flying=player.getAbilities().flying;
        try {
            player.setPosition(ox+.5,y,oz+.5);player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);player.setOnGround(true);player.getAbilities().flying=false;
            var follower=new GroundNavigator();var metrics=new ActionMetrics();
            for(int tick=0;tick<500 && Math.hypot(player.getX()-(ox+6.5),player.getZ()-(oz+.5))>.65;tick++){
                var input=follower.tick(world,player,ox+6.5,oz+.5,metrics);player.setSprinting(input.sprint());
                if(input.jump())throw new AssertionError("flat detour spuriously jumped");
                player.travel(new net.minecraft.util.math.Vec3d(0,0,input.forward()?1:0));
            }
            if(Math.hypot(player.getX()-(ox+6.5),player.getZ()-(oz+.5))>.8)throw new AssertionError("native follower remained stuck: "+player.getPos()+" "+follower.status()+" "+metrics.snapshot());
            if(follower.distance()<8 || follower.extent()<5)throw new AssertionError("native traversal telemetry missing: "+follower.distance()+" / "+follower.extent());
            System.out.println("SMOOTHFIX_VERIFY_PASS production ground follower drives native player travel around a two-block wall to target without jump spam; real displacement and distance recorded");
            int sx=ox-8,sz=oz+8;for(int dx=1;dx<=4;dx++)for(int dz=-2;dz<=2;dz++)set.accept(new BlockPos(sx+dx,y,sz+dz),Blocks.STONE.getDefaultState());
            player.setPosition(sx+.5,y,sz+.5);player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);player.setOnGround(true);
            var stairFollower=new GroundNavigator();var stairMetrics=new ActionMetrics();int pulses=0;
            for(int tick=0;tick<300 && (player.getX()<sx+2 || player.getY()<y+.9);tick++){
                var input=stairFollower.tick(world,player,sx+3.5,sz+.5,stairMetrics);player.setSprinting(input.sprint());
                if(input.jump()){((LivingJumpAccess)player).fixtureJump();pulses++;}
                player.travel(new net.minecraft.util.math.Vec3d(0,0,input.forward()?1:0));
            }
            if(player.getX()<sx+2 || player.getY()<y+.9 || pulses==0 || pulses>3)throw new AssertionError("one-block native jump failed or spammed: "+player.getPos()+" pulses="+pulses+" state="+stairFollower.status());
            System.out.println("SMOOTHFIX_VERIFY_PASS production follower climbs one-block platform through native jump/travel with bounded jump pulses");
        } finally {player.setPosition(saved.x,saved.y,saved.z);player.setYaw(savedYaw);player.setPitch(savedPitch);player.setSprinting(false);player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);player.getAbilities().flying=flying;}
        } finally {original.forEach((pos,state)->world.setBlockState(pos,state));}
        System.out.println("SMOOTHFIX_VERIFY_PASS native ground collision path detours around tall wall; one-block step and half-slab recognized; low ceiling, pit, lava and unavailable chunks rejected without chunk loading");
    }
    private GroundNavigationCheck() { }
}
