package com.vandorlabs.ramp;

final class RampAxisTest {
    static void run() {
        for (RampAxis mount : RampAxis.values()) {
            RampAxis[] choices = RampAxis.choices(mount);
            if (choices.length != (mount == RampAxis.Y ? 2 : 3)) throw new AssertionError("Mount axis choice count");
            for (RampAxis selected : RampAxis.values()) {
                boolean expected = mount != RampAxis.Y || selected != RampAxis.Y;
                if (RampAxis.allowed(mount, selected) != expected) throw new AssertionError("Mount axis restrictions");
                if (RampAxis.allowed(mount.rotateQuarterTurn(), selected.rotateQuarterTurn()) != expected)
                    throw new AssertionError("Rotated mount axis restrictions");
            }
        }
        System.out.println("Ramp axis choices PASS: side mounts X/Y/Z, top/bottom mounts X/Z, quarter-turn parity");
    }
    private RampAxisTest() { }
}
