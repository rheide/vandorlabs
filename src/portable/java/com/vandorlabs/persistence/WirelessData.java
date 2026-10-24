package com.vandorlabs.persistence;

import com.vandorlabs.redstone.WirelessSignalPolicy;

/** Stable wireless settings and sampled levels, including pre-configuration legacy defaults. */
public final class WirelessData {
    public final boolean analog;
    public final int onLevel, offLevel, inputLevel, receivedLevel;
    public WirelessData(boolean analog, int on, int off, int input, int received) {
        this.analog = analog;
        onLevel = WirelessSignalPolicy.clampLevel(on);
        offLevel = WirelessSignalPolicy.clampLevel(off);
        inputLevel = WirelessSignalPolicy.clampLevel(input);
        receivedLevel = WirelessSignalPolicy.clampLevel(received);
    }
    public static WirelessData read(PrimitiveData data) {
        return new WirelessData(!data.contains(SaveSchema.Wireless.ANALOG) || data.getBoolean(SaveSchema.Wireless.ANALOG),
                data.containsInt(SaveSchema.Wireless.ON) ? data.getInt(SaveSchema.Wireless.ON) : 15,
                data.getInt(SaveSchema.Wireless.OFF), data.getInt(SaveSchema.Wireless.INPUT), data.getInt(SaveSchema.Wireless.RECEIVED));
    }
    public void writeConfiguration(PrimitiveData data) {
        data.putBoolean(SaveSchema.Wireless.ANALOG, analog);
        data.putInt(SaveSchema.Wireless.ON, onLevel); data.putInt(SaveSchema.Wireless.OFF, offLevel);
    }
    public void write(PrimitiveData data) {
        writeConfiguration(data);
        data.putInt(SaveSchema.Wireless.INPUT, inputLevel); data.putInt(SaveSchema.Wireless.RECEIVED, receivedLevel);
    }
}
