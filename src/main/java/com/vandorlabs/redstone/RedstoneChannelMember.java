package com.vandorlabs.redstone;

import net.minecraft.tileentity.TileEntity;

/** A loaded tile that participates in its dimension's virtual redstone bus. */
public interface RedstoneChannelMember {
    TileEntity channelTile();
    int getRedstoneChannel();
    void setRedstoneChannel(int channel);
    default ChannelList getRedstoneChannels(){return ChannelList.of(Math.max(0,getRedstoneChannel()));}
    default void setRedstoneChannels(ChannelList channels) {
        if(channels.size()>1)throw new IllegalArgumentException("Member only supports one channel");
        setRedstoneChannel(channels.first());
    }
    boolean hasLocalRedstoneSignal();
    default boolean hasLocalRedstoneSignal(int channel){return hasLocalRedstoneSignal();}
    void setChannelSignal(boolean powered);
}
