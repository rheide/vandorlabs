package com.vandorlabs.client;

import com.vandorlabs.render.HousingFacePlane;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Bounded face-plane discovery and deterministic16x16 artwork spans, independent of world/cache APIs. */
public final class JoinedHousingPolicy {
    public static final int LIMIT = 256, MAX_SPAN = 16;
    private JoinedHousingPolicy() { }
    public static final class Cell {
        public final int x, y, z;
        public Cell(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        Cell offset(int axis, int amount) { return new Cell(x + (axis == 0 ? amount : 0), y + (axis == 1 ? amount : 0), z + (axis == 2 ? amount : 0)); }
        @Override public boolean equals(Object other) { if (!(other instanceof Cell)) return false; Cell cell = (Cell) other; return x == cell.x && y == cell.y && z == cell.z; }
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
    }
    /** Host-sampled eligibility/exposure and local material identity on the requested face. */
    public static final class Surface {
        public final boolean eligible, exposed;
        public final int facing, layout, material;
        public Surface(boolean eligible, boolean exposed, int facing, int layout, int material) {
            this.eligible = eligible; this.exposed = exposed; this.facing = facing; this.layout = layout; this.material = material;
        }
    }
    public static boolean matches(Surface root, Surface candidate) {
        return root != null && candidate != null && candidate.eligible && candidate.exposed
                && matches(root.facing, root.layout, root.material, candidate.facing, candidate.layout, candidate.material);
    }
    public static boolean matches(int rootFacing, int rootLayout, int rootMaterial, int facing, int layout, int material) {
        return facing == rootFacing && layout == rootLayout && material == rootMaterial;
    }
    public static boolean joined(int layout) { return layout != 1; }
    public static final class Result<P extends HousingFacePlane> {
        public final Map<Cell, P> planes;
        public final boolean exceeded;
        Result(Map<Cell, P> planes, boolean exceeded) { this.planes = Collections.unmodifiableMap(planes); this.exceeded = exceeded; }
    }
    public static Result<HousingFacePlane> discover(Cell root, int face, Predicate<Cell> matches) {
        return discover(root, face, matches, HousingFacePlane::new);
    }
    /** Native adapters can create their own compatible plane subclass without an intermediate copy. */
    public static <P extends HousingFacePlane> Result<P> discover(Cell root, int face, Predicate<Cell> matches, BiFunction<int[], int[], P> plane) {
        if (face < 0 || face > 5) throw new IllegalArgumentException("face index");
        int u = face >= 4 ? 2 : 0, v = face <= 1 ? 2 : 1;
        Set<Cell> members = new HashSet<>(); ArrayDeque<Cell> queue = new ArrayDeque<>(); queue.add(root);
        // The257th accepted cell establishes overflow, exactly as the original loaded-world scan.
        while (!queue.isEmpty() && members.size() <= LIMIT) {
            Cell cell = queue.removeFirst();
            if (members.contains(cell) || !matches.test(cell)) continue;
            members.add(cell); queue.add(cell.offset(u, 1)); queue.add(cell.offset(u, -1)); queue.add(cell.offset(v, 1)); queue.add(cell.offset(v, -1));
        }
        Map<Cell, P> result = new LinkedHashMap<>();
        if (members.size() > LIMIT) {
            for (Cell cell : members) result.put(cell, plane.apply(new int[3], new int[]{1, 1, 1}));
            return new Result<>(result, true);
        }
        Map<Long, Cell> cells = new HashMap<>();
        for (Cell cell : members) cells.put(PortholeRectangles.cell(cell.axis(u), cell.axis(v)), cell);
        for (PortholeRectangles.Rect rect : PortholeRectangles.partition(cells.keySet())) {
            for (int row = 0; row < rect.height; row++) for (int column = 0; column < rect.width; column++) {
                Cell cell = cells.get(PortholeRectangles.cell(rect.x + column, rect.y + row));
                int[] min = new int[3], size = new int[]{1, 1, 1}; min[u] = -(column % MAX_SPAN); min[v] = -(row % MAX_SPAN);
                size[u] = Math.min(MAX_SPAN, rect.width - column / MAX_SPAN * MAX_SPAN);
                size[v] = Math.min(MAX_SPAN, rect.height - row / MAX_SPAN * MAX_SPAN);
                result.put(cell, plane.apply(min, size));
            }
        }
        return new Result<>(result, false);
    }
}
