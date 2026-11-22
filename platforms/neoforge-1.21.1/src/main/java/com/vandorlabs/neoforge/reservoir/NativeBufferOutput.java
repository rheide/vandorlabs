package com.vandorlabs.neoforge.reservoir;

import com.vandorlabs.neoforge.redstone.NativeChannelMember;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Independent analog output so the Battery's transfer-enable input never repeats a channel. */
public final class NativeBufferOutput implements NativeChannelMember {
    private final NativeBufferEntity owner;
    private ChannelList channels = ChannelList.EMPTY;
    private int range = TransmissionSettings.DEFAULT_RANGE;
    private int published;
    private boolean local, configured;

    NativeBufferOutput(NativeBufferEntity owner) {
        this.owner = owner;
        local = owner instanceof NativeHexTankEntity;
    }

    public static int level(int amount, int capacity) {
        return amount <= 0 || capacity <= 0 ? 0 : Math.min(15, 1 + (int)(14L * amount / capacity));
    }

    @Override public BlockEntity channelBlockEntity() { return owner; }
    @Override public ChannelList getRedstoneChannels() { return ChannelList.EMPTY; }
    @Override public ChannelList transmittedChannels() { return channels; }
    @Override public int transmissionRange() { return range; }
    @Override public void setChannelLevel(int value) {}
    @Override public int localSignalLevel(int channel) { return level(owner.amount(), owner.capacity()); }
    @Override public boolean isChannelMemberActive() { return owner.installed() && !channels.isEmpty(); }
    public boolean localRedstone() { return local; }
    public int localPower() { return local && !owner.inert() ? localSignalLevel(0) : 0; }

    void configure(ChannelList channels, int range, boolean local) {
        this.channels = channels;
        this.range = range;
        boolean notify = local != this.local;
        this.local = local;
        configured = true;
        if (channels.isEmpty()) NativeRedstoneChannels.unregister(this);
        else NativeRedstoneChannels.channelsChanged(this);
        if (notify) notifyNeighbors();
    }

    void load(CompoundTag source) {
        var all = source.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS);
        configured = all.contains("main", Tag.TAG_COMPOUND);
        var main = all.getCompound("main");
        channels = TransmissionSettings.readChannels(main.contains("Channels", Tag.TAG_INT_ARRAY),
            main.getIntArray("Channels"), ChannelList.EMPTY);
        range = TransmissionSettings.readRange(main.contains("Range", Tag.TAG_INT), main.getInt("Range"), true);
        local = TransmissionSettings.readLocal(main.contains("Local", Tag.TAG_BYTE), main.getBoolean("Local"),
            owner instanceof NativeHexTankEntity);
    }

    void write(CompoundTag target) {
        if (!configured) return;
        var forge = target.getCompound("ForgeData");
        var all = forge.getCompound(TransmissionSettings.SETTINGS);
        var main = all.getCompound("main");
        main.putIntArray("Channels", channels.toArray());
        main.putInt("Range", range);
        main.putBoolean("Local", local);
        all.put("main", main);
        forge.put(TransmissionSettings.SETTINGS, all);
        target.put("ForgeData", forge);
    }

    void loaded() {
        if (!channels.isEmpty()) NativeRedstoneChannels.queueRegistration(this);
    }

    void unloaded() {
        if (!channels.isEmpty()) NativeRedstoneChannels.unregister(this);
    }

    void publish() {
        int next = localSignalLevel(0);
        if (next == published) return;
        published = next;
        if (!channels.isEmpty()) NativeRedstoneChannels.outputChanged(this);
        if (local) notifyNeighbors();
    }

    private void notifyNeighbors() {
        if (owner.getLevel() instanceof ServerLevel server)
            server.updateNeighborsAt(owner.getBlockPos(), owner.getBlockState().getBlock());
    }
}
