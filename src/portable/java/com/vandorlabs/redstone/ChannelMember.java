package com.vandorlabs.redstone;

/** Platform identity/liveness stays at the host boundary; signal policy is shared. */
public interface ChannelMember {
    ChannelList getRedstoneChannels();
    boolean isChannelMemberActive();
    int localSignalLevel(int channel);
    void setChannelLevel(int level);
}
