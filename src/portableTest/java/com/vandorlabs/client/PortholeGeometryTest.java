package com.vandorlabs.client;

import java.util.List;

/** Geometry fingerprint captured from the original implementation before optimization. */
public final class PortholeGeometryTest {
    private PortholeGeometryTest() { }
    public static void main(String[] args) {
        verify(false);
        verify(true);
        for (int i = 1; i <= 1500; i++) {
            PortholeHex outline = PortholeGeometryCache.outline(i, 1, i % 4, i % 2);
            PortholeGeometryCache.slice(i, 1, i % 4, i % 2, 0, 0, outline);
        }
        require(PortholeGeometryCache.retainedCoordinates() <= 65536, "coordinate budget exceeded");
        require(PortholeGeometryCache.retainedSlices() <= 1024, "entry budget exceeded");
        verify(true); // Same results after eviction and recreation.
        PortholeGeometryCache.clear();
        require(PortholeGeometryCache.retainedSlices() == 0
                && PortholeGeometryCache.retainedCoordinates() == 0, "cache clear leaked geometry");
        verify(true);
    }
    private static void verify(boolean cached) {
        long hash = 1;
        int slices = 0;
        for (int shape = 0; shape < 4; shape++) {
            for (int border = 0; border < 2; border++) {
                for (int[] size : new int[][] {{1,1},{2,1},{1,3},{3,2},{8,8},{16,2}}) {
                    PortholeHex hex = cached
                            ? PortholeGeometryCache.outline(size[0], size[1], shape, border)
                            : new PortholeHex(size[0], size[1], shape, border);
                    for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) {
                        PortholeHex.Slice slice = cached
                                ? PortholeGeometryCache.slice(size[0], size[1], shape, border, x, y, hex)
                                : hex.slice(x,y);
                        hash = hash(hash, slice.polygon);
                        hash = hash(hash, slice.frameQuads);
                        hash = hash(hash, slice.hexEdges);
                        hash = hash(hash, slice.glassQuads);
                        double area = area(slice.frameQuads) + area(slice.glassQuads);
                        require(Math.abs(area - 256) < 1E-6, "frame/glass must cover exactly one cell");
                        for (int axis = 0; axis < 2; axis++) for (int edge : new int[]{0,16}) {
                            double[] opening = slice.edgeOpening(axis,edge);
                            hash = 31 * hash + (opening == null ? 0 : 1);
                            if (opening != null) for (double coordinate : opening)
                                hash = 31 * hash + Double.doubleToLongBits(coordinate);
                        }
                        slices++;
                    }
                }
            }
        }
        System.out.println("Porthole geometry slices=" + slices + " fingerprint=" + hash);
        // Filled with the measured baseline before production changes.
        require(hash == EXPECTED, "geometry changed: " + hash);
    }
    private static final long EXPECTED = -8752866524235774271L;
    private static long hash(long hash, List<double[]> values) {
        hash = 31 * hash + values.size();
        for (double[] value : values) for (double coordinate : value) {
            require(Double.isFinite(coordinate) && coordinate >= -1E-6 && coordinate <= 16+1E-6,
                    "coordinate outside cell: " + coordinate);
            hash = 31 * hash + Double.doubleToLongBits(coordinate);
        }
        return hash;
    }
    private static double area(List<double[]> quads) {
        double total = 0;
        for (double[] q : quads) {
            double area = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                area += q[2*i]*q[2*j+1]-q[2*j]*q[2*i+1];
            }
            total += Math.abs(area)/2;
        }
        return total;
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
