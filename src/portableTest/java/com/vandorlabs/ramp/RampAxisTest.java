package com.vandorlabs.ramp;

final class RampAxisTest {
    static void run() {
        for (RampAxis mount : RampAxis.values()) {
            RampAxis[] choices = RampAxis.choices(mount);
            if (choices.length != 2 || choices[0] == choices[1]) throw new AssertionError("Two distinct axis choices");
            for (RampAxis selected : RampAxis.values()) {
                boolean expected = mount == RampAxis.Y ? selected != RampAxis.Y
                        : selected == mount || selected == RampAxis.Y;
                if (RampAxis.allowed(mount, selected) != expected) throw new AssertionError("Mount axis restrictions");
                if (RampAxis.allowed(mount.rotateQuarterTurn(), selected.rotateQuarterTurn()) != expected)
                    throw new AssertionError("Rotated mount axis restrictions");
            }
        }
        System.out.println("Ramp axis choices PASS: side mounts X/Y or Z/Y, top/bottom mounts X/Z, quarter-turn parity");
    }
    private RampAxisTest() { }
}
