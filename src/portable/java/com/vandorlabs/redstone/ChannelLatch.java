package com.vandorlabs.redstone;

import java.util.Map;

/** Persistent linked controls share state; timed pulses remain independent members. */
public interface ChannelLatch extends ChannelMember {
    boolean isChannelLatch();
    int latchedLevel(int channel);
    void applyLinkedLevels(Map<Integer, Integer> levels);
}
