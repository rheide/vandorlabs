package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RedstoneData;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * Marker tile entity that makes sliding doors render through
 * {@link com.vandorlabs.client.TESlidingDoor}. All motion state lives in the
 * renderer and the client tick driver; this tile intentionally does not tick.
 */
public class TileEntitySlidingDoor extends TileEntity implements RedstoneChannelMember {
    private int channel;
    private boolean channelSignal;

    @Override public boolean shouldRefresh(net.minecraft.world.World world,
            BlockPos pos, IBlockState before, IBlockState after) {
        return before.getBlock() != after.getBlock();
    }

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return channel; }
    public boolean isChannelSignalPowered() { return channelSignal; }

    @Override public void setRedstoneChannel(int value) {
        int next = Math.max(0, value);
        if (next == channel) return;
        int old = channel;
        channel = next;
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        sync();
    }

    private boolean isLowerDoor() {
        if (world == null || pos == null) return false;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockVandorDoor
                && state.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;
    }

    @Override public boolean hasLocalRedstoneSignal() {
        return isLowerDoor() && (world.isBlockPowered(pos) || world.isBlockPowered(pos.up()));
    }

    @Override public void setChannelSignal(boolean powered) {
        if (channelSignal == powered) return;
        channelSignal = powered;
        if (world != null && !world.isRemote && isLowerDoor()) {
            IBlockState state = world.getBlockState(pos);
            ((BlockVandorDoor) state.getBlock()).updateRedstoneState(world, pos, state);
        }
    }

    public boolean usable(EntityPlayer player) {
        return world != null && world.getTileEntity(pos) == this && player.getDistanceSq(pos) <= 64
                && player.canPlayerEdit(pos, net.minecraft.util.EnumFacing.UP, player.getHeldItemMainhand())
                && world.isBlockModifiable(player, pos);
    }

    @Override public void onLoad() { super.onLoad(); if (isLowerDoor()) RedstoneChannels.register(this); }
    @Override public void invalidate() { RedstoneChannels.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { RedstoneChannels.unregister(this); super.onChunkUnload(); }

    public void localInputChanged() { RedstoneChannels.inputChanged(this); }

    private void sync() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        new RedstoneData.Member(channel,channelSignal).write(new NbtPrimitiveData(tag));
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        int oldChannel = channel;
        super.readFromNBT(tag);
        RedstoneData.Member data=RedstoneData.Member.read(new NbtPrimitiveData(tag));
        channel=data.channel;
        channelSignal=data.signal;
        if (world != null && !world.isRemote && oldChannel != channel)
            RedstoneChannels.channelChanged(this, oldChannel);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        BlockPos pos = getPos();
        return new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(2, 1, 2));
    }
}
