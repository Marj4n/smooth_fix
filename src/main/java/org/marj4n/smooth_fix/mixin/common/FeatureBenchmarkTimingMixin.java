package org.marj4n.smooth_fix.mixin.common;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.chunk.Chunk;
import org.marj4n.smooth_fix.benchmark.ChunkMetrics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChunkGenerator.class)
public abstract class FeatureBenchmarkTimingMixin {
    @Unique private static final ThreadLocal<ChunkMetrics.Span> smoothfix$features=new ThreadLocal<>();
    @Inject(method="generateFeatures",at=@At("HEAD")) private void smoothfix$start(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci){smoothfix$features.set(ChunkMetrics.begin(world.toServerWorld(),chunk.getPos(),"featureGeneration"));}
    @Inject(method="generateFeatures",at=@At("RETURN")) private void smoothfix$end(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci){ChunkMetrics.end(smoothfix$features.get());smoothfix$features.remove();}
}
