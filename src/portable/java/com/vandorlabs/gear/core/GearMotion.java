package com.vandorlabs.gear.core;

/**
 * Shared animation math, in block units. The top extender is local block 0.
 * The mounting cap AND outer sleeve stay outside the moving transform.
 * Pure Java 8, with no client, Forge or OpenGL dependencies.
 */
public final class GearMotion {
    public static final double PIVOT_X = 0.5;
    public static final double PIVOT_Y = 9.0 / 16.0;
    public static final double PIVOT_Z = 0.5;
    public static final double RETRACTED_FOOT_Y = 3.0 / 16.0;
    public static final double FOOT_SOCKET_Y = 5.0 / 16.0;
    public static final double ROD_TOP_Y = 9.0 / 16.0;

    public enum Mode {
        EXTEND,
        ROTATE,
        /** A rigid swinging arm with a counter-rotating lower foot swivel. */
        ROTATE_LEVEL
    }

    /** World-cardinal destination of the foot as it folds upward by 90 degrees. */
    public enum Direction {
        NORTH(1, 0), EAST(0, 1), SOUTH(-1, 0), WEST(0, -1);

        private final int axisX;
        private final int axisZ;

        Direction(int axisX, int axisZ) {
            this.axisX = axisX;
            this.axisZ = axisZ;
        }

        public int axisX() { return axisX; }
        public int axisY() { return 0; }
        public int axisZ() { return axisZ; }
    }

    private GearMotion() { }

    public static final class Vec {
        public final double x;
        public final double y;
        public final double z;

        public Vec(double x, double y, double z) {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() { return "(" + x + "," + y + "," + z + ")"; }
    }

    /** Axis-aligned bounds, useful for preview and expanded TESR render bounds. */
    public static final class Bounds {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        public Bounds(double minX, double minY, double minZ,
                      double maxX, double maxY, double maxZ) {
            requireFinite(minX, "minX");
            requireFinite(minY, "minY");
            requireFinite(minZ, "minZ");
            requireFinite(maxX, "maxX");
            requireFinite(maxY, "maxY");
            requireFinite(maxZ, "maxZ");
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Bounds minimum exceeds maximum.");
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean contains(Vec point) {
            double epsilon = 1e-9;
            return point.x >= minX - epsilon && point.x <= maxX + epsilon
                    && point.y >= minY - epsilon && point.y <= maxY + epsilon
                    && point.z >= minZ - epsilon && point.z <= maxZ + epsilon;
        }
    }

    public static final class Pose {
        private final Mode mode;
        private final Direction direction;
        private final int columnHeight;
        private final double deployProgress;
        private final double easedDeployProgress;
        private final double offsetY;
        private final double angleDegrees;
        private final double sine;
        private final double cosine;
        private final double levelOffsetX;
        private final double levelOffsetY;
        private final double levelOffsetZ;

        private Pose(Mode mode, Direction direction, int height, double progress,double stowedFootY) {
            this.mode = mode;
            this.direction = direction;
            this.columnHeight = height;
            this.deployProgress = progress;
            this.easedDeployProgress = smoothstep(progress);
            double stowed = 1.0 - easedDeployProgress;
            this.offsetY = mode == Mode.EXTEND ? (height + stowedFootY) * stowed : 0.0;
            this.angleDegrees = mode != Mode.EXTEND ? 90.0 * stowed : 0.0;
            // Exact endpoints avoid tiny residual sideways translations in
            // fully deployed/stowed states and make joined faces coincide.
            this.sine = angleDegrees == 90.0 ? 1.0 : Math.sin(Math.toRadians(angleDegrees));
            this.cosine = angleDegrees == 90.0 ? 0.0 : Math.cos(Math.toRadians(angleDegrees));
            if (mode == Mode.ROTATE_LEVEL) {
                double deployedAnkleY = -height + FOOT_SOCKET_Y;
                Vec swungAnkle = rotate(PIVOT_X, deployedAnkleY, PIVOT_Z);
                this.levelOffsetX = swungAnkle.x - PIVOT_X;
                this.levelOffsetY = swungAnkle.y - deployedAnkleY;
                this.levelOffsetZ = swungAnkle.z - PIVOT_Z;
            } else {
                this.levelOffsetX = 0.0;
                this.levelOffsetY = 0.0;
                this.levelOffsetZ = 0.0;
            }
        }

        public Mode mode() { return mode; }
        public Direction direction() { return direction; }
        public int columnHeight() { return columnHeight; }
        public double deployProgress() { return deployProgress; }
        public double easedDeployProgress() { return easedDeployProgress; }
        /** Telescoping offset only; rotating modes keep deployed local coordinates. */
        public double footOffsetY() { return offsetY; }
        /** Local foot coordinate before the selected swinging transform. */
        public double footY() { return -columnHeight + offsetY; }
        /** Local arm/rod endpoint before the selected swinging transform. */
        public double rodBottomY() { return footY() + FOOT_SOCKET_Y; }
        public double rodTopY() { return ROD_TOP_Y; }
        public double angleDegrees() { return angleDegrees; }

        /**
         * Input includes each foot's integer block offset in the DEPLOYED
         * assembly. All feet use this one transform and keep their spacing.
         *
         * <p>ROTATE_LEVEL uses one shared ankle A=(.5,-height+5/16,.5).
         * The arm moves that ankle to A'=P+R(A-P). The lower swivel cancels
         * the arm's rotation for the ENTIRE connected foot group, giving
         * v'=A'+R^-1(R(v-A))=v+(A'-A). Consequently feet and their socket
         * receive only the same translation; their normals stay upright.
         * No member acquires its own pivot or an independent rotation.</p>
         */
        public Vec transformFootVertex(double x, double y, double z) {
            if (mode == Mode.EXTEND) return new Vec(x, y + offsetY, z);
            if (mode == Mode.ROTATE_LEVEL) {
                return new Vec(x + levelOffsetX, y + levelOffsetY, z + levelOffsetZ);
            }
            return rotate(x, y, z);
        }

        /**
         * Transform rigid moving rod/arm components about the upper endpoint
         * enclosed within the fixed housing. Both rotating modes use the
         * same arm transform. A ROTATE_LEVEL foot/socket must instead use
         * transformFootVertex() to stay upright at its lower swivel.
         * The mounting cap and outer sleeve remain stationary in every mode
         * and must never be passed through this transform.
         */
        public Vec transformRigidVertex(double x, double y, double z) {
            if (mode == Mode.EXTEND) return new Vec(x, y, z);
            return rotate(x, y, z);
        }

        /**
         * Optional endpoint-preserving transform for one telescoping rod
         * mesh. A renderer may instead draw a box between rodBottomY() and
         * rodTopY(), then apply transformRigidVertex().
         */
        public Vec transformRodVertex(double x, double deployedY, double z) {
            requireFinite(deployedY, "deployedY");
            if (mode != Mode.EXTEND) return rotate(x, deployedY, z);
            double deployedBottom = -columnHeight + FOOT_SOCKET_Y;
            double fractionFromTop = (ROD_TOP_Y - deployedY) / (ROD_TOP_Y - deployedBottom);
            double currentY = ROD_TOP_Y - fractionFromTop * (ROD_TOP_Y - rodBottomY());
            return new Vec(x, currentY, z);
        }

        public Bounds transformFootBounds(Bounds bounds) { return transformBounds(bounds, true); }
        public Bounds transformRigidBounds(Bounds bounds) { return transformBounds(bounds, false); }

        private Bounds transformBounds(Bounds bounds, boolean foot) {
            if (bounds == null) throw new IllegalArgumentException("Bounds are missing.");
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? bounds.minX : bounds.maxX;
                double y = (corner & 2) == 0 ? bounds.minY : bounds.maxY;
                double z = (corner & 4) == 0 ? bounds.minZ : bounds.maxZ;
                Vec transformed = foot ? transformFootVertex(x, y, z) : transformRigidVertex(x, y, z);
                minX = Math.min(minX, transformed.x);
                minY = Math.min(minY, transformed.y);
                minZ = Math.min(minZ, transformed.z);
                maxX = Math.max(maxX, transformed.x);
                maxY = Math.max(maxY, transformed.y);
                maxZ = Math.max(maxZ, transformed.z);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private Vec rotate(double x, double y, double z) {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
            double dx = x - PIVOT_X;
            double dy = y - PIVOT_Y;
            double dz = z - PIVOT_Z;
            // Rodrigues' rotation formula; the axis is one of +/-X or +/-Z.
            double ax = direction.axisX();
            double az = direction.axisZ();
            double dot = ax * dx + az * dz;
            double oneMinusCosine = 1.0 - cosine;
            return new Vec(PIVOT_X + dx * cosine - az * dy * sine + ax * dot * oneMinusCosine,
                    PIVOT_Y + dy * cosine + (az * dx - ax * dz) * sine,
                    PIVOT_Z + dz * cosine + ax * dy * sine + az * dot * oneMinusCosine);
        }
    }

    /**
     * Raw deploy progress: 0 is stowed and 1 is fully deployed. This method
     * applies smoothstep ONCE. Do not ease progress again in the renderer.
     */
    public static Pose pose(Mode mode, Direction direction, int columnHeight,
                            double deployProgress) {
        return pose(mode,direction,columnHeight,deployProgress,RETRACTED_FOOT_Y);
    }
    public static Pose pose(Mode mode, Direction direction, int columnHeight,
                            double deployProgress,double stowedFootY) {
        requireFinite(stowedFootY,"stowedFootY");
        if (mode == null || direction == null) {
            throw new IllegalArgumentException("Animation mode and direction are required.");
        }
        if (columnHeight < 1 || columnHeight > GearTopology.MAX_COLUMN_HEIGHT) {
            throw new IllegalArgumentException("Column height must be between 1 and 8.");
        }
        return new Pose(mode, direction, columnHeight, clampUnit(deployProgress),stowedFootY);
    }

    /** Ease a raw position; finite overshoot is clamped to the end states. */
    public static double smoothstep(double progress) {
        double p = clampUnit(progress);
        return p * p * (3.0 - 2.0 * p);
    }

    /**
     * Advance the EXISTING raw progress toward its target. Changing target
     * does not reset the starting pose, so reversal does not cause a jump.
     * Duration is the time for a complete stroke, in the same units as delta.
     */
    public static double advance(double currentProgress, boolean deployedTarget,
                                  double deltaTicks, double durationTicks) {
        double current = clampUnit(currentProgress);
        requireFinite(deltaTicks, "deltaTicks");
        requireFinite(durationTicks, "durationTicks");
        if (deltaTicks < 0.0 || durationTicks <= 0.0) {
            throw new IllegalArgumentException("Delta must be nonnegative and duration must be positive.");
        }
        double step = deltaTicks / durationTicks;
        // Clamp before addition so huge but finite inputs cannot produce
        // an infinite intermediate progress.
        if (step >= 1.0 || Double.isInfinite(step)) return deployedTarget ? 1.0 : 0.0;
        return deployedTarget ? Math.min(1.0, current + step) : Math.max(0.0, current - step);
    }

    /** Interpolate adjacent raw tick values before building a pose. */
    public static double interpolate(double previous, double current, double partialTick) {
        double a = clampUnit(previous);
        double b = clampUnit(current);
        double t = clampUnit(partialTick);
        return a + (b - a) * t;
    }

    private static double clampUnit(double value) {
        requireFinite(value, "progress");
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }
}
