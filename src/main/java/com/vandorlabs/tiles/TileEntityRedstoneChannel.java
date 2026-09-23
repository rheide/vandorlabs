package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockIndustrialLever;
import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannelLatch;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RedstoneData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Channel, local latch, and flat-mount orientation for switches and levers. */
public class TileEntityRedstoneChannel extends TileEntity implements RedstoneChannelLatch {
    private int channel;
    private boolean localOn;
    private boolean initialized;
    private int mountRotation;

    public int getMountRotation() { return mountRotation; }
    public void setMountRotation(int value) {
        int next=Math.floorMod(value,4);
        if (next==mountRotation) return;
        mountRotation=next;
        markDirty();
        sync();
    }

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
    @Override public boolean latchOn() { return localOn; }
    @Override public boolean isChannelLatch() {
        if (world==null) return false;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        return block instanceof BlockIndustrialLever
                || block instanceof BlockVandorSwitch
                && !((BlockVandorSwitch)block).isMomentary();
    }

    @Override public void applyLinkedLatch(boolean on) {
        if (world==null || world.isRemote || !isChannelLatch()) return;
        IBlockState state=world.getBlockState(pos);
        if (localOn!=on || !initialized) {
            localOn=on;
            initialized=true;
            markDirty();
            sync();
        }
        if (state.getBlock() instanceof BlockIndustrialLever)
            ((BlockIndustrialLever)state.getBlock()).applyLinkedState(world,pos,state,on);
        else if (state.getBlock() instanceof BlockVandorSwitch)
            ((BlockVandorSwitch)state.getBlock()).applyLinkedState(world,pos,state,on);
    }

    public void setLocalOn(boolean value) {
        if (localOn == value && initialized) return;
        localOn = value;
        initialized = true;
        markDirty();
        if (isChannelLatch() && channel>0) RedstoneChannels.latchChanged(this,value);
        else RedstoneChannels.inputChanged(this);
        sync();
    }

    @Override public boolean hasLocalRedstoneSignal() { return localOn; }

    /** Receivers consume aggregate power; linked latches mirror through applyLinkedLatch. */
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
        new RedstoneData.Source(channel,localOn,initialized,mountRotation).write(new NbtPrimitiveData(tag));
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        int oldChannel = channel;
        int oldRotation = mountRotation;
        super.readFromNBT(tag);
        RedstoneData.Source data=RedstoneData.Source.read(new NbtPrimitiveData(tag));
        channel=data.channel;
        localOn=data.localOn;
        initialized=data.initialized;
        mountRotation=data.mountRotation;
        if (world!=null && world.isRemote && oldRotation!=mountRotation)
            world.markBlockRangeForRenderUpdate(pos,pos);
        if (world != null && !world.isRemote && oldChannel != channel)
            RedstoneChannels.channelChanged(this, oldChannel);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
