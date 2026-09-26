package com.vandorlabs.client;

import java.util.HashSet;
import java.util.Set;

/** Exhaustive baseline policy: largest area, then width, bottom row and left edge. */
public final class PortholePartitionTest {
    private PortholePartitionTest() { }
    public static void main(String[] args) {
        for (int mask = 0; mask < 512; mask++) {
            Set<Long> cells = new HashSet<>();
            for (int i = 0; i < 9; i++) if ((mask & (1 << i)) != 0)
                cells.add(PortholeRectangles.cell(i % 3 - 1, i / 3 - 1));
            Set<Long> remaining = new HashSet<>(cells);
            for (PortholeRectangles.Rect actual : PortholeRectangles.partition(cells)) {
                int[] best = null;
                for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++)
                    for (int h = 1; y+h <= 2; h++) for (int w = 1; x+w <= 2; w++) {
                        boolean full = true;
                        for (int yy=y; yy<y+h; yy++) for (int xx=x; xx<x+w; xx++)
                            full &= remaining.contains(PortholeRectangles.cell(xx, yy));
                        if (full && (best == null || w*h > best[2]*best[3]
                                || w*h == best[2]*best[3] && (w > best[2]
                                || w == best[2] && (y < best[1] || y == best[1] && x < best[0]))))
                            best = new int[] {x,y,w,h};
                    }
                require(best != null && actual.x==best[0] && actual.y==best[1]
                        && actual.width==best[2] && actual.height==best[3], "partition mismatch: " + mask);
                for (int y=actual.y; y<actual.y+actual.height; y++)
                    for (int x=actual.x; x<actual.x+actual.width; x++)
                        require(remaining.remove(PortholeRectangles.cell(x,y)), "overlap");
            }
            require(remaining.isEmpty(), "uncovered cells");
            require(cells.size()==Integer.bitCount(mask), "mutated caller input");
        }
        System.out.println("Porthole partition PASS: all 512 masks including holes, disjoint cells and ties");
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
