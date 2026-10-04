package org.marj4n.smooth_fix.benchmark;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.registry.tag.FluidTags;
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
    public boolean water(double x,double y,double z) {
        BlockPos pos=BlockPos.ofFloored(x,y,z);
        return world.isChunkLoaded(pos) && world.getFluidState(pos).isIn(FluidTags.WATER);
    }
    /** Recheck the actual swept direction, including inertia and corners, without generating chunks. */
    public boolean safeMotion(double x,double y,double z,double dx,double dz,double length) {
        double norm=Math.hypot(dx,dz);if(norm<.001)return true;
        for(double d=.15;d<=length+.15;d+=.15){
            double px=x+dx/norm*d,pz=z+dz/norm*d;
            // Probe the actual footprint, not the center of its block. Slab/edge offsets matter.
            var foot=standingAt(px,y,pz,px,pz);
            if(foot==null || foot.y()<y-1.25)return false;
        }
        return true;
    }
    @Override public GroundRoutePlanner.Point step(GroundRoutePlanner.Point from, int dx, int dz) {
        double x = from.x() + dx + .5, z = from.z() + dz + .5;
        return standingAt(x,from.y(),z,from.x()+.5,from.z()+.5);
    }
    private GroundRoutePlanner.Point standingAt(double x,double fromY,double z,double startX,double startZ) {
        var from = new GroundRoutePlanner.Point(MathHelper.floor(startX),fromY,MathHelper.floor(startZ));
        Box support = new Box(x - halfWidth, from.y() - 2.01, z - halfWidth, x + halfWidth, from.y() + 1.26, z + halfWidth);
        if (!loaded(support)) return null;
        // Water paths stay near the surface. Jump input supplies native buoyancy; lava is never allowed.
        if(water(x,from.y()+.1,z) || water(x,from.y()-.3,z)) {
            for(double wy=from.y()+1;wy>=from.y()-1;wy-=.5)
                if(water(x,wy+.1,z) && !water(x,wy+1.1,z) && clear(bodyAt(x,wy,z)))
                    return new GroundRoutePlanner.Point(MathHelper.floor(x),Math.floor(wy*2)/2,MathHelper.floor(z));
        }
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
            Box sweep = bodyAt(startX, sweepY, startZ).union(bodyAt(x, sweepY, z));
            if (clear(bodyAt(x, y, z)) && clear(sweep)) return new GroundRoutePlanner.Point(MathHelper.floor(x), y, MathHelper.floor(z));
        }
        return null;
    }
}
