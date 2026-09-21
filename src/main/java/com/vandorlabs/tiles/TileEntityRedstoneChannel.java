package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockIndustrialLever;
import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Channel and local latch state for switches whose visible state stays in metadata. */
public class TileEntityRedstoneChannel extends TileEntity implements RedstoneChannelMember {
    private int channel;
    private boolean localOn;
    private boolean initialized;

    @Override public boolean shouldRefresh(net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, IBlockState before, IBlockState after) {
        return before.getBlock() != after.getBlock();
    }

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return channel; }

    @Override public void setRedstoneChannel(int value) {
        int next = Math.max(0, value);
        if (next == channel) return;
        int old = channel;
        channel = next;
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        sync();
    }

    public boolean isLocalOn() { return localOn; }

    public void setLocalOn(boolean value) {
        if (localOn == value && initialized) return;
        localOn = value;
        initialized = true;
        markDirty();
        RedstoneChannels.inputChanged(this);
        sync();
    }

    @Override public boolean hasLocalRedstoneSignal() { return localOn; }

    /** Switches transmit to a channel; their handles do not follow remote inputs. */
    @Override public void setChannelSignal(boolean powered) { }

    public boolean usable(EntityPlayer player) {
        return world != null && world.getTileEntity(pos) == this && player.getDistanceSq(pos) <= 64
                && player.canPlayerEdit(pos, net.minecraft.util.EnumFacing.UP, player.getHeldItemMainhand())
                && world.isBlockModifiable(player, pos);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (!world.isRemote && !initialized) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockVandorSwitch) localOn = state.getValue(BlockVandorSwitch.ON);
            if (state.getBlock() instanceof BlockIndustrialLever) localOn = state.getValue(BlockIndustrialLever.POWERED);
            initialized = true;
            markDirty();
        }
        RedstoneChannels.register(this);
    }

    @Override public void invalidate() { RedstoneChannels.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { RedstoneChannels.unregister(this); super.onChunkUnload(); }

    private void sync() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("RedstoneChannel", channel);
        tag.setBoolean("LocalOn", localOn);
        tag.setBoolean("ChannelInitialized", initialized);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        int oldChannel = channel;
        super.readFromNBT(tag);
        channel = Math.max(0, tag.getInteger("RedstoneChannel"));
        localOn = tag.getBoolean("LocalOn");
        initialized = tag.getBoolean("ChannelInitialized");
        if (world != null && !world.isRemote && oldChannel != channel)
            RedstoneChannels.channelChanged(this, oldChannel);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
