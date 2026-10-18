package com.vandorlabs.turntable;

/**
 * Rotation-axis math shared by the Turntable blocks, renderers and checks.
 * Minecraft-free so the same arithmetic is exercised by the portable tests.
 *
 * Axes are identified by the ordinal of a Minecraft {@code EnumFacing.Axis}
 * ordering used here explicitly: Y = 0, X = 1, Z = 2. A positive angle turns
 * counter-clockwise when looking from the positive end of the axis back toward
 * the origin, matching {@code GlStateManager.rotate}.
 */
public final class TurntableGeometry {
    public static final int AXIS_Y = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;

    public static final int CLOCKWISE = 0;
    public static final int COUNTER_CLOCKWISE = 1;

    /** Hard limits shared by the dialog and the scanner. */
    public static final int MIN_HEIGHT = 1;
    public static final int MAX_HEIGHT = 16;
    public static final int MAX_PLATFORM = 16 * 16;
    public static final int MAX_PAYLOAD = 4096;
    public static final double MAX_SPEED = 360.0;
    public static final double MIN_SPEED = 1.0;

    private TurntableGeometry() { }

    public static boolean validAxis(int axis) {
        return axis >= AXIS_Y && axis <= AXIS_Z;
    }

    /** The rotation sign used for a dialog direction choice. */
    public static int sign(int direction) {
        return direction == COUNTER_CLOCKWISE ? 1 : -1;
    }

    public static int clampHeight(int height) {
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
    }

    public static double clampSpeed(double speed) {
        if (!Double.isFinite(speed)) return 90.0;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /** Wrap an angle into [0, 360). */
    public static double normalize(double degrees) {
        if (!Double.isFinite(degrees)) return 0;
        double wrapped = degrees % 360.0;
        if (wrapped < 0) wrapped += 360.0;
        return wrapped == 360.0 ? 0.0 : wrapped;
    }

    /**
     * Angle of a turning platform. {@code now} and {@code start} are world
     * ticks; {@code partial} is the client frame fraction in [0, 1).
     */
    public static double angle(double startAngle, long now, long start, float partial,
            double degreesPerSecond, int direction) {
        double seconds = (now - start + partial) / 20.0;
        return normalize(startAngle + seconds * clampSpeed(degreesPerSecond) * sign(direction));
    }

    /** A platform of equal width and depth is a valid turntable. */
    public static boolean isSquare(int width, int depth) {
        return width > 0 && width == depth;
    }

    /** A valid platform also fills every cell of its square, with no gaps. */
    public static boolean isFilledSquare(int cells, int size) {
        return size > 0 && cells == size * size;
    }

    /** Run-length and column count of a scanned plane, used for square checks. */
    public static int[] extents(java.util.Collection<int[]> plane) {
        int minA = Integer.MAX_VALUE, minB = Integer.MAX_VALUE;
        int maxA = Integer.MIN_VALUE, maxB = Integer.MIN_VALUE;
        for (int[] cell : plane) {
            minA = Math.min(minA, cell[0]);
            maxA = Math.max(maxA, cell[0]);
            minB = Math.min(minB, cell[1]);
            maxB = Math.max(maxB, cell[1]);
        }
        if (plane.isEmpty()) return new int[] { 0, 0 };
        return new int[] { maxA - minA + 1, maxB - minB + 1 };
    }

    /**
     * Rotate {@code (x, y, z)} about the given axis by {@code angleDegrees}
     * around the origin. Used by tests and by the renderer's pivot math.
     */
    public static double[] rotate(double x, double y, double z, int axis, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        double cos = Math.cos(radians), sin = Math.sin(radians);
        switch (axis) {
            case AXIS_X:
                return new double[] { x, y * cos - z * sin, y * sin + z * cos };
            case AXIS_Z:
                return new double[] { x * cos - y * sin, x * sin + y * cos, z };
            default:
                return new double[] { x * cos + z * sin, y, -x * sin + z * cos };
        }
    }

    /** The two in-plane axes (a, b) of a rotation axis, in right-handed order. */
    public static int[] planeAxes(int axis) {
        switch (axis) {
            case AXIS_X: return new int[] { AXIS_Y, AXIS_Z };
            case AXIS_Z: return new int[] { AXIS_X, AXIS_Y };
            default: return new int[] { AXIS_X, AXIS_Z };
        }
    }

    /** A cell inside the plane is filled when its step stays within the square. */
    public static boolean withinSquare(int a, int b, int size) {
        return a >= 0 && b >= 0 && a < size && b < size;
    }
}
