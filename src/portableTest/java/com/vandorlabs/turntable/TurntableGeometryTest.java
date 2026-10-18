package com.vandorlabs.turntable;

import java.util.ArrayList;
import java.util.List;

public final class TurntableGeometryTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    private static void close(double a, double b, String message) {
        check(Math.abs(a - b) < 1e-8, message);
    }

    public static void main(String[] args) {
        // Angle wrapping keeps the render transform bounded.
        close(TurntableGeometry.normalize(0), 0, "zero wraps to zero");
        close(TurntableGeometry.normalize(360), 0, "full turn wraps to zero");
        close(TurntableGeometry.normalize(-90), 270, "negative wraps forward");
        close(TurntableGeometry.normalize(450), 90, "past one turn wraps");
        close(TurntableGeometry.normalize(Double.NaN), 0, "non-finite clears");

        // Clockwise is negative, counter-clockwise is positive, 1 degree/tick at 20 tps.
        double ccw = TurntableGeometry.angle(0, 20, 0, 0, 20, TurntableGeometry.COUNTER_CLOCKWISE);
        double cw = TurntableGeometry.angle(0, 20, 0, 0, 20, TurntableGeometry.CLOCKWISE);
        close(ccw, 20, "one second counter-clockwise");
        close(cw, 340, "one second clockwise");
        check(TurntableGeometry.angle(350, 40, 20, 0, 20, TurntableGeometry.COUNTER_CLOCKWISE) == 10,
                "start angle accumulates and wraps");
        close(TurntableGeometry.angle(0, 5, 0, 0.5f, 20, TurntableGeometry.COUNTER_CLOCKWISE), 5.5,
                "partial frame fraction contributes");

        // Speed is clamped and non-finite falls back.
        close(TurntableGeometry.clampSpeed(0), TurntableGeometry.MIN_SPEED, "speed floor");
        close(TurntableGeometry.clampSpeed(9999), TurntableGeometry.MAX_SPEED, "speed ceiling");
        close(TurntableGeometry.clampSpeed(Double.NaN), 90, "speed fallback");

        check(TurntableGeometry.clampHeight(0) == 1, "height floor");
        check(TurntableGeometry.clampHeight(99) == 16, "height ceiling");

        // Right-handed rotation about each axis at a quarter turn.
        double[] y = TurntableGeometry.rotate(1, 0, 0, TurntableGeometry.AXIS_Y, 90);
        close(y[0], 0, "Y quarter turn x"); close(y[2], -1, "Y quarter turn z");
        double[] x = TurntableGeometry.rotate(0, 1, 0, TurntableGeometry.AXIS_X, 90);
        close(x[1], 0, "X quarter turn y"); close(x[2], 1, "X quarter turn z");
        double[] z = TurntableGeometry.rotate(1, 0, 0, TurntableGeometry.AXIS_Z, 90);
        close(z[0], 0, "Z quarter turn x"); close(z[1], 1, "Z quarter turn y");

        // A full turn is identity and a composed quarter turns cancel.
        for (int axis = 0; axis <= 2; axis++) {
            double[] identity = TurntableGeometry.rotate(.25, .5, .75, axis, 360);
            close(identity[0], .25, "identity x"); close(identity[1], .5, "identity y"); close(identity[2], .75, "identity z");
            double[] back = TurntableGeometry.rotate(.25, .5, .75, axis, 90);
            back = TurntableGeometry.rotate(back[0], back[1], back[2], axis, 270);
            close(back[0], .25, "composed x"); close(back[1], .5, "composed y"); close(back[2], .75, "composed z");
        }

        // Rotation preserves distance from the pivot, which the payload render relies on.
        double[] arbitrary = TurntableGeometry.rotate(1.5, -2.25, 3.75, TurntableGeometry.AXIS_Y, 37.5);
        close(Math.sqrt(arbitrary[0] * arbitrary[0] + arbitrary[2] * arbitrary[2]),
                Math.sqrt(1.5 * 1.5 + 3.75 * 3.75), "radius preserved on the turning plane");

        // Square validation and inline axis pairing.
        check(TurntableGeometry.isSquare(3, 3), "equal extents are a square");
        check(!TurntableGeometry.isSquare(3, 4), "unequal extents are rejected");
        check(!TurntableGeometry.isSquare(0, 0), "empty plane is rejected");
        int[] plane = TurntableGeometry.planeAxes(TurntableGeometry.AXIS_Y);
        check(plane[0] == TurntableGeometry.AXIS_X && plane[1] == TurntableGeometry.AXIS_Z, "Y plane spans X/Z");
        plane = TurntableGeometry.planeAxes(TurntableGeometry.AXIS_X);
        check(plane[0] == TurntableGeometry.AXIS_Y && plane[1] == TurntableGeometry.AXIS_Z, "X plane spans Y/Z");
        plane = TurntableGeometry.planeAxes(TurntableGeometry.AXIS_Z);
        check(plane[0] == TurntableGeometry.AXIS_X && plane[1] == TurntableGeometry.AXIS_Y, "Z plane spans X/Y");

        // Extents accept a full square and reject a ragged plane.
        List<int[]> square = new ArrayList<>();
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) square.add(new int[] { a, b });
        int[] extents = TurntableGeometry.extents(square);
        check(TurntableGeometry.isSquare(extents[0], extents[1]), "full plane extents are square");
        square.removeIf(cell -> cell[0] == 3);
        extents = TurntableGeometry.extents(square);
        check(!TurntableGeometry.isSquare(extents[0], extents[1]), "ragged plane extents differ");
        check(TurntableGeometry.withinSquare(0, 3, 4) && !TurntableGeometry.withinSquare(4, 0, 4), "square membership");

        // A valid platform must also be filled, not just a square outline.
        check(TurntableGeometry.isFilledSquare(16, 4), "filled square counts every cell");
        check(!TurntableGeometry.isFilledSquare(12, 4), "hollow square is rejected");
        check(!TurntableGeometry.isFilledSquare(0, 4), "empty platform is rejected");

        System.out.println("TurntableGeometryTest passed: " + assertions + " assertions");
    }
}
