package com.vandorlabs.rack;

import com.vandorlabs.redstone.ChannelList;
import java.util.Objects;

/** Portable module configuration; route progress and automation authority are not copied settings. */
public final class AutopilotModuleSettings {
    public static final String KEY = "RackAutopilot";
    public final ChannelList channels;
    public final int activation, markerMetadata;
    public final String marker;
    public AutopilotModuleSettings(ChannelList channels, int activation, String marker, int markerMetadata) {
        if (channels == null || activation < 0 || activation > 2 || marker == null || marker.length() > 128
                || !marker.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || markerMetadata < 0 || markerMetadata > 15)
            throw new IllegalArgumentException("Invalid Autopilot module settings");
        this.channels = channels; this.activation = activation; this.marker = marker; this.markerMetadata = markerMetadata;
    }
    public static boolean powered(int activation,boolean high) { return activation==2?!high:activation==1&&high; }
    public static AutopilotModuleSettings defaults() {
        return new AutopilotModuleSettings(ChannelList.EMPTY, 1, "minecraft:concrete", 0);
    }
    @Override public boolean equals(Object value) {
        if (!(value instanceof AutopilotModuleSettings)) return false;
        AutopilotModuleSettings other = (AutopilotModuleSettings)value;
        return activation == other.activation && markerMetadata == other.markerMetadata
                && marker.equals(other.marker) && channels.equals(other.channels);
    }
    @Override public int hashCode() { return Objects.hash(channels, activation, marker, markerMetadata); }
}
