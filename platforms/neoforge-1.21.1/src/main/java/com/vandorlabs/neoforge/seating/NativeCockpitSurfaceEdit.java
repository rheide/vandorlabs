package com.vandorlabs.neoforge.seating;

import com.vandorlabs.redstone.ChannelList;
import java.util.Objects;

/** A cockpit face's independent artwork, playback and receive bank. */
public record NativeCockpitSurfaceEdit(int artwork, int playback, int speed, int activation, ChannelList channels) {
    public static final int ORIGINAL = -1, REDSTONE = -2, BLACK = -3;

    public NativeCockpitSurfaceEdit {
        Objects.requireNonNull(channels);
        if (artwork < BLACK || playback < 0 || playback > 2 || speed < 0 || speed > 2 || activation < 0 || activation > 2)
            throw new IllegalArgumentException("Cockpit surface options");
    }
}
