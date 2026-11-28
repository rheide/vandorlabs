package com.vandorlabs.display;

import com.vandorlabs.display.DisplayArmGeometry.Box;
import com.vandorlabs.display.DisplayArmGeometry.Pose;
import com.vandorlabs.display.DisplayArmGeometry.Vec;
import com.vandorlabs.render.ScreenSurface;

/** Mount-independent poses, real component picking and persistence bounds without a loader. */
public final class DisplayArmGeometryTest {
    private static int checks;
    private static final Vec[] NORMALS = {
            new Vec(0, -1, 0), new Vec(0, 1, 0), new Vec(0, 0, -1),
            new Vec(0, 0, 1), new Vec(-1, 0, 0), new Vec(1, 0, 0)};

    private static void need(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError("Display arm: " + message);
    }

    private static void near(double actual, double expected, String message) {
        need(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-7,
                message + " (" + actual + " != " + expected + ")");
    }

    private static void near(Vec actual, Vec expected, String message) {
        near(actual.x, expected.x, message + " x");
        near(actual.y, expected.y, message + " y");
        near(actual.z, expected.z, message + " z");
    }

    private static void basis(Vec x, Vec y, Vec z) {
        near(x.length(), 1, "unit X"); near(y.length(), 1, "unit Y");
        near(z.length(), 1, "unit Z");
        near(x.dot(y), 0, "orthogonal XY"); near(y.dot(z), 0, "orthogonal YZ");
        near(z.dot(x), 0, "orthogonal ZX"); near(x.cross(y), z, "right-handed basis");
    }

    private static void settings() {
        DisplayArmSettings defaults = DisplayArmSettings.DEFAULT;
        need(!defaults.half && !defaults.input && defaults.right == 0 && defaults.height == 0
                && defaults.forward == 4 && defaults.yaw == 0 && defaults.pitch == 0
                && defaults.roll == 0, "fresh arm has four pixels of screen clearance");
        DisplayArmSettings clamped = new DisplayArmSettings(true, true,
                Integer.MIN_VALUE, Integer.MAX_VALUE, 1000, -1000, 1000, 1000);
        need(clamped.right == -32 && clamped.height == 32 && clamped.forward == 32
                && clamped.yaw == -180 && clamped.pitch == 90 && clamped.roll == 180,
                "bad saved values are bounded");
        need(DisplayArmSettings.valid(true, true, -32, 32, 0, -180, 90, 180),
                "network accepts inclusive boundaries");
        for (int parameter = 0; parameter < 6; parameter++) for (int sign : new int[]{-1, 1}) {
            int[] values = {0, 0, 0, 0, 0, 0};
            values[parameter] = sign * (parameter < 3 ? 33 : parameter == 4 ? 91 : 181);
            need(!DisplayArmSettings.valid(false, false, values[0], values[1], values[2],
                    values[3], values[4], values[5]), "network rejects parameter " + parameter);
        }
        DisplayArmSettings same = new DisplayArmSettings(false, false, 0, 0, 4, 0, 0, 0);
        need(defaults.equals(same) && defaults.hashCode() == same.hashCode(), "cache key equality");
        need(!defaults.equals(clamped) && !defaults.equals(null), "different settings key");
    }

    private static void cardinalOffsets() {
        Vec[] right = {new Vec(-1, 0, 0), new Vec(1, 0, 0),
                new Vec(0, 0, 1), new Vec(0, 0, -1)};
        for (int facing = 2; facing < 6; facing++) {
            DisplayArmSettings s = new DisplayArmSettings(false, false, 5, 7, 11, 0, 0, 0);
            DisplayArmGeometry geometry = DisplayArmGeometry.create(1, facing, s);
            Vec expected = new Vec(8, 15, 8).add(right[facing - 2].scale(5))
                    .add(NORMALS[facing].scale(11));
            near(geometry.display.center, expected, "viewer-right/height/forward cardinal " + facing);
            near(geometry.display.zAxis.scale(-1), NORMALS[facing], "screen front cardinal " + facing);
            near(geometry.display.xAxis.scale(-1), right[facing - 2], "UV X is viewer-left");
            DisplayArmGeometry rotated = DisplayArmGeometry.create(1, facing,
                    new DisplayArmSettings(false, false, 5, 7, 11, 125, 80, -37));
            near(rotated.display.center, expected, "position ignores added screen angles");
        }
        Pose north = DisplayArmGeometry.create(1, 2,
                new DisplayArmSettings(false, false, 0, 0, 0, 0, 0, 0)).display;
        near(north.center, new Vec(8, 8, 8), "zero offsets explicitly mean cell center");
        Pose pitch = DisplayArmGeometry.create(1, 2,
                new DisplayArmSettings(false, false, 0, 0, 0, 0, 90, 0)).display;
        near(pitch.zAxis.scale(-1), NORMALS[1], "positive ninety pitch faces upward");
        Pose yaw = DisplayArmGeometry.create(1, 2,
                new DisplayArmSettings(false, false, 0, 0, 0, 90, 0, 0)).display;
        near(yaw.zAxis.scale(-1), NORMALS[4], "positive ninety yaw faces west");
    }

    private static void components(DisplayArmGeometry geometry) {
        basis(geometry.display.xAxis, geometry.display.yAxis, geometry.display.zAxis);
        for (Vec local : new Vec[]{new Vec(0, 0, 0), new Vec(-8, 8, -1),
                new Vec(3.25, -5.5, 9.125), new Vec(-40, 50, -60)}) {
            Vec transformed = geometry.display.point(local.x, local.y, local.z);
            near(geometry.display.inverse(transformed), local, "pose forward/inverse round trip");
        }
        for (Box box : geometry.boxes) {
            basis(box.xAxis, box.yAxis, box.zAxis);
            need(box.halfX > 0 && box.halfY > 0 && box.halfZ > 0, "positive component volume");
            Vec[] corners = box.corners();
            for (Vec corner : corners) {
                need(geometry.bounds.contains(corner), "render bounds contain component corner");
                need(box.bounds().contains(corner), "component hull contains rotated corner");
                Vec local = box.inverse(corner);
                near(Math.abs(local.x), box.halfX, "corner local X");
                near(Math.abs(local.y), box.halfY, "corner local Y");
                near(Math.abs(local.z), box.halfZ, "corner local Z");
            }
            for (int[] face : DisplayArmGeometry.FACES) {
                Vec a = corners[face[0]], b = corners[face[1]], c = corners[face[2]];
                need(b.subtract(a).cross(c.subtract(b)).dot(a.subtract(box.center)) > 0,
                        "component quad has outward winding");
            }
            Vec start = box.center.add(box.zAxis.scale(box.halfZ + 10));
            Vec end = box.center.subtract(box.zAxis.scale(box.halfZ + 10));
            near(box.ray(start, end), 10 / (2 * (box.halfZ + 10)), "oriented-box central ray");
            near(box.ray(box.center, box.center), 0, "stationary ray inside component");
        }
        for (int slot = 0; slot < 2; slot++) {
            ScreenSurface.Quad quad = geometry.surface(slot);
            for (ScreenSurface.Vertex vertex : quad.vertices) {
                if (geometry.hasSurface(slot)) need(geometry.bounds.contains(
                        geometry.display.point(vertex.x, vertex.y, vertex.z)),
                        "render bounds contain artwork");
            }
        }
    }

    private static void allMounts() {
        int[][] angles = {{0, 0, 0}, {45, 30, -45}, {-180, -90, 180},
                {180, 90, -180}, {-135, 75, 110}};
        for (boolean half : new boolean[]{false, true}) for (boolean input : new boolean[]{false, true})
            for (int facing = 2; facing < 6; facing++) for (int[] angle : angles) {
                DisplayArmSettings s = new DisplayArmSettings(half, input, -7, 5, 12,
                        angle[0], angle[1], angle[2]);
                DisplayArmGeometry reference = DisplayArmGeometry.create(0, facing, s);
                for (int mount = 0; mount < 6; mount++) {
                    DisplayArmGeometry geometry = DisplayArmGeometry.create(mount, facing, s);
                    near(geometry.display.center, reference.display.center, "mount-independent screen center");
                    near(geometry.display.xAxis, reference.display.xAxis, "mount-independent screen X");
                    near(geometry.display.yAxis, reference.display.yAxis, "mount-independent screen Y");
                    near(geometry.display.zAxis, reference.display.zAxis, "mount-independent screen Z");
                    near(geometry.rearSocket, reference.rearSocket, "mount-independent screen attachment");
                    Vec expectedBase = new Vec(8, 8, 8).subtract(NORMALS[mount].scale(8));
                    near(geometry.baseCenter, expectedBase, "actual clicked support face");
                    Box plate = geometry.boxes.get(0);
                    double nearest = Double.POSITIVE_INFINITY, farthest = Double.NEGATIVE_INFINITY;
                    for (Vec point : plate.corners()) {
                        double depth = point.subtract(expectedBase).dot(NORMALS[mount]);
                        nearest = Math.min(nearest, depth); farthest = Math.max(farthest, depth);
                    }
                    near(nearest, 0, "mount plate contacts supporting plane");
                    near(farthest, 1, "mount plate protrudes one pixel");
                    ScreenSurface.Quad screen = geometry.surface(0), deck = geometry.surface(1);
                    need(screen == reference.surface(0) && deck == reference.surface(1),
                            "pose changes reuse canonical surface identities");
                    near(screen.topRight.x - screen.topLeft.x, 16, "both sizes retain full width");
                    near(screen.topRight.y - screen.bottomRight.y, half ? 8 : 16, "screen height variant");
                    near(screen.nz, -1, "screen artwork faces front");
                    near(deck.ny, 1, "input artwork faces upward");
                    near(deck.topLeft.z - deck.bottomLeft.z, 8, "input deck projects eight pixels");
                    need(geometry.hasSurface(0) && geometry.hasSurface(1) == input,
                            "input toggle controls second surface");
                    need(geometry.boxes.size() == (input ? 13 : 12), "one optional thin input housing");
                    components(geometry);
                }
            }
    }

    private static void extremeAndDegenerate() {
        for (int mount = 0; mount < 6; mount++) for (int offset : new int[]{-32, 32})
            components(DisplayArmGeometry.create(mount, 4, new DisplayArmSettings(true,
                    true, offset, offset, offset, 179, -89, -179)));
        Vec start = new Vec(1, 2, 3);
        for (Vec end : new Vec[]{start, start.add(new Vec(0, 100, 0)),
                start.add(new Vec(0, -100, 0)), start.add(new Vec(0, 0, 100)),
                start.add(new Vec(100, 0, 0)), start.add(new Vec(1e-12, 0, 0))}) {
            Box beam = DisplayArmGeometry.beam(start, end, .625, DisplayArmGeometry.ARM);
            basis(beam.xAxis, beam.yAxis, beam.zAxis);
            for (Vec corner : beam.corners()) need(Double.isFinite(corner.x)
                    && Double.isFinite(corner.y) && Double.isFinite(corner.z), "finite degenerate/vertical shaft");
            need(beam.bounds().contains(start) && beam.bounds().contains(end), "shaft includes both sockets");
        }
        Box diagonal = DisplayArmGeometry.beam(new Vec(0, 0, 0), new Vec(10, 10, 0),
                .5, DisplayArmGeometry.ARM);
        need(diagonal.bounds().contains(new Vec(0, 10, 0)), "negative ray crosses broad hull");
        need(Double.isNaN(diagonal.ray(new Vec(0, 10, -10), new Vec(0, 10, 10))),
                "oriented ray misses empty space inside broad hull");
        DisplayArmGeometry fallback = DisplayArmGeometry.create(-100, 100, null);
        need(fallback.mountFace == 1 && fallback.screenFacing == 2
                && fallback.settings.equals(DisplayArmSettings.DEFAULT), "safe missing-data defaults");
        boolean immutable = false;
        try { fallback.boxes.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        need(immutable, "component collection cannot be mutated by adapters");
    }

    public static void main(String[] args) {
        settings(); cardinalOffsets(); allMounts(); extremeAndDegenerate();
        System.out.println("Display arm geometry PASS " + checks
                + " settings/mount-invariance/cardinal-offsets/pose-inverse/variants/rays/bounds");
    }
}
