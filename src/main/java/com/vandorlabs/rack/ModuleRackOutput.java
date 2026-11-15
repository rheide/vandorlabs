package com.vandorlabs.rack;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** A proportional source using the ordinary built-in transmission settings, without a latch inventory. */
public final class ModuleRackOutput implements RedstoneChannelLatch {
    private final TileEntityModuleRack rack;
    private ChannelList channels = ChannelList.EMPTY;
    private boolean configured;
    private int publishedLevel;
    public ModuleRackOutput(TileEntityModuleRack rack) { this.rack = rack; }
    public int level() { return rack.emitsRedstone() ? ModuleRackPolicy.signalLevel(rack.getOccupiedMask()) : 0; }
    public TileEntity channelTile() { return rack; }
    public int getRedstoneChannel() { return channels.first(); }
    public ChannelList getRedstoneChannels() { return channels; }
    public void setRedstoneChannel(int value) { setRedstoneChannels(ChannelList.of(value)); }
    public void setRedstoneChannels(ChannelList value) {
        if (value == null) throw new IllegalArgumentException("Missing output channels");
        configured = true; channels = value; rack.configurationChanged();
    }
    public boolean hasLocalRedstoneSignal() { return level() > 0; }
    public int localSignalLevel(int channel) { return level(); }
    public void setChannelSignal(boolean ignored) { }
    public boolean isChannelLatch() { return false; }
    public boolean latchOn() { return level() > 0; }
    public void applyLinkedLatch(boolean ignored) { }
    public void loaded(NBTTagCompound data) {
        configured = data.getCompoundTag("ForgeData").getCompoundTag(TransmissionSettings.SETTINGS).hasKey("main", 10);
        channels = TransmitterSlots.channels(this);
    }
    public NBTTagCompound publicSettings() {
        if (!configured) return null;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray(TransmissionSettings.CHANNELS, TransmitterSlots.channels(this).toArray());
        tag.setInteger(TransmissionSettings.RANGE, transmissionRange());
        tag.setBoolean(TransmissionSettings.LOCAL, TransmitterSlots.localRedstone(rack));
        return tag;
    }
    public void readPublic(NBTTagCompound data, boolean replacement) {
        if (!data.hasKey("RackTransmission", 10)) {
            if (replacement && configured) {
                rack.getTileData().getCompoundTag(TransmissionSettings.SETTINGS).removeTag("main");
                configured = false; channels = ChannelList.EMPTY;
            }
            return;
        }
        NBTTagCompound saved = data.getCompoundTag("RackTransmission");
        ChannelList bank = TransmissionSettings.readChannels(saved.hasKey("Channels", 11), saved.getIntArray("Channels"), ChannelList.EMPTY);
        int range = TransmissionSettings.readRange(saved.hasKey("Range", 3), saved.getInteger("Range"), true);
        NBTTagCompound next = new NBTTagCompound();
        next.setIntArray("Channels", bank.toArray()); next.setInteger("Range", range); next.setBoolean("Local", saved.getBoolean("Local"));
        NBTTagCompound all = rack.getTileData().getCompoundTag(TransmissionSettings.SETTINGS);
        all.setTag("main", next); rack.getTileData().setTag(TransmissionSettings.SETTINGS, all);
        channels = bank; configured = true;
    }
    public void publish() {
        net.minecraft.world.World world = rack.getWorld();
        if (world == null || rack.isInvalid() || !(world instanceof com.vandorlabs.vehicle.VehicleWorld) && world.isRemote) return;
        if (!world.isBlockLoaded(rack.getPos()) || world.getTileEntity(rack.getPos()) != rack) return;
        int level = level(); if (level == publishedLevel) return;
        publishedLevel = level;
        if (world instanceof com.vandorlabs.vehicle.VehicleWorld) {
            com.vandorlabs.vehicle.VehicleWorld view = (com.vandorlabs.vehicle.VehicleWorld)world;
            if (view.craft != null && !view.parent.isRemote) view.craft.signals.transmitterChanged(this);
        } else if (!world.isRemote && world.getTileEntity(rack.getPos()) == rack) {
            RedstoneChannels.inputChanged(this);
            if (TransmitterSlots.localRedstone(rack)) TransmitterSlots.notifyLocalOutput(rack);
        }
    }
}
