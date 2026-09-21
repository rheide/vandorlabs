package com.vandorlabs.redstone;

import net.minecraft.tileentity.TileEntity;

/** A loaded tile that participates in its dimension's virtual redstone bus. */
public interface RedstoneChannelMember {
    TileEntity channelTile();
    int getRedstoneChannel();
    void setRedstoneChannel(int channel);
    boolean hasLocalRedstoneSignal();
    void setChannelSignal(boolean powered);
}
