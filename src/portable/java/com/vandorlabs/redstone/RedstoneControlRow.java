package com.vandorlabs.redstone;

import com.vandorlabs.persistence.PrimitiveData;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** One logical row, independent of block entities, NBT APIs, registration and rendering. */
public class RedstoneControlRow implements ChannelLatch {
    public static final String LABEL = "Label", LEVELS = "Levels", LATCHED_CHANNELS = "LatchedChannels";
    public interface Owner {
        boolean installed();
        boolean hasWorld();
        long time();
        int channelLevel(int channel);
        boolean allPowered(ChannelList channels);
        void dirty();
        void changed();
    }
    private final Owner owner;
    public final String label;
    public final ChannelList channels;
    public boolean slider, pulse;
    public int min = 5, max = 15;
    private ChannelList latched = ChannelList.EMPTY;
    private boolean active;
    private long pulseUntil;
    private int level;
    private final Map<Integer, Integer> levels = new HashMap<>();
    public RedstoneControlRow(Owner owner, String label, ChannelList channels) {
        this.owner = java.util.Objects.requireNonNull(owner);
        this.label = label; this.channels = channels;
    }
    @Override public boolean isChannelMemberActive() { return owner.installed(); }
    @Override public ChannelList getRedstoneChannels() { return channels; }
    public boolean active() { return pulse ? pulseUntil > 0 && owner.hasWorld() && owner.time() < pulseUntil : active; }
    public int level() { return level; }
    public long pulseUntil() { return pulseUntil; }
    public int segments() { return 4; }
    public int segmentValue(int step) { return RedstoneScreenPolicy.levelForStep(step, min, max); }
    public int selectedSegment() { return RedstoneScreenPolicy.stepForLevel(level, min, max); }
    @Override public boolean isChannelLatch() { return !pulse; }
    public ChannelList latchedChannels() { return latched; }
    public boolean latchOn() { return !channels.isEmpty() && latched.containsAll(channels); }
    @Override public int latchedLevel(int channel) { return levels.getOrDefault(channel, latched.contains(channel) ? 15 : 0); }
    @Override public int localSignalLevel(int channel) { return pulse ? active() ? 15 : 0 : latchedLevel(channel); }
    @Override public void applyLinkedLevels(Map<Integer, Integer> value) {
        levels.clear(); levels.putAll(value);
        int[] ids = new int[value.size()]; int count = 0;
        for (Map.Entry<Integer, Integer> entry : value.entrySet()) if (entry.getValue() > 0) ids[count++] = entry.getKey();
        applyLinkedChannels(ChannelList.of(Arrays.copyOf(ids, count)));
    }
    public void applyLinkedChannels(ChannelList value) {
        if (!latched.equals(value)) { latched = value; owner.dirty(); }
    }
    public void applyLinkedLatch(boolean on) { applyLinkedChannels(on ? channels : ChannelList.EMPTY); }
    @Override public void setChannelLevel(int ignored) { settle(); }
    public void settle() {
        int next = 0;
        for (int i = 0; i < channels.size(); i++) next = Math.max(next, owner.channelLevel(channels.get(i)));
        if (slider && level != next) { level = next; owner.changed(); }
        boolean powered = owner.allPowered(channels);
        if (active != powered) { active = powered; owner.changed(); }
    }
    public void startPulse(long now) { pulseUntil = now + 20; owner.changed(); }
    public boolean expirePulse(long now) {
        if (pulseUntil <= 0 || now < pulseUntil) return false;
        pulseUntil = 0; owner.changed(); return true;
    }
    /** Editing preserves old per-channel levels and options, but does not replay a transient pulse. */
    public void copyConfigurationState(RedstoneControlRow previous) {
        latched = previous.latched.intersect(channels); levels.putAll(previous.levels);
        slider = previous.slider; pulse = previous.pulse; min = previous.min; max = previous.max;
    }
    public void writeOptions(PrimitiveData tag) {
        tag.putBoolean("Slider", slider); tag.putBoolean("Pulse", pulse); tag.putInt("Min", min); tag.putInt("Max", max);
    }
    public void writeConfiguration(PrimitiveData tag) { tag.putString(LABEL, label); writeOptions(tag); }
    public void writeState(PrimitiveData tag) {
        writeConfiguration(tag); tag.putInt("Level", level); tag.putLong("PulseUntil", pulseUntil); tag.putBoolean("Active", active);
    }
    /** Arrays are decoded by the host; their interpretation and all primitive saved fields are shared. */
    public void readState(PrimitiveData tag, int[] savedLevels, ChannelList savedLatched) {
        readOptions(tag); readLevels(savedLevels, tag.getInt("Level"));
        restoreState(savedLatched, tag.getBoolean("Active"), tag.getLong("PulseUntil"));
    }
    public void readOptions(PrimitiveData tag) {
        pulse = tag.getBoolean("Pulse"); slider = !pulse && tag.getBoolean("Slider");
        if (pulse) { latched = ChannelList.EMPTY; levels.clear(); } else pulseUntil = 0;
        max = tag.containsInt("Max") ? Math.max(3, Math.min(15, tag.getInt("Max"))) : 15;
        int low = tag.getInt("Min"); min = Math.max(1, Math.min(max - 2, low == 0 ? 5 : low));
    }
    public int[] savedLevels() {
        int[] result = new int[channels.size()];
        for (int i = 0; i < result.length; i++) result[i] = latchedLevel(channels.get(i));
        return result;
    }
    public void readLevels(int[] saved, int currentLevel) {
        for (int i = 0; i < Math.min(saved.length, channels.size()); i++) levels.put(channels.get(i), WirelessSignalPolicy.clampLevel(saved[i]));
        level = WirelessSignalPolicy.clampLevel(currentLevel);
    }
    public void restoreState(ChannelList latched, boolean active, long deadline) {
        this.latched = latched.intersect(channels); this.active = active;
        if (pulse) pulseUntil = Math.max(0, deadline);
    }
    public boolean savedActive() { return active; }
}
