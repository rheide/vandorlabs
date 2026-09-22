package com.vandorlabs.redstone;

/** A loaded, persistent control whose handle mirrors linked controls. */
public interface RedstoneChannelLatch extends RedstoneChannelMember {
    boolean isChannelLatch();
    boolean latchOn();
    void applyLinkedLatch(boolean on);
}
