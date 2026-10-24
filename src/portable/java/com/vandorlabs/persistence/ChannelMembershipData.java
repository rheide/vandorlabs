package com.vandorlabs.persistence;

import com.vandorlabs.redstone.ChannelList;

/** Shared saved-list migration: a missing/mistyped/malformed list retains the legacy fallback. */
public final class ChannelMembershipData {
    public static final String CHANNELS = "RedstoneChannels";
    private ChannelMembershipData() { }
    /** Hosts pass null when there is no integer-array tag; an explicitly empty array clears membership. */
    public static ChannelList read(int[] saved, ChannelList fallback) {
        if (saved == null) return fallback;
        try { return ChannelList.of(saved); }
        catch (IllegalArgumentException malformed) { return fallback; }
    }
}
