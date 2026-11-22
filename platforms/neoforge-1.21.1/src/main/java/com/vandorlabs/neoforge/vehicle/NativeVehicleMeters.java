package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.screen.NativeScreenBlock;
import com.vandorlabs.neoforge.screen.NativeScreenCatalog;
import com.vandorlabs.neoforge.screen.NativeScreenControls;
import com.vandorlabs.neoforge.screen.NativeScreenSettings;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.persistence.SignalControlData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelSpatialPolicy;
import com.vandorlabs.redstone.TransmissionSettings;
import com.vandorlabs.screen.ScreenControlLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Carried row choices, owned outputs and read-only feedback. The client receives presentation levels only. */
final class NativeVehicleMeters {
    private static final String[] SLOT_KEYS = {"RedstonePrimary", "RedstoneSecondary"};
    private static final int PULSE_TICKS = 20;
    record Click(int surface, int row, int segment) {}

    private static final class Panel {
        final BlockPos pos;
        final int offset, range;
        final boolean local;
        final Surface[] surfaces = new Surface[2];

        Panel(BlockPos pos, int offset, int range, boolean local) {
            this.pos = pos;
            this.offset = offset;
            this.range = range;
            this.local = local;
        }
    }

    private static final class Surface {
        final String title;
        final ScreenControlLayout.Projection geometry;
        final List<Control> controls = new ArrayList<>();

        Surface(String title, ScreenControlLayout.Projection geometry) {
            this.title = title;
            this.geometry = geometry;
        }

        NativeScreenControls saved() {
            return new NativeScreenControls(true, title, controls.stream().map(control -> control.row).toList());
        }
    }

    private static final class Control {
        final Panel panel;
        final int surface, index;
        NativeScreenControls.Row row;

        Control(Panel panel, int surface, int index, NativeScreenControls.Row row) {
            this.panel = panel;
            this.surface = surface;
            this.index = index;
            this.row = row;
        }

        int output(int channel) {
            return row.pulse() ? row.pulseUntil() > 0 ? 15 : 0 : row.latchedLevel(channel);
        }
    }

    private final Map<BlockPos, Panel> panels = new LinkedHashMap<>();
    private final List<Control> inputs = new ArrayList<>();
    private final Map<Integer, Map<Control, Integer>> channels = new HashMap<>();
    private final Map<BlockPos, Integer> locals = new HashMap<>();
    private final TreeMap<Long, Set<Control>> pulses = new TreeMap<>();
    private final Set<BlockPos> changedPanels = new HashSet<>();
    private NativeTransferSnapshot savedInput, savedSnapshot;
    private int[] levels = new int[0];
    private int cursor, reservedBytes;
    private boolean changed, presentationDirty;

    static NativeVehicleMeters server(NativeTransferSnapshot snapshot, long now) {
        var meters = new NativeVehicleMeters();
        for (var cell : snapshot.cells()) {
            if (NativeVehicleStatus.supports(cell.state)) meters.add(cell.pos, cell.state, cell.tile(), true);
        }
        meters.levels = new int[2 * meters.panels.size()];
        for (var panel : meters.panels.values()) for (var surface : panel.surfaces) {
            if (surface == null) continue;
            for (var control : surface.controls) {
                var row = control.row;
                if (row.status()) {
                    meters.inputs.add(control);
                    continue;
                }
                if (row.pulseUntil() > 0) {
                    meters.schedule(control);
                    // Preserve the exact receipt before publication. The first live tick clears expired rows.
                    if (row.pulseUntil() <= now) continue;
                }
                meters.publish(control);
            }
        }
        return meters;
    }

    static NativeVehicleMeters client(NativeVehicleVisual visual) {
        var meters = new NativeVehicleMeters();
        for (var cell : visual.cells()) {
            if (NativeVehicleStatus.supports(cell.state())) meters.add(cell.pos(), cell.state(), cell.appearance(), false);
        }
        meters.levels = new int[2 * meters.panels.size()];
        return meters;
    }

    private void add(BlockPos pos, BlockState state, CompoundTag tag, boolean server) {
        if (tag == null || !tag.getCompound(SLOT_KEYS[0]).getBoolean("Enabled")
                && !tag.getCompound(SLOT_KEYS[1]).getBoolean("Enabled")) return;
        var block = (NativeScreenBlock)state.getBlock();
        var family = block.family();
        var settings = NativeScreenSettings.read(tag, NativeScreenCatalog.INSTANCE);
        var shape = block.screenState(state);
        var scalar = settings.scalar();
        var transmission = tag.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");
        int range = TransmissionSettings.readRange(transmission.contains("Range", Tag.TAG_INT), transmission.getInt("Range"), true);
        var panel = new Panel(pos, panels.size() * 2, range, transmission.getBoolean("Local"));
        CompoundTag largest = server ? tag.copy() : null;
        for (int slot = 0; slot < 2; slot++) {
            if (!family.supportsSlot(slot)) continue;
            var rows = NativeScreenControls.read(tag.getCompound(SLOT_KEYS[slot]), family.standaloneControlRows(slot));
            if (!rows.enabled() || rows.rows().isEmpty()) continue;
            var projection = ScreenControlLayout.of(family, shape, slot, scalar.smallInput, scalar.wallPosition,
                    settings.ceilingMounted(), settings.ceilingPosition(), false);
            var surface = new Surface(rows.title(), projection);
            for (var row : rows.rows()) surface.controls.add(new Control(panel, slot, surface.controls.size(), row));
            panel.surfaces[slot] = surface;
            if (largest != null) {
                var expanded = largest.getCompound(SLOT_KEYS[slot]);
                var full = rows.rows().stream().map(row -> !row.status() && !row.pulse() ? latchRow(row, 15) : row).toList();
                new NativeScreenControls(true, rows.title(), full).write(expanded);
                largest.put(SLOT_KEYS[slot], expanded);
            }
        }
        if (panel.surfaces[0] == null && panel.surfaces[1] == null) return;
        panels.put(pos, panel);
        if (largest != null) {
            if (largest.sizeInBytes() > NativeVehicleStructure.LIMITS.tileBytes()) {
                throw new IllegalArgumentException("Vehicle screen has no room for its saved control state");
            }
            reservedBytes += Math.max(0, largest.sizeInBytes() - tag.sizeInBytes());
        }
    }

    int reservedBytes() { return reservedBytes; }

    Click hit(BlockPos at, Vec3 start, Vec3 end) {
        var panel = panels.get(at);
        if (panel == null) return null;
        double x = at.getX() - .5, y = at.getY(), z = at.getZ() - .5;
        for (int slot = 0; slot < panel.surfaces.length; slot++) {
            var surface = panel.surfaces[slot];
            if (surface == null) continue;
            var sample = surface.geometry.sample(start.x - x, start.y - y, start.z - z, end.x - x, end.y - y, end.z - z);
            int index = sample.row(surface.controls.size());
            if (index < 0) continue;
            var row = surface.controls.get(index).row;
            if (row.status()) return null;
            int segment = row.slider() ? sample.sliderStep() : -1;
            return row.slider() && segment < 0 ? null : new Click(slot, index, segment);
        }
        return null;
    }

    void activate(BlockPos at, Click click, long now) {
        var panel = panels.get(at);
        if (panel == null || click.surface < 0 || click.surface >= panel.surfaces.length) return;
        var surface = panel.surfaces[click.surface];
        if (surface == null || click.row < 0 || click.row >= surface.controls.size()) return;
        var control = surface.controls.get(click.row);
        var row = control.row;
        if (row.status() || row.channels().isEmpty()) return;
        if (row.pulse()) {
            cancelTimer(control);
            replace(control, pulseRow(row, now + PULSE_TICKS));
            schedule(control);
        } else if (row.slider() && click.segment >= 0) {
            replace(control, latchRow(row, SignalControlData.levelForStep(click.segment, row.min(), row.max())));
        } else if (!row.slider()) {
            replace(control, latchRow(row, row.latched().containsAll(row.channels()) ? 0 : 15));
        }
    }

    private static NativeScreenControls.Row latchRow(NativeScreenControls.Row row, int next) {
        var levels = new HashMap<Integer, Integer>();
        for (int i = 0; i < row.channels().size(); i++) levels.put(row.channels().get(i), next);
        return new NativeScreenControls.Row(row.label(), row.channels(), row.slider(), false, row.min(), row.max(),
                row.slider() ? next : 0, levels, next == 0 ? ChannelList.EMPTY : row.channels(), next != 0, 0, false);
    }

    private static NativeScreenControls.Row pulseRow(NativeScreenControls.Row row, long until) {
        return new NativeScreenControls.Row(row.label(), row.channels(), false, true, row.min(), row.max(),
                0, Map.of(), ChannelList.EMPTY, until > 0, until, false);
    }

    private void schedule(Control control) {
        pulses.computeIfAbsent(control.row.pulseUntil(), unused -> new HashSet<>()).add(control);
    }

    private void cancelTimer(Control control) {
        long until = control.row.pulseUntil();
        var bucket = pulses.get(until);
        if (bucket != null && bucket.remove(control) && bucket.isEmpty()) pulses.remove(until);
    }

    void expire(long now) {
        while (!pulses.isEmpty() && pulses.firstKey() <= now) {
            for (var control : pulses.pollFirstEntry().getValue()) replace(control, pulseRow(control.row, 0));
        }
    }

    private void replace(Control control, NativeScreenControls.Row next) {
        if (control.row.equals(next)) return;
        control.row = next;
        changed = true;
        changedPanels.add(control.panel.pos);
        publish(control);
    }

    private void publish(Control control) {
        var row = control.row;
        int display = 0;
        for (int i = 0; i < row.channels().size(); i++) {
            int channel = row.channels().get(i);
            int signal = control.output(channel);
            display = Math.max(display, signal);
            if (signal > 0) channels.computeIfAbsent(channel, unused -> new HashMap<>()).put(control, signal);
            else {
                var owners = channels.get(channel);
                if (owners != null && owners.remove(control) != null && owners.isEmpty()) channels.remove(channel);
            }
        }
        presentation(control, display);
        if (!control.panel.local) return;
        int output = 0;
        for (var other : control.panel.surfaces) if (other != null) {
            for (var source : other.controls) {
                if (!source.row.status()) output = Math.max(output, (levels[source.panel.offset + source.surface] >>> (4 * source.index)) & 15);
            }
        }
        if (output > 0) locals.put(control.panel.pos, output);
        else locals.remove(control.panel.pos);
    }

    private void presentation(Control control, int signal) {
        int slot = control.panel.offset + control.surface;
        int shift = 4 * control.index;
        int old = levels[slot];
        int next = (old & ~(15 << shift)) | (signal << shift);
        if (old != next) {
            levels[slot] = next;
            presentationDirty = true;
        }
    }

    boolean sample(NativeGroundVehicle craft, NativeVehicleResources resources, boolean readInputs) {
        if (readInputs) {
            int count = Math.min(96, inputs.size());
            for (int i = 0; i < count; i++) {
                var input = inputs.get(cursor++);
                if (cursor == inputs.size()) cursor = 0;
                presentation(input, resources.channelLevel(craft, input.panel.pos, input.row.channels()));
            }
        }
        boolean dirty = presentationDirty;
        presentationDirty = false;
        return dirty;
    }

    boolean hasLocalOutput() { return !locals.isEmpty(); }
    int localOutput(BlockPos source) { return locals.getOrDefault(source, 0); }

    int channelOutput(BlockPos receiver, ChannelList selected) {
        int maximum = 0;
        for (int i = 0; i < selected.size(); i++) {
            var owners = channels.get(selected.get(i));
            if (owners == null) continue;
            for (var entry : owners.entrySet()) {
                int strength = entry.getValue();
                if (strength <= maximum) continue;
                var panel = entry.getKey().panel;
                if (ChannelSpatialPolicy.finiteContains(panel.pos.getX(), panel.pos.getY(), panel.pos.getZ(),
                        receiver.getX(), receiver.getY(), receiver.getZ(), panel.range)) maximum = strength;
                if (maximum == 15) return maximum;
            }
        }
        return maximum;
    }

    NativeTransferSnapshot snapshot(NativeTransferSnapshot input) {
        if (changedPanels.isEmpty()) return input;
        if (!changed && input == savedInput) return savedSnapshot;
        var cells = new ArrayList<NativeRawCell>(input.cells().size());
        for (var cell : input.cells()) {
            if (!changedPanels.contains(cell.pos)) {
                cells.add(cell);
                continue;
            }
            var raw = cell.tile();
            var panel = panels.get(cell.pos);
            for (int slot = 0; slot < 2; slot++) {
                var surface = panel.surfaces[slot];
                if (surface == null) continue;
                var data = raw.getCompound(SLOT_KEYS[slot]);
                surface.saved().write(data);
                raw.put(SLOT_KEYS[slot], data);
            }
            cells.add(new NativeRawCell(cell.pos, cell.state, raw, NativeVehicleStructure.LIMITS.tileBytes()));
        }
        savedInput = input;
        savedSnapshot = new NativeTransferSnapshot(cells, NativeVehicleStructure.LIMITS);
        changed = false;
        return savedSnapshot;
    }

    CompoundTag publicData() {
        var tag = new CompoundTag();
        tag.putIntArray("Levels", levels.clone());
        return tag;
    }

    void receive(CompoundTag tag) {
        if (tag.get("Levels") instanceof IntArrayTag values && values.size() == levels.length) levels = values.getAsIntArray();
    }

    int surface(BlockPos pos, int slot) {
        var panel = panels.get(pos);
        return panel == null ? 0 : levels[panel.offset + slot];
    }

    static void project(CompoundTag source, CompoundTag target, String name, int capacity) {
        if (!source.contains(name, Tag.TAG_COMPOUND)) return;
        var rows = NativeScreenControls.read(source.getCompound(name), capacity);
        if (!rows.enabled()) return;
        var result = rows.configuration();
        for (var entry : result.getList("Rows", Tag.TAG_COMPOUND)) ((CompoundTag)entry).remove("RedstoneChannels");
        target.put(name, result);
    }
}
