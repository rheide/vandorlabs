package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Actual render rectangles for one compatible plane inside a 16-block section.
 * Callers partition by plane, material, lighting and texture representation first.
 * Textured cells may be retained as unit rectangles: atlas sprites cannot repeat
 * across a larger rectangle merely by extending their atlas coordinates.
 */
public final class JoinedPaneMesher {
    public static final int SECTION_SIZE = 16;

    public static final class Cell {
        public final int u, v;
        public final boolean joined;

        public Cell(int u, int v, boolean joined) {
            this.u = u;
            this.v = v;
            this.joined = joined;
        }
    }

    public static final class Rect {
        public final int u, v, width, height;

        public Rect(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public int cells() { return width * height; }
    }

    public static List<Rect> merge(List<Cell> cells) { return merge(cells, true); }

    /**
     * Partition every supplied cell exactly once, without changing the input.
     * A non-joined cell is always a unit rectangle. The merge flag additionally
     * preserves per-block UV repetition for authored or unrecognised textures.
     * Work and temporary storage are bounded by one 16 by 16 plane.
     */
    public static List<Rect> merge(List<Cell> cells, boolean mergeEnabled) {
        if (cells == null) throw new IllegalArgumentException("Missing pane cells");
        if (cells.size() > SECTION_SIZE * SECTION_SIZE)
            throw new IllegalArgumentException("Pane plane exceeds one section");
        if (cells.isEmpty()) return Collections.emptyList();

        // 0 = absent/consumed, 1 = separate cell, 2 = mergeable cell.
        byte[] remaining = new byte[SECTION_SIZE * SECTION_SIZE];
        for (Cell cell : cells) {
            if (cell == null || cell.u < 0 || cell.u >= SECTION_SIZE
                    || cell.v < 0 || cell.v >= SECTION_SIZE)
                throw new IllegalArgumentException("Pane cell is outside its section");
            int index = cell.v * SECTION_SIZE + cell.u;
            if (remaining[index] != 0)
                throw new IllegalArgumentException("Duplicate pane cell");
            remaining[index] = (byte) (mergeEnabled && cell.joined ? 2 : 1);
        }

        List<Rect> result = new ArrayList<>();
        for (int v = 0; v < SECTION_SIZE; v++) {
            for (int u = 0; u < SECTION_SIZE; u++) {
                int index = v * SECTION_SIZE + u;
                if (remaining[index] == 0) continue;
                int width = 1, height = 1;
                if (remaining[index] == 2) {
                    int candidateWidth = SECTION_SIZE - u;
                    // Choose the largest rectangle anchored at this first cell.
                    // Narrower higher rectangles can beat the widest first row.
                    for (int row = v; row < SECTION_SIZE; row++) {
                        int run = 0;
                        while (run < candidateWidth
                                && remaining[row * SECTION_SIZE + u + run] == 2) run++;
                        candidateWidth = run;
                        if (candidateWidth == 0) break;
                        int candidateHeight = row - v + 1;
                        int area = candidateWidth * candidateHeight;
                        if (area > width * height
                                || area == width * height && candidateWidth > width) {
                            width = candidateWidth;
                            height = candidateHeight;
                        }
                    }
                }
                result.add(new Rect(u, v, width, height));
                for (int row = v; row < v + height; row++)
                    for (int col = u; col < u + width; col++)
                        remaining[row * SECTION_SIZE + col] = 0;
            }
        }
        return Collections.unmodifiableList(result);
    }

    private JoinedPaneMesher() {}
}
