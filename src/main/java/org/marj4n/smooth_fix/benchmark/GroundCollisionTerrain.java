package org.marj4n.smooth_fix.benchmark;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Collision-shape walkability, restricted to already loaded chunks on either side. */
public final class GroundCollisionTerrain implements GroundRoutePlanner.Terrain {
    private final World world;
    private final Entity body;
    private final double halfWidth, height;
    public GroundCollisionTerrain(World world, Entity body) {
        this.world = world; this.body = body;
        halfWidth = Math.max(.3, body.getWidth() / 2.0) + .03;
        height = Math.max(1.8, body.getHeight());
    }
    private Box bodyAt(double x, double y, double z) {
        return new Box(x - halfWidth, y + .02, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
    }
    private boolean loaded(Box box) {
        for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++)
            for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++)
                if (!world.isChunkLoaded(new BlockPos(x * 16, 0, z * 16))) return false;
        return true;
    }
    public boolean clear(Box box) { return loaded(box) && world.isSpaceEmpty(body, box); }
    @Override public GroundRoutePlanner.Point step(GroundRoutePlanner.Point from, int dx, int dz) {
        double x = from.x() + dx + .5, z = from.z() + dz + .5;
        Box support = new Box(x - halfWidth, from.y() - 2.01, z - halfWidth, x + halfWidth, from.y() + 1.26, z + halfWidth);
        if (!loaded(support)) return null;
        List<Double> floors = new ArrayList<>();
        for (var shape : world.getBlockCollisions(body, support)) if (!shape.isEmpty()) {
            double y = shape.getMax(Direction.Axis.Y);
            if (y <= from.y() + 1.25 && y >= from.y() - 2 && !floors.contains(y)) floors.add(y);
        }
        floors.sort(Comparator.reverseOrder());
        for (double y : floors) {
            BlockPos feet = BlockPos.ofFloored(x, y + .02, z), below = BlockPos.ofFloored(x, y - .02, z);
            if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(below).isEmpty()) continue;
            var state = world.getBlockState(below);
            if (state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                    || state.isOf(Blocks.CACTUS) || world.getBlockState(feet).isOf(Blocks.SWEET_BERRY_BUSH)) continue;
            // Sweeping at the higher floor rejects walls, low ceilings and corner clipping.
            double sweepY = Math.max(y, from.y());
            Box sweep = bodyAt(from.x() + .5, sweepY, from.z() + .5).union(bodyAt(x, sweepY, z));
            if (clear(bodyAt(x, y, z)) && clear(sweep)) return new GroundRoutePlanner.Point(from.x() + dx, y, from.z() + dz);
        }
        return null;
    }
}
