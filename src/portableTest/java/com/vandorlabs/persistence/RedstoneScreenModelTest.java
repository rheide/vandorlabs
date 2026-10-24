package com.vandorlabs.persistence;

import com.vandorlabs.redstone.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RedstoneScreenModelTest {
    private static final class Host implements RedstoneControlRow.Owner, RedstoneScreenModel.Owner<RedstoneControlRow> {
        final ChannelNetwork network = new ChannelNetwork(Runnable::run);
        final List<String> events = new ArrayList<>();
        final List<Integer> observed = new ArrayList<>();
        boolean installed = true, world = true, authority = true, local = true;
        long now = 100;
        int capacity = 8, texture, dirty, changes, activities;
        public RedstoneControlRow createRow(String label, ChannelList channels) { return new RedstoneControlRow(this, label, channels); }
        public int maxRows() { return capacity; }
        public boolean validHousing(int choice) { return choice >= 0 && choice <= 3; }
        public int housing() { return texture; }
        public void housing(int choice) { texture = choice; }
        public boolean installed() { return installed; }
        public boolean hasWorld() { return world; }
        public boolean authoritative() { return world && authority; }
        public boolean canPulse() { return world; }
        public boolean publishedLocally() { return local; }
        public long time() { return now; }
        public long serverTime() { return now; }
        public void dirty() { dirty++; }
        public void changed() { changes++; }
        public void register(RedstoneControlRow row) { events.add("+" + row.label); network.register(row); observed.add(network.level(1)); }
        public void unregister(RedstoneControlRow row) { events.add("-" + row.label); network.unregister(row); observed.add(network.level(1)); }
        public void inputChanged(RedstoneControlRow row) { events.add("input"); network.inputChanged(row); }
        public void latchLevelChanged(RedstoneControlRow row, int level) { network.latchChanged(row, level); }
        public int channelLevel(int channel) { return network.level(channel); }
        public boolean allPowered(ChannelList channels) { return network.allPowered(channels); }
        public void activatePulses() { activities++; }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static MemoryPrimitiveData options(boolean slider, boolean pulse, int min, int max) {
        MemoryPrimitiveData data = new MemoryPrimitiveData();
        data.putBoolean("Slider", slider); data.putBoolean("Pulse", pulse); data.putInt("Min", min); data.putInt("Max", max); return data;
    }
    public static void main(String[] args) {
        require(RedstoneScreenPolicy.REDSTONE_SURFACE == -2 && RedstoneScreenPolicy.VEHICLE_SURFACE == -3, "saved surface sentinels");
        require(RedstoneScreenPolicy.validTitle("") && !RedstoneScreenPolicy.validTitle("bad\n") && !RedstoneScreenPolicy.validLabel("  ")
                && !RedstoneScreenPolicy.validLabel("\u00a7color"), "text policy");
        require(RedstoneScreenPolicy.validOptions("legacy", true, false, 0, 0), "collapsed legacy ranges accepted before migration");
        require(!RedstoneScreenPolicy.validOptions("bad", true, true, 5, 15), "slider/pulse exclusion");
        Host host = new Host(); RedstoneScreenModel<RedstoneControlRow> model = new RedstoneScreenModel<>(host);
        List<String> labels = Arrays.asList(" Toggle ", "Throttle", "Pulse");
        List<ChannelList> channels = Arrays.asList(ChannelList.of(1), ChannelList.of(2, 3), ChannelList.of(1));
        require(model.configure(" Control ", labels, channels, 2, true), "initial configuration");
        require(model.title().equals("Control") && model.rows().get(0).label.equals("Toggle") && host.texture == 2, "trim and housing");
        model.applyOptions(Arrays.asList(options(false, false, 5, 15), options(true, false, 5, 15), options(false, true, 5, 15)));
        model.toggleRow(0); require(host.network.level(1) == 15, "toggle on");
        model.selectSegment(1, 2); RedstoneControlRow slider = model.rows().get(1);
        require(slider.level() == 10 && slider.selectedSegment() == 2 && host.network.level(2) == 10 && host.network.level(3) == 10, "slider publishes exact multi-channel level");
        model.selectSegment(1, 4); require(slider.level() == 10, "segment bounds");
        require(Arrays.equals(new int[]{slider.segmentValue(0), slider.segmentValue(1), slider.segmentValue(2), slider.segmentValue(3)}, new int[]{0, 5, 10, 15}), "four detents");
        require(RedstoneScreenPolicy.stepForLevel(1, 5, 15) == 1 && RedstoneScreenPolicy.stepForLevel(0, 5, 15) == 0, "positive levels never select Off");
        host.events.clear(); host.observed.clear();
        require(model.configure("Control", labels, channels, 2, true), "edit while powered");
        require(host.events.subList(0, 3).equals(Arrays.asList("+Toggle", "+Throttle", "+Pulse")), "register replacements before removal");
        for (int level : host.observed) require(level == 15, "editing must not glitch unchanged bank");
        require(model.rows().get(1).latchedLevel(2) == 10, "editing retains exact levels");
        slider = model.rows().get(1);
        model.toggleRow(0); require(host.network.level(1) == 0, "linked off");
        model.toggleRow(2); RedstoneControlRow pulse = model.rows().get(2);
        require(pulse.active() && pulse.pulseUntil() == 120 && host.network.level(1) == 15, "twenty tick pulse");
        model.toggleRow(0);
        require(model.rows().get(0).latchedLevel(1) == 0 && host.network.level(1) == 15, "independent pulse does not hold latch on");
        host.now = 110; model.toggleRow(2); require(pulse.pulseUntil() == 130, "repeat pulse extends deadline");
        host.now = 129; require(model.expirePulses(host.now) && host.network.level(1) == 15, "pulse not early");
        host.now = 130; require(!model.expirePulses(host.now) && host.network.level(1) == 0 && !pulse.active(), "pulse expiry exactly at deadline");
        int count = model.rows().size(); require(!model.configure("Control", labels, channels, 99, true) && model.rows().size() == count, "invalid housing atomic rejection");
        host.authority = false; model.selectSegment(1, 3); model.toggleRow(0); require(slider.latchedLevel(2) == 10 && host.network.level(1) == 0, "client view cannot mutate latch");
        host.local = false; host.events.clear(); model.pulseRow(2); require(!host.events.contains("input") && pulse.active(), "carried pulse retained for external publisher");
        model.setEnabled(false); require(!model.enabled() && host.network.level(2) == 0, "disabled rows unregister");

        Host loadedHost = new Host(); loadedHost.world = false; loadedHost.capacity = 4;
        RedstoneScreenModel<RedstoneControlRow> loaded = new RedstoneScreenModel<>(loadedHost);
        List<RedstoneControlRow> saved = new ArrayList<>();
        for (int i = 0; i < 8; i++) saved.add(loadedHost.createRow("Saved " + i, ChannelList.of(i + 1)));
        loaded.restore(true, null, saved);
        require(loaded.rows().size() == 8 && loadedHost.events.isEmpty() && loaded.title().equals(RedstoneScreenPolicy.DEFAULT_TITLE), "NBT is local before world attachment");
        loadedHost.world = true; loaded.finishLoading(); require(loaded.rows().size() == 4 && loadedHost.events.size() == 4 && loadedHost.dirty == 1, "deferred native capacity trim and registration");
        try { loaded.rows().clear(); throw new AssertionError("mutable row view"); } catch (UnsupportedOperationException expected) { }
        RedstoneControlRow migrated = loadedHost.createRow("Range", ChannelList.of(42));
        migrated.readOptions(options(true, false, 0, 0));
        require(migrated.min == 1 && migrated.max == 3 && migrated.segmentValue(2) == 2, "collapsed range migration");
        MemoryPrimitiveData typed = options(true, false, 0, 15); typed.putBoolean("Max", false); migrated.readOptions(typed);
        require(migrated.max == 15 && migrated.min == 5, "typed maximum legacy default");
        migrated.readLevels(new int[]{7}, 7); migrated.restoreState(ChannelList.of(42), true, 0);
        require(migrated.latchedLevel(42) == 7 && migrated.level() == 7 && migrated.active(), "per-channel and display levels restored");
        MemoryPrimitiveData savedState = new MemoryPrimitiveData(); migrated.writeState(savedState);
        require(savedState.getString("Label").equals("Range") && savedState.getBoolean("Slider") && !savedState.getBoolean("Pulse")
                && savedState.getInt("Min") == 5 && savedState.getInt("Max") == 15 && savedState.getInt("Level") == 7
                && savedState.getLong("PulseUntil") == 0 && savedState.getBoolean("Active"), "primitive row saved schema");
        RedstoneControlRow restored = loadedHost.createRow(savedState.getString(RedstoneControlRow.LABEL), ChannelList.of(42));
        restored.readState(savedState, migrated.savedLevels(), migrated.latchedChannels());
        require(restored.latchedLevel(42) == 7 && restored.level() == 7 && restored.active() && restored.slider, "shared row state roundtrip");
        migrated.readOptions(options(false, true, 5, 15));
        require(migrated.latchedLevel(42) == 0 && migrated.pulse && !migrated.slider, "pulse conversion clears latch bank state");
        migrated.startPulse(500); migrated.writeState(savedState);
        loadedHost.now = 510; restored.readState(savedState, migrated.savedLevels(), migrated.latchedChannels());
        require(restored.pulse && !restored.slider && restored.pulseUntil() == 520 && restored.active(), "saved pulse deadline roundtrip");
        loadedHost.now = 520; require(!restored.active() && restored.expirePulse(520), "saved pulse expires without restarting timeline");
        loaded.restore(false, "", Collections.<RedstoneControlRow>emptyList());
        System.out.println("Redstone screen model PASS: legacy validation/detents, linked levels, edit ordering, pulse extension/expiry, authority and deferred lifecycle");
    }
}
