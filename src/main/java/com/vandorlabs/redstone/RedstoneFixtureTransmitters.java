package com.vandorlabs.redstone;

/** Built-in long-range settings for isolated gameplay fixtures. */
public final class RedstoneFixtureTransmitters {
    private RedstoneFixtureTransmitters(){}
    public static void equip(RedstoneChannelMember source){equip(source,source.getRedstoneChannels());}
    public static void equip(RedstoneChannelMember source,ChannelList channels){TransmitterSlots.configure(source,channels,256,TransmitterSlots.localRedstone(source.channelTile()));}
}
