package com.vandorlabs.gear.track.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java 8 validation of one already discovered, complete track component.
 * No method accesses a world or loads chunks. All masks are local to facing:
 * front=1, right=2, back=4, left=8; north-facing front is negative world Z.
 */
public final class TrackTopology {
    public static final int MAX_TRACKS = 64;
    public static final int MAX_HORIZONTAL_SPAN = 16;
    public static final int FRONT = 1;
    public static final int RIGHT = 2;
    public static final int BACK = 4;
    public static final int LEFT = 8;

    public enum Style { COMPACT, ARMOURED, BOGIE }

    public enum Facing {
        NORTH(0, -1, 1, 0),
        EAST(1, 0, 0, 1),
        SOUTH(0, 1, -1, 0),
        WEST(-1, 0, 0, -1);

        public final int frontX;
        public final int frontZ;
        public final int rightX;
        public final int rightZ;

        Facing(int frontX, int frontZ, int rightX, int rightZ) {
            this.frontX = frontX;
            this.frontZ = frontZ;
            this.rightX = rightX;
            this.rightZ = rightZ;
        }

        /** Local X points right and local Z points back. */
        public int localX(int worldDeltaX, int worldDeltaZ) {
            return Math.addExact(Math.multiplyExact(worldDeltaX, rightX),
                    Math.multiplyExact(worldDeltaZ, rightZ));
        }

        public int localZ(int worldDeltaX, int worldDeltaZ) {
            return Math.addExact(Math.multiplyExact(worldDeltaX, -frontX),
                    Math.multiplyExact(worldDeltaZ, -frontZ));
        }
    }

    /** Immutable world-cell coordinate, including Y so separate layers never join. */
    public static final class Cell {
        public final int x;
        public final int y;
        public final int z;

        public Cell(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell)) return false;
            Cell cell = (Cell) other;
            return x == cell.x && y == cell.y && z == cell.z;
        }

        @Override public int hashCode() {
            int hash = 31 + x;
            hash = 31 * hash + y;
            return 31 * hash + z;
        }

        @Override public String toString() { return "(" + x + "," + y + "," + z + ")"; }
    }

    /** One input block. Validation handles missing fields as an invalid result. */
    public static final class Node {
        public final Cell cell;
        public final Style style;
        public final Facing facing;

        public Node(Cell cell, Style style, Facing facing) {
            this.cell = cell; this.style = style; this.facing = facing;
        }

        public Node(int x, int y, int z, Style style, Facing facing) {
            this(new Cell(x, y, z), style, facing);
        }
    }

    public static final class Part {
        private final Cell cell;
        private final int beltMask;
        private final int chassisMask;
        private final int localX;
        private final int localZ;
        private final int runMinZ;
        private final int runMaxZ;

        private Part(Cell cell, int beltMask, int chassisMask, int localX, int localZ,
                     int runMinZ, int runMaxZ) {
            this.cell = cell; this.beltMask = beltMask; this.chassisMask = chassisMask;
            this.localX = localX; this.localZ = localZ;
            this.runMinZ = runMinZ; this.runMaxZ = runMaxZ;
        }

        public Cell cell() { return cell; }
        public int beltMask() { return beltMask; }
        public int chassisMask() { return chassisMask; }
        public int localX() { return localX; }
        public int localZ() { return localZ; }
        /** Inclusive endpoints of this straight belt run in assembly-local coordinates. */
        public int runMinZ() { return runMinZ; }
        public int runMaxZ() { return runMaxZ; }
        public int runLength() { return runMaxZ - runMinZ + 1; }
    }

    public static final class Assembly {
        private final Cell leader;
        private final Style style;
        private final Facing facing;
        private final List<Part> parts;
        private final Map<Cell, Part> byCell;

        private Assembly(Cell leader, Style style, Facing facing, List<Part> parts) {
            this.leader = leader; this.style = style; this.facing = facing;
            this.parts = Collections.unmodifiableList(new ArrayList<Part>(parts));
            Map<Cell, Part> index = new LinkedHashMap<Cell, Part>();
            for (Part part : parts) index.put(part.cell(), part);
            this.byCell = Collections.unmodifiableMap(index);
        }

        public Cell leader() { return leader; }
        public Style style() { return style; }
        public Facing facing() { return facing; }
        public List<Part> parts() { return parts; }
        public Part partAt(Cell cell) { return byCell.get(cell); }
        public int size() { return parts.size(); }
    }

    public static final class Result {
        private final Assembly assembly;
        private final String error;

        private Result(Assembly assembly, String error) {
            this.assembly = assembly; this.error = error;
        }

        public boolean isValid() { return assembly != null; }
        /** Null for an invalid result; call isValid() first. */
        public Assembly assembly() { return assembly; }
        /** Empty for a valid result. */
        public String error() { return error; }
    }

    private static final Comparator<Cell> WORLD_ORDER = new Comparator<Cell>() {
        @Override public int compare(Cell a, Cell b) {
            int order = Integer.compare(a.z, b.z);
            if (order == 0) order = Integer.compare(a.x, b.x);
            if (order == 0) order = Integer.compare(a.y, b.y);
            return order;
        }
    };

    private TrackTopology() { }

    /**
     * Validate a complete component, rejecting conflicts or excess size as a whole.
     * Opposite-facing tracks intentionally remain distinct just like perpendicular
     * tracks: connected members have exactly the same style and facing.
     */
    public static Result analyze(Collection<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) return invalid("No track blocks were supplied.");
        if (nodes.size() > MAX_TRACKS) return invalid("A connected track may contain at most 64 blocks.");
        Map<Cell, Node> members = new HashMap<Cell, Node>();
        Style style = null;
        Facing facing = null;
        int layer = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Node node : nodes) {
            if (node == null || node.cell == null || node.style == null || node.facing == null) {
                return invalid("Every track requires a position, style and facing.");
            }
            if (members.isEmpty()) {
                style = node.style; facing = node.facing; layer = node.cell.y;
            } else if (node.style != style || node.facing != facing || node.cell.y != layer) {
                return invalid("Connected tracks must share style, facing and height.");
            }
            if (members.put(node.cell, node) != null) return invalid("A track cell was supplied twice.");
            if (members.size() > MAX_TRACKS) return invalid("A connected track may contain at most 64 blocks.");
            minX = Math.min(minX, node.cell.x); maxX = Math.max(maxX, node.cell.x);
            minZ = Math.min(minZ, node.cell.z); maxZ = Math.max(maxZ, node.cell.z);
            if ((long) maxX - minX + 1 > MAX_HORIZONTAL_SPAN
                    || (long) maxZ - minZ + 1 > MAX_HORIZONTAL_SPAN) {
                return invalid("The track footprint may span at most 16 blocks in either horizontal direction.");
            }
        }

        List<Cell> ordered = new ArrayList<Cell>(members.keySet());
        Collections.sort(ordered, WORLD_ORDER);
        Cell leader = ordered.get(0);
        Set<Cell> visited = new HashSet<Cell>();
        ArrayDeque<Cell> queue = new ArrayDeque<Cell>();
        visited.add(leader); queue.add(leader);
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            for (int side = 0; side < 4; side++) {
                Cell neighbor = neighbor(current, facing, side);
                if (neighbor != null && members.containsKey(neighbor) && visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        if (visited.size() != members.size()) return invalid("Tracks must form one face-connected horizontal component.");

        Map<Cell, int[]> runs = new HashMap<Cell, int[]>();
        for (Cell cell : ordered) {
            Cell first = cell, last = cell;
            Cell candidate;
            while ((candidate = neighbor(first, facing, 0)) != null && members.containsKey(candidate)) first = candidate;
            while ((candidate = neighbor(last, facing, 2)) != null && members.containsKey(candidate)) last = candidate;
            int runMin = localZ(first, leader, facing);
            int runMax = localZ(last, leader, facing);
            runs.put(cell, new int[] { runMin, runMax });
        }

        List<Part> parts = new ArrayList<Part>();
        for (Cell cell : ordered) {
            int chassisMask = 0, beltMask = 0;
            int[] run = runs.get(cell);
            for (int side = 0; side < 4; side++) {
                Cell neighbor = neighbor(cell, facing, side);
                if (neighbor == null || !members.containsKey(neighbor)) continue;
                int bit = 1 << side;
                chassisMask |= bit;
                int[] neighborRun = runs.get(neighbor);
                if ((side & 1) == 0 || (run[0] == neighborRun[0] && run[1] == neighborRun[1])) beltMask |= bit;
            }
            int dx = (int) ((long) cell.x - leader.x);
            int dz = (int) ((long) cell.z - leader.z);
            parts.add(new Part(cell, beltMask, chassisMask, facing.localX(dx, dz), facing.localZ(dx, dz), run[0], run[1]));
        }
        return new Result(new Assembly(leader, style, facing, parts), "");
    }

    private static int localZ(Cell cell, Cell leader, Facing facing) {
        // Inclusive span validation guarantees these long differences fit int.
        int dx = (int) ((long) cell.x - leader.x);
        int dz = (int) ((long) cell.z - leader.z);
        return facing.localZ(dx, dz);
    }

    /** Null rather than wrapped coordinates at the integer world boundary. */
    private static Cell neighbor(Cell cell, Facing facing, int side) {
        int dx = side == 0 ? facing.frontX : side == 1 ? facing.rightX
                : side == 2 ? -facing.frontX : -facing.rightX;
        int dz = side == 0 ? facing.frontZ : side == 1 ? facing.rightZ
                : side == 2 ? -facing.frontZ : -facing.rightZ;
        long x = (long) cell.x + dx, z = (long) cell.z + dz;
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new Cell((int) x, cell.y, (int) z);
    }

    private static Result invalid(String reason) { return new Result(null, reason); }
}
