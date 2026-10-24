package com.vandorlabs.client;

import com.vandorlabs.render.HousingFacePlane;
import java.util.HashSet;
import java.util.Set;

public final class JoinedHousingPolicyTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static JoinedHousingPolicy.Cell offset(JoinedHousingPolicy.Cell origin, int u, int v, int x, int y) { return origin.offset(u, x).offset(v, y); }
    private static Set<JoinedHousingPolicy.Cell> rectangle(JoinedHousingPolicy.Cell root, int u, int v, int width, int height) {
        Set<JoinedHousingPolicy.Cell> cells = new HashSet<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) cells.add(offset(root, u, v, x, y));
        return cells;
    }
    private static final class AdapterPlane extends HousingFacePlane { AdapterPlane(int[] min, int[] size) { super(min, size); } }
    public static void main(String[] args) {
        JoinedHousingPolicy.Surface root = new JoinedHousingPolicy.Surface(true, true, 2, 0, 7);
        require(JoinedHousingPolicy.matches(root, new JoinedHousingPolicy.Surface(true, true, 2, 0, 7)), "matching authored face");
        require(!JoinedHousingPolicy.matches(root, new JoinedHousingPolicy.Surface(false, true, 2, 0, 7)), "nonprogrammable candidate excluded");
        require(!JoinedHousingPolicy.matches(root, new JoinedHousingPolicy.Surface(true, false, 2, 0, 7)), "occluded face excluded");
        require(!JoinedHousingPolicy.matches(root, new JoinedHousingPolicy.Surface(true, true, 3, 0, 7)), "facing boundary");
        require(!JoinedHousingPolicy.matches(root, new JoinedHousingPolicy.Surface(true, true, 2, 2, 7)), "layout boundary");
        require(!JoinedHousingPolicy.matches(root, new JoinedHousingPolicy.Surface(true, true, 2, 0, 8)), "per-face material boundary");
        require(!JoinedHousingPolicy.matches(root, null) && !JoinedHousingPolicy.matches(null, root), "missing loaded snapshot");
        require(JoinedHousingPolicy.joined(0) && !JoinedHousingPolicy.joined(1) && JoinedHousingPolicy.joined(2), "tile-versus-span layout policy");
        int[] min = {0, 0, 0}, size = {2, 3, 4}; HousingFacePlane immutable = new HousingFacePlane(min, size);
        min[0] = 99; size[0] = 99; require(immutable.min(0) == 0 && immutable.size(0) == 2, "immutable plane arrays");
        int visited = 0;
        for (int face = 0; face < 6; face++) {
            int u = face >= 4 ? 2 : 0, v = face <= 1 ? 2 : 1;
            JoinedHousingPolicy.Cell origin = new JoinedHousingPolicy.Cell(-17, -4, -33);
            Set<JoinedHousingPolicy.Cell> cells = rectangle(origin, u, v, 2, 2);
            cells.add(offset(origin, u, v, 2, 2));
            {
                JoinedHousingPolicy.Result<AdapterPlane> result = JoinedHousingPolicy.discover(origin, face, cells::contains, AdapterPlane::new);
                require(!result.exceeded && result.planes.size() == 4, "diagonal-only contacts cannot join");
                for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                    AdapterPlane plane = result.planes.get(offset(origin, u, v, x, y));
                    for (int axis = 0; axis < 3; axis++) {
                        require(plane.min(axis) == (axis == u ? -x : axis == v ? -y : 0), "negative-position relative origin");
                        require(plane.size(axis) == (axis == u || axis == v ? 2 : 1), "all six projected face extents");
                    }
                    visited++;
                }
                try { result.planes.clear(); throw new AssertionError("mutable published planes"); } catch (UnsupportedOperationException expected) { }
            }
            cells = rectangle(origin, u, v, 17, 1);
            JoinedHousingPolicy.Result<HousingFacePlane> spanning = JoinedHousingPolicy.discover(origin, face, cells::contains);
            require(spanning.planes.size() == 17, "seventeen-cell strip coverage");
            for (int x = 0; x < 17; x++) {
                HousingFacePlane plane = spanning.planes.get(offset(origin, u, v, x, 0));
                require(plane.min(u) == -(x % 16) && plane.size(u) == (x < 16 ? 16 : 1), "sixteen-cell authored artwork cap");
            }
            cells = rectangle(origin, u, v, 1, 17);
            JoinedHousingPolicy.Result<HousingFacePlane> vertical = JoinedHousingPolicy.discover(origin, face, cells::contains);
            for (int y = 0; y < 17; y++) {
                HousingFacePlane plane = vertical.planes.get(offset(origin, u, v, 0, y));
                require(plane.min(v) == -(y % 16) && plane.size(v) == (y < 16 ? 16 : 1), "vertical authored artwork cap");
            }
            cells = rectangle(origin, u, v, 2, 2); cells.remove(offset(origin, u, v, 1, 1));
            JoinedHousingPolicy.Result<HousingFacePlane> hole = JoinedHousingPolicy.discover(origin, face, cells::contains);
            require(hole.planes.size() == 3 && hole.planes.get(origin).size(u) == 2 && hole.planes.get(origin).size(v) == 1
                    && hole.planes.get(offset(origin, u, v, 0, 1)).size(u) == 1, "largest-first rectangle partition never spans hole");
        }
        JoinedHousingPolicy.Cell origin = new JoinedHousingPolicy.Cell(0, 0, 0);
        Set<JoinedHousingPolicy.Cell> cells = rectangle(origin, 0, 1, 256, 1);
        JoinedHousingPolicy.Result<HousingFacePlane> exact = JoinedHousingPolicy.discover(origin, 2, cells::contains);
        require(!exact.exceeded && exact.planes.size() == 256, "exact loaded component budget");
        cells.add(new JoinedHousingPolicy.Cell(256, 0, 0));
        final Set<JoinedHousingPolicy.Cell> oversized = cells; final int[] probes = {0};
        JoinedHousingPolicy.Result<HousingFacePlane> exceeded = JoinedHousingPolicy.discover(origin, 2, cell -> { probes[0]++; return oversized.contains(cell); });
        require(exceeded.exceeded && exceeded.planes.size() == 257 && probes[0] <= 1029, "bounded overflow proof");
        for (HousingFacePlane plane : exceeded.planes.values()) for (int axis = 0; axis < 3; axis++) require(plane.min(axis) == 0 && plane.size(axis) == 1, "overflow retains unit-plane fallback");
        require(JoinedHousingPolicy.discover(origin, 2, cell -> false).planes.isEmpty(), "unloaded root never scans neighbours");
        System.out.println("Joined housing policy PASS: " + visited + " six-face negative-origin assignments, material boundaries, rectangle holes,16cell spans and256cell budget");
    }
}
