package com.vandorlabs.gear.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates an entire landing-gear assembly without Minecraft dependencies.
 *
 * <p>The origin is the block containing the TOP extender. Feet use integer
 * block offsets from that origin. A column of height {@code h} places every
 * foot at {@code y == -h}. The foot directly beneath the fixed mount must be
 * present. The mount never moves to the geometric center of an even-width or
 * asymmetric foot group.</p>
 *
 * <p>The caller must supply the COMPLETE connected group, or report discovery
 * failure. A flood fill must reject a 65th member, not truncate to 64 before
 * calling this class. Chunk loading, hull support, foreign controllers and
 * scanning the actual vertical extender column are world-level concerns.</p>
 */
public final class GearTopology {
    public static final int MAX_COLUMN_HEIGHT = 8;
    public static final int MAX_FEET = 64;
    public static final int MAX_HORIZONTAL_SPAN = 16;

    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;

    public enum FootKind { PLATE, SKI, WHEEL }

    private GearTopology() { }

    /** Immutable block offset, relative to the top extender. */
    public static final class Cell {
        public final int x;
        public final int y;
        public final int z;

        public Cell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Cell add(int dx, int dy, int dz) {
            return new Cell(Math.addExact(x, dx), Math.addExact(y, dy),
                    Math.addExact(z, dz));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell)) return false;
            Cell cell = (Cell) other;
            return x == cell.x && y == cell.y && z == cell.z;
        }

        @Override
        public int hashCode() {
            int hash = x;
            hash = 31 * hash + y;
            return 31 * hash + z;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }

    /** Useful when discovery has not yet established one shared foot kind. */
    public static final class Foot {
        public final Cell offset;
        public final FootKind kind;

        public Foot(Cell offset, FootKind kind) {
            this.offset = offset;
            this.kind = kind;
        }
    }

    public static final class Result {
        private final Assembly assembly;
        private final String error;

        private Result(Assembly assembly, String error) {
            this.assembly = assembly;
            this.error = error;
        }

        public boolean isValid() { return assembly != null; }

        /** Empty string for a valid result; otherwise a user-readable reason. */
        public String error() { return error; }

        /** Null for an invalid result. An invalid assembly is never partial. */
        public Assembly assembly() { return assembly; }
    }

    /** Validated, immutable assembly; cells and returned collections are safe to share. */
    public static final class Assembly {
        private final int columnHeight;
        private final FootKind kind;
        private final List<Cell> feet;
        private final Set<Cell> membership;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private Assembly(int height, FootKind kind, Set<Cell> members,
                         int minX, int maxX, int minZ, int maxZ) {
            this.columnHeight = height;
            this.kind = kind;
            List<Cell> ordered = new ArrayList<Cell>(members);
            Collections.sort(ordered, new Comparator<Cell>() {
                @Override
                public int compare(Cell a, Cell b) {
                    int zCompare = Integer.compare(a.z, b.z);
                    return zCompare != 0 ? zCompare : Integer.compare(a.x, b.x);
                }
            });
            this.feet = Collections.unmodifiableList(ordered);
            this.membership = Collections.unmodifiableSet(new HashSet<Cell>(members));
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public int columnHeight() { return columnHeight; }
        public FootKind kind() { return kind; }
        public List<Cell> feet() { return feet; }
        public int minX() { return minX; }
        public int maxX() { return maxX; }
        public int minZ() { return minZ; }
        public int maxZ() { return maxZ; }
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
        public Cell anchorFoot() { return new Cell(0, -columnHeight, 0); }

        /** N=-Z, E=+X, S=+Z, W=-X. A nonmember has mask zero. */
        public int connectionMask(Cell cell) {
            if (!membership.contains(cell)) return 0;
            int mask = 0;
            if (membership.contains(cell.add(0, 0, -1))) mask |= NORTH;
            if (membership.contains(cell.add(1, 0, 0))) mask |= EAST;
            if (membership.contains(cell.add(0, 0, 1))) mask |= SOUTH;
            if (membership.contains(cell.add(-1, 0, 0))) mask |= WEST;
            return mask;
        }
    }

    /** Validate a group while also rejecting mixed plate/ski members. */
    public static Result validate(int columnHeight, Collection<Foot> feet) {
        if (feet == null) return invalid("Foot collection is missing.");
        if (feet.size() > MAX_FEET) return invalid("An assembly may contain at most 64 feet.");
        FootKind kind = null;
        List<Cell> offsets = new ArrayList<Cell>();
        for (Foot foot : feet) {
            if (foot == null || foot.offset == null || foot.kind == null) {
                return invalid("A foot is missing its position or kind.");
            }
            if (kind == null) kind = foot.kind;
            if (kind != foot.kind) return invalid("Plates and skis cannot share one assembly.");
            offsets.add(foot.offset);
            if (offsets.size() > MAX_FEET) {
                return invalid("An assembly may contain at most 64 feet.");
            }
        }
        return validate(columnHeight, kind, offsets);
    }

    /**
     * Validate a complete, already homogeneous foot group.
     * Invalid input returns an error and no assembly, never a clipped subset.
     */
    public static Result validate(int columnHeight, FootKind kind,
                                  Collection<Cell> footOffsets) {
        if (columnHeight < 1 || columnHeight > MAX_COLUMN_HEIGHT) {
            return invalid("An extender column must contain between 1 and 8 blocks.");
        }
        if (kind == null) return invalid("Choose either plate or ski feet.");
        if (footOffsets == null || footOffsets.isEmpty()) {
            return invalid("An assembly needs at least one foot.");
        }
        if (footOffsets.size() > MAX_FEET) {
            return invalid("An assembly may contain at most 64 feet.");
        }

        Set<Cell> members = new HashSet<Cell>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : footOffsets) {
            if (cell == null) return invalid("A foot position is missing.");
            if (cell.y != -columnHeight) {
                return invalid("All feet must share the plane directly below the extender column.");
            }
            if (!members.add(cell)) return invalid("A foot position occurs more than once.");
            if (members.size() > MAX_FEET) {
                return invalid("An assembly may contain at most 64 feet.");
            }
            minX = Math.min(minX, cell.x);
            maxX = Math.max(maxX, cell.x);
            minZ = Math.min(minZ, cell.z);
            maxZ = Math.max(maxZ, cell.z);
            // Use long arithmetic: even corrupt extreme integer coordinates
            // must fail safely instead of wrapping into a small positive span.
            if ((long) maxX - minX + 1 > MAX_HORIZONTAL_SPAN
                    || (long) maxZ - minZ + 1 > MAX_HORIZONTAL_SPAN) {
                return invalid("The footprint may span at most 16 blocks along either horizontal axis.");
            }
        }

        Cell anchor = new Cell(0, -columnHeight, 0);
        if (!members.contains(anchor)) {
            return invalid("A foot must sit directly beneath the fixed extender column.");
        }

        Set<Cell> visited = new HashSet<Cell>();
        ArrayDeque<Cell> queue = new ArrayDeque<Cell>();
        visited.add(anchor);
        queue.add(anchor);
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            enqueue(cell.add(0, 0, -1), members, visited, queue);
            enqueue(cell.add(1, 0, 0), members, visited, queue);
            enqueue(cell.add(0, 0, 1), members, visited, queue);
            enqueue(cell.add(-1, 0, 0), members, visited, queue);
        }
        if (visited.size() != members.size()) {
            return invalid("Every foot must connect by a horizontal face to the same group.");
        }
        return new Result(new Assembly(columnHeight, kind, members,
                minX, maxX, minZ, maxZ), "");
    }

    private static void enqueue(Cell cell, Set<Cell> members, Set<Cell> visited,
                                ArrayDeque<Cell> queue) {
        if (members.contains(cell) && visited.add(cell)) queue.addLast(cell);
    }

    private static Result invalid(String reason) {
        return new Result(null, reason);
    }
}
