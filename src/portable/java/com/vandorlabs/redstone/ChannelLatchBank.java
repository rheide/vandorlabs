package com.vandorlabs.redstone;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared per-channel handle state. The host owns loaded membership, lifetime and
 * notifications; pulses and other independent power sources do not enter this bank.
 */
public final class ChannelLatchBank {
    private final Map<Integer, Integer> levels = new HashMap<>();

    public boolean contains(int channel) { return levels.containsKey(channel); }

    /** Existing live state wins over a newly loaded control's saved level. */
    public void initialize(int channel, int savedLevel) {
        if (!levels.containsKey(channel)) levels.put(channel, savedLevel);
    }

    /** A linked control updates only its own subscriptions, including zero levels. */
    public void set(ChannelList channels, int level) {
        for (int i = 0; i < channels.size(); i++) levels.put(channels.get(i), level);
    }

    public int level(int channel) { return levels.getOrDefault(channel, 0); }

    /** Called by the host when the last active latch for this channel disappears. */
    public void remove(int channel) { levels.remove(channel); }

    /** Includes off channels so partially overlapping controls mirror exact levels. */
    public Map<Integer, Integer> snapshot(ChannelList channels) {
        Map<Integer, Integer> result = new HashMap<>();
        for (int i = 0; i < channels.size(); i++) {
            int channel = channels.get(i);
            result.put(channel, level(channel));
        }
        return result;
    }
}
