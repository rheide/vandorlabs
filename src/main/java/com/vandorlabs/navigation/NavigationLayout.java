package com.vandorlabs.navigation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Java 8, no Minecraft/Forge dependencies. Computes immutable render assignments
 * from ONE COMPLETE connected component already filtered by kind and facing.
 * The host discovers members, verifies loaded chunks, and publishes the result.
 */
public final class NavigationLayout {
    public static final int NORTH = 1, EAST = 2, SOUTH = 4, WEST = 8;
    public static final int DEFAULT_MAX_CELLS = 256;
    public static final double OVERLAY_OFFSET_BLOCKS = 0.01 / 16.0;

    private NavigationLayout() { }

    public enum Kind {
        FLAT("flat"), ANGLED("angled");
        public final String id;
        Kind(String id) { this.id = id; }
    }

    public enum Facing {
        NORTH(0), EAST(90), SOUTH(180), WEST(270);
        public final int modelYRotation;
        Facing(int rotation) { this.modelYRotation = rotation; }
        public String serializedName() { return name().toLowerCase(Locale.ROOT); }

        private long projectX(Cell c) {
            switch (this) {
                case NORTH: return c.x;
                case EAST: return c.z;
                case SOUTH: return -(long)c.x;
                default: return -(long)c.z;
            }
        }
        private long projectZ(Cell c) {
            switch (this) {
                case NORTH: return c.z;
                case EAST: return -(long)c.x;
                case SOUTH: return -(long)c.z;
                default: return c.x;
            }
        }
        private Cell unproject(long x, int y, long z) {
            switch (this) {
                case NORTH: return new Cell(Math.toIntExact(x), y, Math.toIntExact(z));
                case EAST: return new Cell(Math.toIntExact(-z), y, Math.toIntExact(x));
                case SOUTH: return new Cell(Math.toIntExact(-x), y, Math.toIntExact(-z));
                default: return new Cell(Math.toIntExact(z), y, Math.toIntExact(-x));
            }
        }
        public Vec3 rotateDirection(Vec3 p) {
            switch (this) {
                case NORTH: return p;
                case EAST: return new Vec3(-p.z, p.y, p.x);
                case SOUTH: return new Vec3(-p.x, p.y, -p.z);
                default: return new Vec3(p.z, p.y, -p.x);
            }
        }
    }

    public static final class Cell {
        public final int x, y, z;
        public Cell(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Cell)) return false;
            Cell c = (Cell)other;
            return x == c.x && y == c.y && z == c.z;
        }
        @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
        @Override public String toString() { return "(" + x + "," + y + "," + z + ")"; }
    }

    public static final class Vec3 {
        public final double x, y, z;
        public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public Vec3 plus(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
        public Vec3 minus(Vec3 b) { return new Vec3(x - b.x, y - b.y, z - b.z); }
        public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
        @Override public String toString() { return "(" + x + "," + y + "," + z + ")"; }
    }

    public static final class Tile {
        public final Cell cell;
        public final int localX, localZ, connections;
        public final boolean chip;
        private final Kind kind;
        private final Facing facing;
        private Tile(Cell cell, int x, int z, int mask, boolean chip, Kind kind, Facing facing) {
            this.cell = cell; localX = x; localZ = z; connections = mask;
            this.chip = chip; this.kind = kind; this.facing = facing;
        }
        public String blockstateVariant() {
            return "chip=" + chip + ",connections=" + connections + ",facing=" + facing.serializedName();
        }
        public String modelFileName() {
            return String.format(Locale.ROOT, "mask_%02d%s.obj", connections, chip ? "_chip" : "");
        }
        /** Forge blockstate model value; the blockstate loader supplies block/. */
        public String blockstateModel() {
            return "vandorlabs:navigation_table/" + kind.id + "/" + modelFileName();
        }
        /** Direct ModelLoaderRegistry ResourceLocation; models/ is supplied by the loader. */
        public String directModelResource() {
            return "vandorlabs:block/navigation_table/" + kind.id + "/" + modelFileName();
        }
    }

    public static final class Screen {
        /** World-space corners in blocks, ordered bottom-left/right, top-right/left. */
        public final Vec3 bottomLeft, bottomRight, topRight, topLeft;
        public final Vec3 normal, basisU, basisV;
        public final double widthBlocks, heightBlocks;
        private Screen(Layout layout) {
            double rightX = layout.width - 2.0 / 16.0;
            double leftX = 2.0 / 16.0;
            double frontZ = 2.0 / 16.0;
            double rearZ = (layout.kind == Kind.FLAT ? layout.depth : 1) - 2.0 / 16.0;
            double frontY = (layout.kind == Kind.FLAT ? 14.0 : 8.0) / 16.0;
            double rearY = 14.0 / 16.0;
            bottomLeft = layout.worldPoint(new Vec3(rightX, frontY, frontZ));
            bottomRight = layout.worldPoint(new Vec3(leftX, frontY, frontZ));
            topRight = layout.worldPoint(new Vec3(leftX, rearY, rearZ));
            topLeft = layout.worldPoint(new Vec3(rightX, rearY, rearZ));
            double h = Math.hypot(rearZ - frontZ, rearY - frontY);
            Vec3 v = new Vec3(0, (rearY - frontY) / h, (rearZ - frontZ) / h);
            basisU = layout.facing.rotateDirection(new Vec3(-1, 0, 0));
            basisV = layout.facing.rotateDirection(v);
            normal = layout.facing.rotateDirection(new Vec3(0, v.z, -v.y));
            widthBlocks = rightX - leftX; heightBlocks = h;
        }
        /** Image s/t in [0,1], top-origin t. Offset is already applied once. */
        public Vec3 overlayPoint(double s, double t) {
            return bottomLeft.plus(bottomRight.minus(bottomLeft).scale(s))
                    .plus(topLeft.minus(bottomLeft).scale(1.0 - t))
                    .plus(normal.scale(OVERLAY_OFFSET_BLOCKS));
        }
    }

    public static final class Layout {
        public final Kind kind;
        public final Facing facing;
        public final int width, depth, worldY;
        /** World block at local cell (0,0); not necessarily minimum world X/Z. */
        public final Cell originCell, chipOwner;
        public final Map<Cell, Tile> tiles;
        private Layout(Kind kind, Facing facing, int width, int depth, Cell origin,
                       Cell owner, Map<Cell, Tile> tiles) {
            this.kind = kind; this.facing = facing; this.width = width; this.depth = depth;
            worldY = origin.y; originCell = origin; chipOwner = owner;
            this.tiles = Collections.unmodifiableMap(new LinkedHashMap<Cell, Tile>(tiles));
        }
        /** Assembly-local position in BLOCK units -> world position in blocks. */
        public Vec3 worldPoint(Vec3 p) {
            switch (facing) {
                case NORTH: return new Vec3(originCell.x + p.x, worldY + p.y, originCell.z + p.z);
                case EAST: return new Vec3(originCell.x + 1.0 - p.z, worldY + p.y, originCell.z + p.x);
                case SOUTH: return new Vec3(originCell.x + 1.0 - p.x, worldY + p.y, originCell.z + 1.0 - p.z);
                default: return new Vec3(originCell.x + p.z, worldY + p.y, originCell.z + 1.0 - p.x);
            }
        }
        /** Convert a module-local manifest anchor (MODEL PIXELS) to world blocks. */
        public Vec3 moduleAnchor(Cell cell, Vec3 modelPixels) {
            Tile t = tiles.get(cell);
            if (t == null) throw new IllegalArgumentException("Cell is not a member: " + cell);
            return worldPoint(new Vec3(t.localX + modelPixels.x / 16.0,
                    modelPixels.y / 16.0, t.localZ + modelPixels.z / 16.0));
        }
        public Screen screen() { return new Screen(this); }
    }

    public static Layout build(Kind kind, Facing facing, Collection<Cell> members, boolean complete) {
        return build(kind, facing, members, complete, DEFAULT_MAX_CELLS);
    }

    /**
     * complete must certify the ENTIRE matching connected component and its
     * boundary were discovered in loaded data. Never call this independently
     * for each visible chunk or for an arbitrary rectangle cut from a component.
     */
    public static Layout build(Kind kind, Facing facing, Collection<Cell> members,
                               boolean complete, int maxCells) {
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(members, "members");
        if (!complete) throw new IllegalArgumentException("A complete loaded component is required");
        if (maxCells < 1 || members.isEmpty() || members.size() > maxCells)
            throw new IllegalArgumentException("Component size must be 1.." + maxCells);
        Set<Cell> unique = new LinkedHashSet<Cell>();
        long minX = Long.MAX_VALUE, minZ = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        Integer y = null;
        for (Cell c : members) {
            Objects.requireNonNull(c, "member");
            if (!unique.add(c)) throw new IllegalArgumentException("Duplicate cell: " + c);
            if (y == null) y = c.y;
            if (c.y != y) throw new IllegalArgumentException("Members must share one Y level");
            long px = facing.projectX(c), pz = facing.projectZ(c);
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
        }
        long w = maxX - minX + 1L, d = maxZ - minZ + 1L;
        if (w > maxCells || d > maxCells || w * d != unique.size())
            throw new IllegalArgumentException("Component must be one filled rectangle without holes");
        if (kind == Kind.ANGLED && d != 1)
            throw new IllegalArgumentException("Angled consoles connect side by side only");
        int width = (int)w, depth = (int)d;
        int ownerX = (width - 1) / 2;
        Cell origin = facing.unproject(minX, y, minZ);
        Cell owner = facing.unproject(minX + ownerX, y, minZ);
        Map<Cell, Tile> tiles = new LinkedHashMap<Cell, Tile>();
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                Cell c = facing.unproject(minX + x, y, minZ + z);
                if (!unique.contains(c)) throw new IllegalArgumentException("Missing cell: " + c);
                int mask = (z > 0 ? NORTH : 0) | (x + 1 < width ? EAST : 0)
                        | (z + 1 < depth ? SOUTH : 0) | (x > 0 ? WEST : 0);
                tiles.put(c, new Tile(c, x, z, mask, x == ownerX && z == 0, kind, facing));
            }
        }
        return new Layout(kind, facing, width, depth, origin, owner, tiles);
    }
}
