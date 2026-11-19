package com.vandorlabs.neoforge.seating;

import com.vandorlabs.neoforge.redstone.NativeChannelLatch;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Legacy indices 0–2 belong to the touch panel, followed by the two armrest grips. */
final class NativeChairArmrest implements NativeChannelLatch {
    static final int LEFT = 3, RIGHT = 4;
    final NativeChairEntity owner;
    final String id;
    private ChannelList channels = ChannelList.EMPTY;
    private final Map<Integer, Integer> levels = new HashMap<>();
    private int min = 5, max = 15, level;

    NativeChairArmrest(NativeChairEntity owner, String id) {
        this.owner = owner;
        this.id = id;
    }

    int level() { return level; }
    int min() { return min; }
    int max() { return max; }
    @Override public BlockEntity channelBlockEntity() { return owner; }
    @Override public ChannelList getRedstoneChannels() { return channels; }
    @Override public ChannelList transmittedChannels() { return channels; }
    @Override public int transmissionRange() { return TransmissionSettings.DEFAULT_RANGE; }
    @Override public boolean isChannelMemberActive() {
        return NativeChannelLatch.super.isChannelMemberActive() && !owner.inert()
            && ((NativeSeatBlock) owner.getBlockState().getBlock()).complete(owner.getLevel(), owner.getBlockPos());
    }
    @Override public boolean isChannelLatch() { return true; }
    @Override public int latchedLevel(int channel) { return levels.getOrDefault(channel, 0); }
    @Override public int localSignalLevel(int channel) { return latchedLevel(channel); }
    @Override public ChannelList latchedChannels() { return NativeChannelLatch.activeChannels(levels); }
    @Override public boolean latchOn() { return channels.isEmpty() ? level > 0 : latchedChannels().containsAll(channels); }
    @Override public void applyLinkedLatch(boolean on) {
        for (int channel : channels.toArray()) levels.put(channel, on ? 15 : 0);
        owner.setChanged();
    }
    @Override public void applyLinkedLevels(Map<Integer, Integer> next) {
        levels.clear();
        levels.putAll(next);
        owner.setChanged();
    }
    @Override public void setChannelLevel(int value) {
        if (!channels.isEmpty() && level != value) {
            level = value;
            owner.armrestsChanged();
        }
    }

    boolean configure(ChannelList next, int low, int high) {
        if (low < 0 || high > 15 || high - low < 2 || !(owner.getLevel() instanceof ServerLevel server)
            || !NativeRedstoneChannels.open(server) || !isChannelMemberActive()) return false;
        NativeRedstoneChannels.batch(server, () -> {
            channels = next;
            min = low;
            max = high;
            levels.keySet().removeIf(channel -> !next.contains(channel));
            NativeRedstoneChannels.channelsChanged(this);
            owner.armrestsChanged();
        });
        return true;
    }

    private int detent(int step) {
        return step == 0 ? 0 : min + (max - min) * (step - 1) / 2;
    }

    void cycle() {
        int step = 0;
        for (int i = 1; i < 4; i++) {
            if (Math.abs(detent(i) - level) < Math.abs(detent(step) - level)) step = i;
        }
        int next = detent((step + 1) % 4);
        if (next == detent(step)) next = detent((step + 2) % 4);
        if (channels.isEmpty()) {
            level = next;
            owner.armrestsChanged();
        } else {
            NativeRedstoneChannels.latchLevelChanged(this, next);
        }
    }

    boolean read(CompoundTag tag) {
        try {
            if ((tag.contains("RedstoneChannels") && !tag.contains("RedstoneChannels", Tag.TAG_INT_ARRAY))
                || (tag.contains("LevelChannels") && !tag.contains("LevelChannels", Tag.TAG_INT_ARRAY))
                || (tag.contains("Levels") && !tag.contains("Levels", Tag.TAG_INT_ARRAY))) return false;
            int low = tag.contains("Min", Tag.TAG_INT) ? tag.getInt("Min") : 5;
            int high = tag.contains("Max", Tag.TAG_INT) ? tag.getInt("Max") : 15;
            int value = tag.getInt("Level");
            if (low < 0 || high > 15 || high - low < 2 || value < 0 || value > 15) return false;
            ChannelList next = tag.contains("RedstoneChannels")
                ? ChannelList.of(tag.getIntArray("RedstoneChannels")) : ChannelList.of(tag.getInt("Channel"));
            int[] keys = tag.contains("LevelChannels") ? tag.getIntArray("LevelChannels") : next.toArray();
            int[] saved = tag.getIntArray("Levels");
            if (keys.length > ChannelList.MAX_CHANNELS || saved.length > ChannelList.MAX_CHANNELS) return false;
            Map<Integer, Integer> bank = new HashMap<>();
            for (int i = 0; i < keys.length; i++) {
                int stored = i < saved.length ? saved[i] : value;
                if (stored < 0 || stored > 15) return false;
                if (next.contains(keys[i])) bank.put(keys[i], stored);
            }
            channels = next;
            levels.clear();
            levels.putAll(bank);
            min = low;
            max = high;
            level = value;
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    CompoundTag write(CompoundTag tag) {
        tag.putString("Id", id);
        tag.putInt("Kind", 1);
        tag.putInt("Min", min);
        tag.putInt("Max", max);
        tag.putInt("Level", level);
        int[] keys = channels.toArray();
        int[] saved = new int[keys.length];
        for (int i = 0; i < saved.length; i++) saved[i] = latchedLevel(keys[i]);
        tag.putIntArray("RedstoneChannels", keys);
        tag.putIntArray("LevelChannels", keys);
        tag.putIntArray("Levels", saved);
        return tag;
    }
}
