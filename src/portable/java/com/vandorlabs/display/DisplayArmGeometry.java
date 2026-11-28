package com.vandorlabs.display;

import com.vandorlabs.render.ScreenSurface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One geometry source for drawing, picking, collision bounds and carried displays.
 * All coordinates are model pixels, relative to the owning block's lower corner.
 * Mount directions use Minecraft's stable DOWN/UP/NORTH/SOUTH/WEST/EAST indices.
 */
public final class DisplayArmGeometry {
    public static final int BASE = 0, ARM = 1, DETAIL = 2, HOUSING = 3;
    private static final Vec UP = new Vec(0, 1, 0);
    private static final Vec EAST = new Vec(1, 0, 0);
    private static final Vec SOUTH = new Vec(0, 0, 1);
    private static final Vec CENTER = new Vec(8, 8, 8);
    private static final Vec[] DIRECTIONS = {
            new Vec(0, -1, 0), UP, new Vec(0, 0, -1), SOUTH,
            new Vec(-1, 0, 0), EAST};
    // Frame renderers cache canonical quads by identity. Pose edits must reuse these four.
    private static final ScreenSurface.Quad[][] SURFACES = {
            {ScreenSurface.quad(-8, 8, -8, -1.02, 8, -1.02),
                    ScreenSurface.quad(-8, 8, -7.98, -9, -7.98, -1)},
            {ScreenSurface.quad(-8, 8, -4, -1.02, 4, -1.02),
                    ScreenSurface.quad(-8, 8, -3.98, -9, -3.98, -1)}};

    /** Outward-wound faces of Box.corners(), in -X,+X,-Y,+Y,-Z,+Z order. */
    public static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

    public final int mountFace, screenFacing;
    public final DisplayArmSettings settings;
    public final Pose display;
    public final Vec baseCenter, baseSocket, rearSocket;
    public final List<Box> boxes;
    public final Bounds bounds;
    private final ScreenSurface.Quad screen, deck;

    private DisplayArmGeometry(int mountFace, int screenFacing, DisplayArmSettings settings) {
        this.mountFace = mountFace >= 0 && mountFace < 6 ? mountFace : 1;
        this.screenFacing = screenFacing >= 2 && screenFacing < 6 ? screenFacing : 2;
        this.settings = settings == null ? DisplayArmSettings.DEFAULT : settings;
        DisplayArmSettings s = this.settings;

        Vec front = DIRECTIONS[this.screenFacing];
        Vec rear = front.scale(-1), left = UP.cross(rear);
        // Canonical +X follows the existing screen UV convention (viewer-left).
        // UI Right is the opposite vector. Offsets intentionally ignore yaw/tilt/roll.
        Vec screenCenter = CENTER.add(left.scale(-s.right)).add(UP.scale(s.height))
                .add(front.scale(s.forward));
        display = new Pose(screenCenter,
                rotate(EAST, left, UP, rear, s),
                rotate(UP, left, UP, rear, s),
                rotate(SOUTH, left, UP, rear, s));

        List<Box> parts = new ArrayList<>();
        Vec normal = DIRECTIONS[this.mountFace];
        baseCenter = CENTER.subtract(normal.scale(8));
        Pose mount = along(baseCenter, normal);
        parts.add(new Box(BASE, mount.point(0, 0, .5), mount.xAxis, mount.yAxis,
                mount.zAxis, 3, 3, .5));
        // Four restrained fasteners and a small socket distinguish the plate from the shaft.
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1})
            parts.add(new Box(DETAIL, mount.point(x * 2.1, y * 2.1, 1.15),
                    mount.xAxis, mount.yAxis, mount.zAxis, .3, .3, .15));
        parts.add(new Box(BASE, mount.point(0, 0, 1.5), mount.xAxis, mount.yAxis,
                mount.zAxis, 1.3, 1.3, .5));
        Vec stubStart = mount.point(0, 0, 2);
        baseSocket = mount.point(0, 0, 4);
        rearSocket = display.point(0, 0, 2.5);
        parts.add(beam(stubStart, baseSocket, .625, ARM));
        parts.add(beam(baseSocket, rearSocket, .625, ARM));
        parts.add(new Box(BASE, baseSocket, mount.xAxis, mount.yAxis, mount.zAxis,
                .9, .9, .9));
        parts.add(new Box(BASE, display.point(0, 0, 1.5), display.xAxis,
                display.yAxis, display.zAxis, 2, 2, .5));
        parts.add(new Box(DETAIL, rearSocket, display.xAxis, display.yAxis,
                display.zAxis, .9, .9, .5));

        double halfHeight = s.half ? 4 : 8;
        parts.add(new Box(HOUSING, display.center, display.xAxis, display.yAxis,
                display.zAxis, 8, halfHeight, 1));
        if (s.input)
            parts.add(new Box(HOUSING, display.point(0, -halfHeight - .5, -5),
                    display.xAxis, display.yAxis, display.zAxis, 8, .5, 4));
        screen = SURFACES[s.half ? 1 : 0][0];
        deck = SURFACES[s.half ? 1 : 0][1];
        boxes = Collections.unmodifiableList(parts);
        Bounds all = parts.get(0).bounds();
        for (int i = 1; i < parts.size(); i++) all = all.union(parts.get(i).bounds());
        // Include the tiny artwork depth bias as well as the solid geometry.
        for (int slot = 0; slot < (s.input ? 2 : 1); slot++)
            for (ScreenSurface.Vertex vertex : surface(slot).vertices)
                all = all.include(display.point(vertex.x, vertex.y, vertex.z));
        bounds = all;
    }

    public static DisplayArmGeometry create(int mountFace, int screenFacing,
            DisplayArmSettings settings) {
        return new DisplayArmGeometry(mountFace, screenFacing, settings);
    }

    /** Canonical display-local artwork, transformed with display for drawing and picking. */
    public ScreenSurface.Quad surface(int slot) {
        if (slot == 0) return screen;
        if (slot == 1) return deck;
        throw new IllegalArgumentException("Display arm surface slot must be 0 or 1");
    }

    public boolean hasSurface(int slot) {
        return slot == 0 || slot == 1 && settings.input;
    }

    /** Nearest solid component intersected by a pixel-space segment, or NaN when missed. */
    public double ray(Vec start, Vec end) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Box box : boxes) {
            double t = box.ray(start, end);
            if (!Double.isNaN(t)) nearest = Math.min(nearest, t);
        }
        return Double.isInfinite(nearest) ? Double.NaN : nearest;
    }

    /** Build an oriented plain square shaft. Coincident endpoints remain a finite joint. */
    public static Box beam(Vec start, Vec end, double radius, int kind) {
        if (!(radius > 0) || !Double.isFinite(radius))
            throw new IllegalArgumentException("Display arm shaft radius must be positive");
        Vec delta = end.subtract(start);
        double length = delta.length();
        Pose pose = along(start.add(end).scale(.5), length < 1e-9 ? SOUTH : delta.scale(1 / length));
        return new Box(kind, pose.center, pose.xAxis, pose.yAxis, pose.zAxis,
                radius, radius, Math.max(length / 2, radius));
    }

    private static Pose along(Vec center, Vec normal) {
        Vec reference = Math.abs(normal.y) < .9 ? UP : EAST;
        Vec x = reference.cross(normal).unit(), y = normal.cross(x);
        return new Pose(center, x, y, normal);
    }

    /** Intrinsic yaw, then pitch, then roll; cardinal screen facing is the outer frame. */
    private static Vec rotate(Vec v, Vec xAxis, Vec yAxis, Vec zAxis, DisplayArmSettings s) {
        double roll = Math.toRadians(s.roll), pitch = Math.toRadians(s.pitch), yaw = Math.toRadians(s.yaw);
        double x = v.x * Math.cos(roll) - v.y * Math.sin(roll);
        double y = v.x * Math.sin(roll) + v.y * Math.cos(roll), z = v.z;
        double py = y * Math.cos(pitch) - z * Math.sin(pitch);
        z = y * Math.sin(pitch) + z * Math.cos(pitch);
        y = py;
        double px = x * Math.cos(yaw) + z * Math.sin(yaw);
        z = -x * Math.sin(yaw) + z * Math.cos(yaw);
        return xAxis.scale(px).add(yAxis.scale(y)).add(zAxis.scale(z));
    }

    public static final class Vec {
        public final double x, y, z;
        public Vec(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public Vec add(Vec value) { return new Vec(x + value.x, y + value.y, z + value.z); }
        public Vec subtract(Vec value) { return new Vec(x - value.x, y - value.y, z - value.z); }
        public Vec scale(double value) { return new Vec(x * value, y * value, z * value); }
        public double dot(Vec value) { return x * value.x + y * value.y + z * value.z; }
        public Vec cross(Vec value) {
            return new Vec(y * value.z - z * value.y, z * value.x - x * value.z,
                    x * value.y - y * value.x);
        }
        public double length() { return Math.sqrt(dot(this)); }
        public Vec unit() {
            double length = length();
            if (!(length > 0) || !Double.isFinite(length))
                throw new IllegalArgumentException("Display arm direction must be finite and nonzero");
            return scale(1 / length);
        }
    }

    /** Orthonormal right-handed pose. +Z points behind the authored screen. */
    public static final class Pose {
        public final Vec center, xAxis, yAxis, zAxis;
        private Pose(Vec center, Vec xAxis, Vec yAxis, Vec zAxis) {
            this.center = center; this.xAxis = xAxis; this.yAxis = yAxis; this.zAxis = zAxis;
        }
        public Vec point(double x, double y, double z) {
            return center.add(xAxis.scale(x)).add(yAxis.scale(y)).add(zAxis.scale(z));
        }
        public Vec inverse(double x, double y, double z) {
            Vec local = new Vec(x, y, z).subtract(center);
            return new Vec(local.dot(xAxis), local.dot(yAxis), local.dot(zAxis));
        }
        public Vec inverse(Vec point) { return inverse(point.x, point.y, point.z); }
    }

    public static final class Box {
        public final int kind;
        public final Vec center, xAxis, yAxis, zAxis;
        public final double halfX, halfY, halfZ;
        private Box(int kind, Vec center, Vec xAxis, Vec yAxis, Vec zAxis,
                double halfX, double halfY, double halfZ) {
            this.kind = kind; this.center = center;
            this.xAxis = xAxis; this.yAxis = yAxis; this.zAxis = zAxis;
            this.halfX = halfX; this.halfY = halfY; this.halfZ = halfZ;
        }
        public Vec point(double x, double y, double z) {
            return center.add(xAxis.scale(x)).add(yAxis.scale(y)).add(zAxis.scale(z));
        }
        public Vec inverse(Vec point) {
            Vec local = point.subtract(center);
            return new Vec(local.dot(xAxis), local.dot(yAxis), local.dot(zAxis));
        }
        /** Binary order: X bit 2, Y bit 1, Z bit 0; set bits select the positive side. */
        public Vec[] corners() {
            Vec[] result = new Vec[8];
            for (int i = 0; i < 8; i++) result[i] = point((i & 4) == 0 ? -halfX : halfX,
                    (i & 2) == 0 ? -halfY : halfY, (i & 1) == 0 ? -halfZ : halfZ);
            return result;
        }
        public Bounds bounds() {
            double x = Math.abs(xAxis.x) * halfX + Math.abs(yAxis.x) * halfY + Math.abs(zAxis.x) * halfZ;
            double y = Math.abs(xAxis.y) * halfX + Math.abs(yAxis.y) * halfY + Math.abs(zAxis.y) * halfZ;
            double z = Math.abs(xAxis.z) * halfX + Math.abs(yAxis.z) * halfY + Math.abs(zAxis.z) * halfZ;
            return new Bounds(center.x - x, center.y - y, center.z - z,
                    center.x + x, center.y + y, center.z + z);
        }
        public double ray(Vec start, Vec end) {
            Vec a = inverse(start), b = inverse(end);
            double[] origin = {a.x, a.y, a.z}, delta = {b.x - a.x, b.y - a.y, b.z - a.z};
            double[] half = {halfX, halfY, halfZ};
            double near = 0, far = 1;
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(delta[axis]) < 1e-10) {
                    if (origin[axis] < -half[axis] || origin[axis] > half[axis]) return Double.NaN;
                    continue;
                }
                double first = (-half[axis] - origin[axis]) / delta[axis];
                double last = (half[axis] - origin[axis]) / delta[axis];
                near = Math.max(near, Math.min(first, last));
                far = Math.min(far, Math.max(first, last));
                if (near > far) return Double.NaN;
            }
            return near;
        }
    }

    public static final class Bounds {
        public final double minX, minY, minZ, maxX, maxY, maxZ;
        private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public Bounds union(Bounds other) {
            return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        public Bounds include(Vec point) {
            return new Bounds(Math.min(minX, point.x), Math.min(minY, point.y),
                    Math.min(minZ, point.z), Math.max(maxX, point.x),
                    Math.max(maxY, point.y), Math.max(maxZ, point.z));
        }
        public boolean contains(Vec point) {
            return point.x >= minX - 1e-8 && point.x <= maxX + 1e-8
                    && point.y >= minY - 1e-8 && point.y <= maxY + 1e-8
                    && point.z >= minZ - 1e-8 && point.z <= maxZ + 1e-8;
        }
    }
}
