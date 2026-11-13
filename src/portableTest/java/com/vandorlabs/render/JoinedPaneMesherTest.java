package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Independent raster coverage checks for the rectangles actually submitted. */
public final class JoinedPaneMesherTest {
    private static long assertions;
    private static void need(boolean value, String why) {
        assertions++;
        if (!value) throw new AssertionError(why);
    }

    private static List<JoinedPaneMesher.Rect> check(List<JoinedPaneMesher.Cell> cells, boolean merge) {
        int[] expected = new int[256], actual = new int[256];
        for (JoinedPaneMesher.Cell cell : cells) expected[cell.v * 16 + cell.u] = cell.joined ? 2 : 1;
        List<JoinedPaneMesher.Rect> rectangles = JoinedPaneMesher.merge(cells, merge);
        for (JoinedPaneMesher.Rect rect : rectangles) {
            need(rect.width > 0 && rect.height > 0, "nonempty rectangle");
            need(rect.u >= 0 && rect.v >= 0 && rect.u + rect.width <= 16
                    && rect.v + rect.height <= 16, "rectangle remains inside section");
            for (int v = rect.v; v < rect.v + rect.height; v++) {
                for (int u = rect.u; u < rect.u + rect.width; u++) {
                    int index = v * 16 + u;
                    actual[index]++;
                    need(expected[index] != 0, "hole remains empty");
                    need(rect.cells() == 1 || merge && expected[index] == 2,
                            "separate cells and authored UVs remain separate");
                }
            }
        }
        for (int i = 0; i < 256; i++) need(actual[i] == (expected[i] == 0 ? 0 : 1), "exact single coverage");
        return rectangles;
    }

    private static void same(List<JoinedPaneMesher.Rect> a, List<JoinedPaneMesher.Rect> b) {
        need(a.size() == b.size(), "deterministic rectangle count");
        for (int i = 0; i < a.size(); i++) {
            JoinedPaneMesher.Rect x = a.get(i), y = b.get(i);
            need(x.u == y.u && x.v == y.v && x.width == y.width && x.height == y.height,
                    "input order does not change geometry");
        }
    }

    private static void rejects(List<JoinedPaneMesher.Cell> cells) {
        boolean rejected = false;
        try { JoinedPaneMesher.merge(cells); } catch (IllegalArgumentException expected) { rejected = true; }
        need(rejected, "invalid occupancy is rejected");
    }

    public static void main(String[] args) {
        // Every possible shape in a 4x4 region, translated away from zero.
        for (int mask = 0; mask < 65536; mask++) {
            List<JoinedPaneMesher.Cell> cells = new ArrayList<>();
            for (int bit = 0; bit < 16; bit++)
                if ((mask & (1 << bit)) != 0)
                    cells.add(new JoinedPaneMesher.Cell(5 + bit % 4, 7 + bit / 4, true));
            check(cells, true);
        }

        Random random = new Random(0x676c617373L);
        for (int trial = 0; trial < 512; trial++) {
            List<JoinedPaneMesher.Cell> cells = new ArrayList<>();
            for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
                if (random.nextInt(5) != 0)
                    cells.add(new JoinedPaneMesher.Cell(u, v, random.nextInt(4) != 0));
            List<JoinedPaneMesher.Rect> before = check(cells, true);
            Collections.shuffle(cells, random);
            same(before, check(cells, true));
            check(cells, false);
        }

        List<JoinedPaneMesher.Cell> full = new ArrayList<>();
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
            full.add(new JoinedPaneMesher.Cell(u, v, true));
        List<JoinedPaneMesher.Rect> merged = check(full, true);
        need(merged.size() == 1 && merged.get(0).cells() == 256, "full joined plane becomes one rectangle");
        need(check(full, false).size() == 256, "authored mode retains per-block UV rectangles");

        List<JoinedPaneMesher.Cell> disconnected = new ArrayList<>();
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
            disconnected.add(new JoinedPaneMesher.Cell(u, v, false));
        need(check(disconnected, true).size() == 256, "join-disabled plane keeps independent cells");

        // A window surround with a large empty aperture must never close it.
        List<JoinedPaneMesher.Cell> ring = new ArrayList<>();
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
            if (u < 2 || u >= 14 || v < 2 || v >= 14) ring.add(new JoinedPaneMesher.Cell(u, v, true));
        need(check(ring, true).size() == 4, "rectangular ring uses four rectangles with an empty centre");

        rejects(null);
        rejects(Collections.singletonList(new JoinedPaneMesher.Cell(-1, 0, true)));
        rejects(Collections.singletonList(new JoinedPaneMesher.Cell(0, 16, true)));
        rejects(Collections.singletonList(null));
        List<JoinedPaneMesher.Cell> duplicate = new ArrayList<>();
        duplicate.add(new JoinedPaneMesher.Cell(1, 1, true));
        duplicate.add(new JoinedPaneMesher.Cell(1, 1, false));
        rejects(duplicate);
        List<JoinedPaneMesher.Cell> tooMany = new ArrayList<>(full);
        tooMany.add(new JoinedPaneMesher.Cell(0, 0, true));
        rejects(tooMany);

        System.out.println("JoinedPaneMesher PASS assertions=" + assertions
                + " exhaustive_shapes=65536 random_sections=512 joined_cells=256 merged_rectangles=1");
    }
}
