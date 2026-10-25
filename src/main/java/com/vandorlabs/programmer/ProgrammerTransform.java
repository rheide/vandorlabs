package com.vandorlabs.programmer;

/**
 * Java 8 math with no Minecraft dependencies.
 * All distances are blocks. The assembly origin is its minimum WORLD XYZ.
 */
public final class ProgrammerTransform {
    private ProgrammerTransform() { }

    public enum Facing { NORTH, EAST, SOUTH, WEST }

    /** A cardinal rotation plus the translation that keeps the rotated bounds positive. */
    public static final class Affine {
        public final double xx, xz, zx, zz, offsetX, offsetZ;
        public final double worldWidth, worldDepth;

        private Affine(double xx, double xz, double zx, double zz,
                       double offsetX, double offsetZ, double worldWidth, double worldDepth) {
            this.xx = xx; this.xz = xz; this.zx = zx; this.zz = zz;
            this.offsetX = offsetX; this.offsetZ = offsetZ;
            this.worldWidth = worldWidth; this.worldDepth = worldDepth;
        }

        public double x(double localX, double localZ) {
            return xx * localX + xz * localZ + offsetX;
        }

        public double z(double localX, double localZ) {
            return zx * localX + zz * localZ + offsetZ;
        }

        /** Position in blocks, relative to the minimum world corner of the assembly. */
        public double[] point(double localX, double localY, double localZ) {
            return new double[] { x(localX, localZ), localY, z(localX, localZ) };
        }

        /** Directions and normals rotate without the position offset or scale. */
        public double[] direction(double localX, double localY, double localZ) {
            return new double[] {
                xx * localX + xz * localZ, localY, zx * localX + zz * localZ
            };
        }
    }

    public static Affine of(Facing facing, double width, double depth) {
        if (facing == null) throw new IllegalArgumentException("Missing horizontal facing");
        if (!(width > 0) || !(depth > 0) || Double.isInfinite(width) || Double.isInfinite(depth))
            throw new IllegalArgumentException("Invalid programmer dimensions");
        switch (facing) {
            case NORTH: return new Affine( 1,  0,  0,  1,     0,     0, width, depth);
            case EAST:  return new Affine( 0, -1,  1,  0, depth,     0, depth, width);
            case SOUTH: return new Affine(-1,  0,  0, -1, width, depth, width, depth);
            case WEST:  return new Affine( 0,  1, -1,  0,     0, width, depth, width);
            default: throw new AssertionError(facing);
        }
    }

    public static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0) return 0;
        return value >= 1 ? 1 : value;
    }
}
