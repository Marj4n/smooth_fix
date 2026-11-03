package org.marj4n.smooth_fix.performance;

/** Fresh invocation-owned arrays only. No pooling, shared scratch state or noise arithmetic changes. */
public final class NoiseArrayAllocation {
    private NoiseArrayAllocation() {}

    public static double[][] column(int height, int generators) {
        check(height, generators);
        return new double[height][];
    }

    public static double[][][][] cube(int width, int depth, int height, int generators) {
        check(width, depth);
        check(height, generators);
        double[][][][] out = new double[width][depth][][];
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++)
                if (!((x == 0 || x == width - 1) && (z == 0 || z == depth - 1)))
                    out[x][z] = new double[height][generators];
        return out;
    }

    private static void check(int a, int b) {
        if (a < 0 || b < 0) throw new NegativeArraySizeException();
    }
}
