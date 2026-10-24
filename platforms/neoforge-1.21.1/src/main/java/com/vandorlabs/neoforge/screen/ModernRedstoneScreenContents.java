package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.persistence.NbtPrimitiveData;
import com.vandorlabs.neoforge.redstone.ModernChannelMember;
import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.persistence.ChannelMembershipData;
import com.vandorlabs.persistence.PrimitiveData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneControlRow;
import com.vandorlabs.redstone.RedstoneScreenModel;
import com.vandorlabs.redstone.RedstoneScreenPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Native owner, NBT and deferred lifecycle adapter for the exact shared screen-control model. */
@EventBusSubscriber(modid = "vandorlabs")
public final class ModernRedstoneScreenContents {
    private static final Set<ModernRedstoneScreenContents> PULSING = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final ProgrammableScreenEntity owner;
    private final int slot;
    private final RedstoneScreenModel<Row> model;
    public ModernRedstoneScreenContents(ProgrammableScreenEntity owner, int slot) {
        this.owner = owner; this.slot = slot; model = new RedstoneScreenModel<>(new ModelOwner());
    }
    public ProgrammableScreenEntity tile() { return owner; }
    public int slot() { return slot; }
    public int maxRows() { return owner.rowCapacity(slot); }
    public boolean enabled() { return model.enabled(); }
    public void setEnabled(boolean value) { model.setEnabled(value); }
    public String title() { return model.title(); }
    public List<Row> rows() { return model.rows(); }
    public boolean configure(String title, List<String> labels, List<ChannelList> channels, int housing) { return model.configure(title, labels, channels, housing, true); }
    public boolean configureRows(List<String> labels, List<ChannelList> channels, int housing) { return configure(title(), labels, channels, housing); }
    public void selectSegment(int row, int segment) { model.selectSegment(row, segment); }
    public void toggleRow(int row) { model.toggleRow(row); }
    public void pulseRow(int row) { model.pulseRow(row); }
    public boolean expirePulses(long now) { return model.expirePulses(now); }
    public boolean hasPulses() { return model.hasPulses(); }
    /** Call from the owner's deferred channelLoaded callback, never directly from onLoad. */
    public void finishLoading() { model.finishLoading(); }
    public void unregister() { model.unregisterRows(); PULSING.remove(this); }
    private void changed() {
        owner.setChanged();
        if (owner.getLevel() instanceof ServerLevel) ModernRedstoneChannels.afterSignals(owner, owner::redstoneChanged);
    }
    private static ChannelList channels(CompoundTag tag, String key, ChannelList fallback) {
        return ChannelMembershipData.read(tag.contains(key, 11) ? tag.getIntArray(key) : null, fallback);
    }
    private static List<PrimitiveData> options(ListTag list) {
        List<PrimitiveData> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(new NbtPrimitiveData(list.getCompound(i)));
        return result;
    }
    public ListTag rowConfiguration() {
        ListTag list = new ListTag();
        for (Row row : rows()) {
            CompoundTag entry = new CompoundTag(); row.writeConfiguration(new NbtPrimitiveData(entry));
            entry.putIntArray(ChannelMembershipData.CHANNELS, row.channels.toArray()); list.add(entry);
        }
        return list;
    }
    public CompoundTag configuration() {
        CompoundTag tag = new CompoundTag(); model.writeMetadata(new NbtPrimitiveData(tag)); tag.put("Rows", rowConfiguration()); return tag;
    }
    public static boolean validRows(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!RedstoneScreenPolicy.validOptions(row.getString("Label"), row.getBoolean("Slider"), row.getBoolean("Pulse"), row.getInt("Min"), row.getInt("Max"))) return false;
        }
        return true;
    }
    public static boolean validConfiguration(CompoundTag tag) {
        if (!RedstoneScreenPolicy.validTitle(tag.getString("Title"))) return false;
        ListTag list = tag.getList("Rows", 10);
        if (list.size() > RedstoneScreenPolicy.MAX_ROWS) return false;
        for (int i = 0; i < list.size(); i++) if (!RedstoneScreenPolicy.validLabel(list.getCompound(i).getString("Label"))) return false;
        return validRows(list);
    }
    public boolean applyRowConfiguration(String title, ListTag list) {
        if (list.size() > RedstoneScreenPolicy.MAX_ROWS || !validRows(list)) return false;
        List<String> labels = new ArrayList<>(); List<ChannelList> channels = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) { CompoundTag row = list.getCompound(i); labels.add(row.getString("Label")); channels.add(channels(row, ChannelMembershipData.CHANNELS, ChannelList.EMPTY)); }
        boolean result = configure(title, labels, channels, owner.getHousingTexture());
        if (result) model.applyOptions(options(list)); return result;
    }
    public boolean applyConfiguration(CompoundTag tag) {
        if (!validConfiguration(tag)) return false;
        ListTag list = tag.getList("Rows", 10); List<String> labels = new ArrayList<>(); List<ChannelList> channels = new ArrayList<>();
        for (int i = 0; i < Math.min(maxRows(), list.size()); i++) { CompoundTag row = list.getCompound(i); labels.add(row.getString("Label")); channels.add(channels(row, ChannelMembershipData.CHANNELS, ChannelList.EMPTY)); }
        boolean result = model.configure(tag.getString("Title"), labels, channels, owner.getHousingTexture(), tag.getBoolean("Enabled") && owner.supportsSlot(slot));
        if (result) model.applyOptions(options(list)); return result;
    }
    public CompoundTag save(CompoundTag tag) {
        model.writeMetadata(new NbtPrimitiveData(tag)); ListTag list = new ListTag();
        for (Row row : rows()) {
            CompoundTag entry = new CompoundTag(); row.writeState(new NbtPrimitiveData(entry)); entry.putIntArray(ChannelMembershipData.CHANNELS, row.channels.toArray());
            entry.putIntArray(RedstoneControlRow.LEVELS, row.savedLevels()); entry.putIntArray(RedstoneControlRow.LATCHED_CHANNELS, row.latchedChannels().toArray()); list.add(entry);
        }
        tag.put("Rows", list); return tag;
    }
    public void load(CompoundTag tag) {
        List<Row> next = new ArrayList<>(); ListTag list = tag.getList("Rows", 10);
        for (int i = 0; i < Math.min(RedstoneScreenPolicy.MAX_ROWS, list.size()); i++) {
            CompoundTag entry = list.getCompound(i); String label = entry.getString("Label"); if (!RedstoneScreenPolicy.validLabel(label)) continue;
            Row row = new Row(label, channels(entry, ChannelMembershipData.CHANNELS, ChannelList.EMPTY));
            row.readState(new NbtPrimitiveData(entry), entry.getIntArray(RedstoneControlRow.LEVELS), channels(entry, RedstoneControlRow.LATCHED_CHANNELS, ChannelList.EMPTY)); next.add(row);
        }
        model.restore(tag.getBoolean("Enabled"), tag.contains("Title", 8) ? tag.getString("Title") : null, next);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (ModernRedstoneScreenContents contents : new ArrayList<>(PULSING)) if (contents.owner.getLevel() == level) {
            if (!contents.owner.isChannelMemberActive() || !contents.enabled() || !contents.expirePulses(level.getGameTime())) PULSING.remove(contents);
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        for (ModernRedstoneScreenContents contents : new ArrayList<>(PULSING)) if (contents.owner.getLevel() == event.getLevel()) PULSING.remove(contents);
    }
    private final RedstoneControlRow.Owner rowOwner = new RedstoneControlRow.Owner() {
        public boolean installed() { return owner.isChannelMemberActive(); }
        public boolean hasWorld() { return owner.getLevel() != null; }
        public long time() { return owner.getLevel().getGameTime(); }
        public int channelLevel(int channel) { return owner.getLevel() instanceof ServerLevel level ? ModernRedstoneChannels.level(level, channel) : 0; }
        public boolean allPowered(ChannelList channels) { return owner.getLevel() instanceof ServerLevel level && ModernRedstoneChannels.allPowered(level, channels); }
        public void dirty() { owner.setChanged(); }
        public void changed() { ModernRedstoneScreenContents.this.changed(); }
    };
    private final class ModelOwner implements RedstoneScreenModel.Owner<Row> {
        public Row createRow(String label, ChannelList channels) { return new Row(label, channels); }
        public int maxRows() { return ModernRedstoneScreenContents.this.maxRows(); }
        public boolean validHousing(int choice) { return ModernFinishCatalog.validChoice(choice); }
        public int housing() { return owner.getHousingTexture(); }
        public void housing(int choice) { owner.setHousingTexture(choice); }
        public boolean hasWorld() { return owner.getLevel() != null; }
        public boolean authoritative() { return owner.getLevel() instanceof ServerLevel; }
        public boolean canPulse() { return authoritative(); }
        public boolean publishedLocally() { return true; }
        public long serverTime() { return owner.getLevel().getGameTime(); }
        public void dirty() { owner.setChanged(); }
        public void changed() { ModernRedstoneScreenContents.this.changed(); }
        public void register(Row row) { ModernRedstoneChannels.channelsChanged(row); }
        public void unregister(Row row) { ModernRedstoneChannels.unregister(row); }
        public void inputChanged(Row row) { ModernRedstoneChannels.inputChanged(row); }
        public void latchLevelChanged(Row row, int level) { ModernRedstoneChannels.latchLevelChanged(row, level); }
        public boolean allPowered(ChannelList channels) { return rowOwner.allPowered(channels); }
        public void activatePulses() { if (authoritative()) PULSING.add(ModernRedstoneScreenContents.this); }
    }
    public final class Row extends RedstoneControlRow implements ModernChannelMember {
        Row(String label, ChannelList channels) { super(rowOwner, label, channels); }
        @Override public BlockEntity channelBlockEntity() { return owner; }
    }
}
