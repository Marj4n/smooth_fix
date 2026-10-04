package org.marj4n.smooth_fix.mixin.common;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;

/** Request native chunk work without the public API's server-thread wait loop. */
@Mixin(ServerChunkManager.class)
public interface ServerChunkFutureAccess {
    @Invoker("getChunkFuture")
    CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> smoothfix$requestChunk(int x, int z, ChunkStatus status, boolean create);
}
