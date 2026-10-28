package com.vandorlabs.redstone;

import net.minecraft.tileentity.TileEntity;

/** A loaded tile that participates in its dimension's virtual redstone bus. */
public interface RedstoneChannelMember {
    TileEntity channelTile();
    /** Logical adapters must override this with the identity of their live source. */
    default boolean isChannelMemberActive(){
        TileEntity tile=channelTile();
        return tile!=null && !tile.isInvalid() && tile.getWorld()!=null
                && tile.getWorld().isBlockLoaded(tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos())==tile;
    }
    /** Stable slot identity for logical controls sharing a tile. */
    default String transmitterSlot(){return "main";}
    default ChannelList transmittedChannels(){return TransmitterSlots.channels(this);}
    /** Negative means no transmitter, zero means unbounded within this dimension. */
    default int transmissionRange(){return TransmitterSlots.range(this);}
    default net.minecraft.util.math.BlockPos channelPosition(){return RedstoneChannels.physicalPosition(channelTile().getWorld(),channelTile().getPos());}
    int getRedstoneChannel();
    void setRedstoneChannel(int channel);
    default ChannelList getRedstoneChannels(){return ChannelList.of(Math.max(0,getRedstoneChannel()));}
    default void setRedstoneChannels(ChannelList channels) {
        if(channels.size()>1)throw new IllegalArgumentException("Member only supports one channel");
        setRedstoneChannel(channels.first());
    }
    boolean hasLocalRedstoneSignal();
    default boolean hasLocalRedstoneSignal(int channel){return hasLocalRedstoneSignal();}
    default int localSignalLevel(int channel){return hasLocalRedstoneSignal(channel)?15:0;}
    default void setChannelLevel(int level){setChannelSignal(level>0);}
    void setChannelSignal(boolean powered);
}
