package org.marj4n.smooth_fix.mixin.common;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import org.marj4n.smooth_fix.benchmark.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(Explosion.class)
public abstract class ExplosionBenchmarkMixin {
    @Shadow @Final private World world;
    @Shadow @Final private Entity entity;
    @Shadow @Final private it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockPos> affectedBlocks;
    @Unique private Map<BlockPos,BlockState> smoothfix$before;
    @Inject(method="affectWorld",at=@At("HEAD")) private void smoothfix$before(boolean particles,CallbackInfo ci){
        if(!(world instanceof ServerWorld server) || !AdvancedBenchmark.ownsActor(entity))return;
        ChunkMetrics.count(server,"explosions",1);ChunkMetrics.count(server,"explosionAffectedPositions",affectedBlocks.size());
        smoothfix$before=new HashMap<>();for(BlockPos pos:affectedBlocks)if(smoothfix$before.size()<8192 && !world.getBlockState(pos).isAir())smoothfix$before.put(pos.toImmutable(),world.getBlockState(pos));
    }
    @Inject(method="affectWorld",at=@At("RETURN")) private void smoothfix$after(boolean particles,CallbackInfo ci){
        if(smoothfix$before==null)return;int destroyed=0;for(var entry:smoothfix$before.entrySet())if(world.getBlockState(entry.getKey()).isAir())destroyed++;
        if(destroyed>0)ChunkMetrics.count((ServerWorld)world,"explosionDestroyedBlocks",destroyed);smoothfix$before=null;
    }
}
