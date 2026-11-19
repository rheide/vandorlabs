package com.vandorlabs.turntable;

import com.vandorlabs.redstone.ChannelList;

/** Controller choices shared by the two platform adapters. Motion and ownership are separate. */
public final class TurntableSettings {
    public static final int MIN_HEIGHT = 1;
    public static final int MAX_HEIGHT = 16;
    public static final int SCAN_DISABLED = 0;
    public static final int SIGNAL_ON = 1;
    public static final int SIGNAL_OFF = 2;

    public final int height;
    public final int scanMode;
    public final int rotationMode;
    public final double rpm;
    public final double snapTolerance;
    public final boolean clockwise;
    public final ChannelList scanChannels;
    public final ChannelList rotationChannels;
    public final ChannelList directionChannels;
    public final ChannelList restoreChannels;

    public TurntableSettings(int height, int scanMode, int rotationMode, double rpm, double snapTolerance,
                             boolean clockwise, ChannelList scanChannels, ChannelList rotationChannels,
                             ChannelList directionChannels, ChannelList restoreChannels) {
        TurntableGeometry.checkSnapTolerance(snapTolerance);
        TurntableGeometry.degreesPerTick(rpm);
        if (height < MIN_HEIGHT || height > MAX_HEIGHT
                || scanMode < SCAN_DISABLED || scanMode > SIGNAL_OFF
                || rotationMode < SIGNAL_ON || rotationMode > SIGNAL_OFF
                || scanChannels == null || rotationChannels == null || directionChannels == null || restoreChannels == null) {
            throw new IllegalArgumentException("Invalid turntable settings");
        }
        this.height = height;
        this.scanMode = scanMode;
        this.rotationMode = rotationMode;
        this.rpm = rpm;
        this.snapTolerance = snapTolerance;
        this.clockwise = clockwise;
        this.scanChannels = scanChannels;
        this.rotationChannels = rotationChannels;
        this.directionChannels = directionChannels;
        this.restoreChannels = restoreChannels;
    }

    public static boolean active(int mode, boolean powered) {
        return mode != SCAN_DISABLED && (mode == SIGNAL_ON) == powered;
    }
}
