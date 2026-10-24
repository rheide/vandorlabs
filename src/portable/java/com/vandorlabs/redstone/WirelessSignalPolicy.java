package com.vandorlabs.redstone;

/** Directional wireless endpoint levels; world sampling and notifications belong to the host. */
public final class WirelessSignalPolicy {
    private WirelessSignalPolicy() { }
    public static int clampLevel(int value) { return Math.max(0, Math.min(15, value)); }
    public static int sourceLevel(boolean transmitter, boolean analog, int sampledLevel) {
        return transmitter ? analog ? sampledLevel : sampledLevel > 0 ? 15 : 0 : 0;
    }
    public static int outputLevel(boolean transmitter, boolean analog, int receivedLevel, int onLevel, int offLevel) {
        return transmitter ? 0 : receivedLevel > 0 ? analog ? receivedLevel : onLevel : offLevel;
    }
}
