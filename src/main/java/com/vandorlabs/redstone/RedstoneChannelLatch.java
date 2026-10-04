package com.vandorlabs.redstone;

/** A loaded, persistent control whose handle mirrors linked controls. */
public interface RedstoneChannelLatch extends RedstoneChannelMember {
    boolean isChannelLatch();
    boolean latchOn();
    void applyLinkedLatch(boolean on);
    default ChannelList latchedChannels(){return latchOn()?getRedstoneChannels():ChannelList.EMPTY;}
    default void applyLinkedChannels(ChannelList active){applyLinkedLatch(!active.isEmpty());}
}
