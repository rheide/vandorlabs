package com.vandorlabs.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World-independent geometry only: no tiles, textures, lighting or GL resources. */
final class PortholeGeometryCache {
    private static final int MAX_SHAPES = 32;
    private static final int MAX_SLICES = 1024;
    // Bound retained coordinate payload as well as entry count (512 KiB of doubles).
    private static final int MAX_COORDINATES = 65536;
    private static final Map<ShapeKey, PortholeHex> SHAPES = new LinkedHashMap<>(32, .75F, true);
    private static final Map<SliceKey, PortholeHex.Slice> SLICES = new LinkedHashMap<>(128, .75F, true);
    private static int coordinates;

    private PortholeGeometryCache() { }

    static PortholeHex outline(int columns, int rows, int shape, int border) {
        ShapeKey key = new ShapeKey(columns, rows, shape, border);
        PortholeHex hex = SHAPES.get(key);
        if (hex != null) return hex;
        hex = new PortholeHex(columns, rows, shape, border);
        // Very long pixel ellipses are legal but should not occupy the shared cache.
        if (columns <= 64 && rows <= 64) {
            SHAPES.put(key, hex);
            if (SHAPES.size() > MAX_SHAPES) SHAPES.remove(SHAPES.keySet().iterator().next());
        }
        return hex;
    }

    static PortholeHex.Slice slice(int columns, int rows, int shape, int border,
            int column, int row, PortholeHex outline) {
        SliceKey key = new SliceKey(columns, rows, shape, border, column, row);
        PortholeHex.Slice result = SLICES.get(key);
        if (result != null) return result;
        result = outline.slice(column, row);
        int weight = weight(result);
        if (weight <= MAX_COORDINATES) {
            SLICES.put(key, result);
            coordinates += weight;
            while (SLICES.size() > MAX_SLICES || coordinates > MAX_COORDINATES) {
                SliceKey oldest = SLICES.keySet().iterator().next();
                coordinates -= weight(SLICES.remove(oldest));
            }
        }
        return result;
    }

    static void clear() {
        SHAPES.clear();
        SLICES.clear();
        coordinates = 0;
    }

    static int retainedCoordinates() { return coordinates; }
    static int retainedSlices() { return SLICES.size(); }

    private static int weight(PortholeHex.Slice slice) {
        return weight(slice.polygon) + weight(slice.frameQuads)
                + weight(slice.hexEdges) + weight(slice.glassQuads);
    }
    private static int weight(List<double[]> arrays) {
        int result = 0;
        for (double[] array : arrays) result += array.length;
        return result;
    }

    private static final class ShapeKey {
        final int columns, rows, shape, border;
        ShapeKey(int columns, int rows, int shape, int border) {
            this.columns=columns; this.rows=rows; this.shape=shape; this.border=border;
        }
        @Override public int hashCode() { return ((columns*31+rows)*31+shape)*31+border; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof ShapeKey)) return false;
            ShapeKey key=(ShapeKey)other;
            return columns==key.columns && rows==key.rows && shape==key.shape && border==key.border;
        }
    }
    private static final class SliceKey {
        final int columns, rows, shape, border, column, row;
        SliceKey(int columns, int rows, int shape, int border, int column, int row) {
            this.columns=columns; this.rows=rows; this.shape=shape; this.border=border;
            this.column=column; this.row=row;
        }
        @Override public int hashCode() {
            return ((((columns*31+rows)*31+shape)*31+border)*31+column)*31+row;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof SliceKey)) return false;
            SliceKey key=(SliceKey)other;
            return columns==key.columns && rows==key.rows && shape==key.shape && border==key.border
                    && column==key.column && row==key.row;
        }
    }
}
