package com.vandorlabs.persistence;

import com.vandorlabs.redstone.WirelessSignalPolicy;

public final class WirelessDataTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        MemoryPrimitiveData empty = new MemoryPrimitiveData();
        WirelessData defaults = WirelessData.read(empty);
        require(defaults.analog && defaults.onLevel == 15 && defaults.offLevel == 0 && defaults.inputLevel == 0 && defaults.receivedLevel == 0, "legacy defaults");
        empty.putBoolean(SaveSchema.Wireless.ANALOG, false); empty.putBoolean(SaveSchema.Wireless.ON, true);
        require(!WirelessData.read(empty).analog && WirelessData.read(empty).onLevel == 15, "mistyped on level retains integer-only legacy default");
        empty.putInt(SaveSchema.Wireless.ON, 0);
        require(WirelessData.read(empty).onLevel == 0, "explicit zero on level preserved");
        WirelessData clamped = new WirelessData(true, -1, 30, Integer.MIN_VALUE, Integer.MAX_VALUE);
        require(clamped.onLevel == 0 && clamped.offLevel == 15 && clamped.inputLevel == 0 && clamped.receivedLevel == 15, "saved level bounds");
        for (int on = 0; on <= 15; on++) for (int off = 0; off <= 15; off++) for (int value = 0; value <= 15; value++) {
            require(WirelessSignalPolicy.sourceLevel(true, true, value) == value, "analog source exact level");
            require(WirelessSignalPolicy.sourceLevel(true, false, value) == (value == 0 ? 0 : 15), "digital source threshold");
            require(WirelessSignalPolicy.sourceLevel(false, true, value) == 0, "receiver never publishes source");
            require(WirelessSignalPolicy.outputLevel(true, true, value, on, off) == 0, "transmitter never produces wired output");
            require(WirelessSignalPolicy.outputLevel(false, true, value, on, off) == (value == 0 ? off : value), "analog receiver follows positive levels");
            require(WirelessSignalPolicy.outputLevel(false, false, value, on, off) == (value == 0 ? off : on), "digital receiver configured levels");
            for (boolean analog : new boolean[]{false, true}) {
                WirelessData before = new WirelessData(analog, on, off, value, 15 - value);
                MemoryPrimitiveData saved = new MemoryPrimitiveData(); before.write(saved);
                WirelessData restored = WirelessData.read(saved);
                require(restored.analog == analog && restored.onLevel == on && restored.offLevel == off
                        && restored.inputLevel == value && restored.receivedLevel == 15 - value, "exact settings/sample roundtrip");
            }
        }
        MemoryPrimitiveData configuration = new MemoryPrimitiveData();
        defaults.writeConfiguration(configuration);
        require(!configuration.contains(SaveSchema.Wireless.INPUT) && !configuration.contains(SaveSchema.Wireless.RECEIVED), "configuration excludes live sampled state");
        System.out.println("Wireless policy/data PASS: all 4096 level combinations, 8192 exact roundtrips and typed legacy defaults");
    }
}
