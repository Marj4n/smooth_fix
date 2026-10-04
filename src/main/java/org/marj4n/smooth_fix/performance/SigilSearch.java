package org.marj4n.smooth_fix.performance;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Enumerates only registered sigils, in precisely the order of vanilla findClosest. No stale cache. */
public final class SigilSearch {
    private SigilSearch() { }

    public static BlockPos find(BlockPos origin, int radius, Predicate<BlockPos> predicate, List<Long> positions) {
        if (positions.isEmpty()) return null;
        // Keep temporary work bounded even on worlds with many registered sigils.
        if (radius < 0 || radius > 64 || positions.size() > 256)
            return BlockPos.findClosest(origin, radius, radius, predicate).orElse(null);
        ArrayList<BlockPos> candidates = null;
        for (Long packed : positions) {
            if (packed == null) continue;
            int x = BlockPos.unpackLongX(packed), y = BlockPos.unpackLongY(packed), z = BlockPos.unpackLongZ(packed);
            if (Math.abs((long)x-origin.getX()) > radius || Math.abs((long)y-origin.getY()) > radius
                    || Math.abs((long)z-origin.getZ()) > radius) continue;
            if (candidates == null) candidates = new ArrayList<>();
            candidates.add(new BlockPos(x,y,z));
        }
        if (candidates == null) return null;
        candidates.sort((a,b) -> compare(origin,a,b));
        BlockPos previous = null;
        for (BlockPos candidate : candidates) {
            if (candidate.equals(previous)) continue;
            previous = candidate;
            if (predicate.test(candidate)) return candidate;
        }
        return null;
    }

    static int compare(BlockPos origin, BlockPos a, BlockPos b) {
        int ax=a.getX()-origin.getX(), ay=a.getY()-origin.getY(), az=a.getZ()-origin.getZ();
        int bx=b.getX()-origin.getX(), by=b.getY()-origin.getY(), bz=b.getZ()-origin.getZ();
        int c=Integer.compare(Math.abs(ax)+Math.abs(ay)+Math.abs(az),Math.abs(bx)+Math.abs(by)+Math.abs(bz));
        if(c==0)c=Integer.compare(ax,bx);
        if(c==0)c=Integer.compare(ay,by);
        if(c==0)c=Integer.compare(bz,az); // iterateOutwards visits positive Z before its negative mirror.
        return c;
    }
}
