package org.marj4n.smooth_fix.mixin.common;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.*;
import net.minecraft.nbt.NbtCompound;
import org.marj4n.smooth_fix.benchmark.ChunkMetrics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChunkSerializer.class)
public abstract class ChunkBenchmarkTimingMixin {
    @Unique private static final ThreadLocal<ChunkMetrics.Span> smoothfix$read=new ThreadLocal<>(),smoothfix$write=new ThreadLocal<>();
    @Inject(method="deserialize",at=@At("HEAD")) private static void smoothfix$readStart(ServerWorld world,PointOfInterestStorage poi,ChunkPos pos,NbtCompound nbt,CallbackInfoReturnable<ProtoChunk> ci){smoothfix$read.set(ChunkMetrics.begin(world,pos,"nbtDecode"));}
    @Inject(method="deserialize",at=@At("RETURN")) private static void smoothfix$readEnd(ServerWorld world,PointOfInterestStorage poi,ChunkPos pos,NbtCompound nbt,CallbackInfoReturnable<ProtoChunk> ci){ChunkMetrics.end(smoothfix$read.get());smoothfix$read.remove();}
    @Inject(method="serialize",at=@At("HEAD")) private static void smoothfix$writeStart(ServerWorld world,Chunk chunk,CallbackInfoReturnable<NbtCompound> ci){smoothfix$write.set(ChunkMetrics.begin(world,chunk.getPos(),"nbtEncode"));}
    @Inject(method="serialize",at=@At("RETURN")) private static void smoothfix$writeEnd(ServerWorld world,Chunk chunk,CallbackInfoReturnable<NbtCompound> ci){ChunkMetrics.end(smoothfix$write.get());smoothfix$write.remove();}
}
