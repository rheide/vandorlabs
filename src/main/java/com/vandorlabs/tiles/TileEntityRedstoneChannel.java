package com.vandorlabs.tiles;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

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
    private ChannelList channels=ChannelList.EMPTY;
    private ChannelList latched=ChannelList.EMPTY;
    private final java.util.Map<Integer,Integer> levels=new java.util.HashMap<>();
    @Override public int latchedLevel(int channel){return levels.getOrDefault(channel,latched.contains(channel)?15:0);}
    @Override public int localSignalLevel(int channel){return isChannelLatch()?latchedLevel(channel):localOn?15:0;}
    @Override public void applyLinkedLevels(java.util.Map<Integer,Integer> value){levels.clear();levels.putAll(value);RedstoneChannelLatch.super.applyLinkedLevels(value);markDirty();}

    @Override public ChannelList getRedstoneChannels(){return channels;}

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
        setRedstoneChannels(ChannelList.of(Math.max(0,value)));
    }
    @Override public void setRedstoneChannels(ChannelList next) {
        if (channels.equals(next)) return;
        ChannelList old = channels;
        latched=localOn?next:latched.intersect(next);
        channels=next;channel=next.first();
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        sync();
    }

    public boolean isLocalOn() { return localOn; }
    @Override public boolean latchOn() { return localOn; }
    @Override public ChannelList latchedChannels(){return latched;}
    @Override public boolean isChannelLatch() {
        if (world==null) return false;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        return block instanceof BlockIndustrialLever
                || block instanceof BlockVandorSwitch
                && !((BlockVandorSwitch)block).isMomentary();
    }

    @Override public void applyLinkedLatch(boolean on) {
        applyLinkedChannels(on?channels:ChannelList.EMPTY);
    }
    @Override public void applyLinkedChannels(ChannelList active) {
        if (world==null || world.isRemote || !isChannelLatch()) return;
        ChannelList next=active.intersect(channels);
        boolean changed=!latched.equals(next);latched=next;
        boolean on=!channels.isEmpty() && latched.containsAll(channels);
        IBlockState state=world.getBlockState(pos);
        if (localOn!=on || !initialized || changed) {
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
        if (localOn == value && initialized && (value || latched.isEmpty())) return;
        levels.clear();
        localOn = value;
        latched=value?channels:ChannelList.EMPTY;
        initialized = true;
        markDirty();
        if (isChannelLatch() && channel>0) RedstoneChannels.latchChanged(this,value);
        else RedstoneChannels.inputChanged(this);
        sync();
    }

    @Override public boolean hasLocalRedstoneSignal() { return localOn; }
    @Override public boolean hasLocalRedstoneSignal(int channel) {
        return isChannelLatch()?latched.contains(channel):localOn;
    }

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
            latched=localOn?channels:ChannelList.EMPTY;
            initialized = true;
            markDirty();
        }
        DeferredTileLoad.schedule(this, () -> RedstoneChannels.register(this));
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
        super.writeToNBT(tag);ChannelData.write(tag,channels);
        new RedstoneData.Source(channel,localOn,initialized,mountRotation).write(new NbtPrimitiveData(tag));
        int[] savedLevels=new int[channels.size()];for(int i=0;i<savedLevels.length;i++)savedLevels[i]=latchedLevel(channels.get(i));tag.setIntArray("LatchedLevels",savedLevels);
        tag.setIntArray("LatchedChannels",latched.toArray());
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        ChannelList previousChannels=channels;
        int oldChannel = channel;
        int oldRotation = mountRotation;
        super.readFromNBT(tag);
        RedstoneData.Source data=RedstoneData.Source.read(new NbtPrimitiveData(tag));
        channel=data.channel;
        channels=ChannelData.read(tag,channel);channel=channels.first();
        localOn=data.localOn;
        latched=ChannelData.read(tag,"LatchedChannels",localOn?channels:ChannelList.EMPTY).intersect(channels);
        levels.clear();int[] savedLevels=tag.getIntArray("LatchedLevels");for(int i=0;i<Math.min(savedLevels.length,channels.size());i++)levels.put(channels.get(i),Math.max(0,Math.min(15,savedLevels[i])));
        initialized=data.initialized;
        mountRotation=data.mountRotation;
        if (world!=null && world.isRemote && oldRotation!=mountRotation)
            world.markBlockRangeForRenderUpdate(pos,pos);
        if (world != null && !world.isRemote && !previousChannels.equals(channels))
            DeferredTileLoad.schedule(this, () -> RedstoneChannels.channelChanged(this, oldChannel));
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
