package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.screen.NativeScreenBlock;
import com.vandorlabs.neoforge.screen.NativeScreenControls;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.redstone.ChannelList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/** Server-owned channel subscriptions. The shared hull ordering identifies each public four-bit reading. */
final class NativeVehicleMeters {
    private record Input(BlockPos pos, int surface, int row, ChannelList channels) {}
    private final Map<BlockPos,Integer> offsets = new HashMap<>();
    private final List<Input> inputs = new ArrayList<>();
    private int[] levels = new int[0];
    private int cursor;

    static NativeVehicleMeters server(NativeTransferSnapshot snapshot) {
        var meters = new NativeVehicleMeters();
        for (var cell : snapshot.cells()) {
            if (NativeVehicleStatus.supports(cell.state)) meters.add(cell.pos, cell.state, cell.tile(), true);
        }
        meters.levels = new int[2 * meters.offsets.size()];
        return meters;
    }

    static NativeVehicleMeters client(NativeVehicleVisual visual) {
        var meters = new NativeVehicleMeters();
        for (var cell : visual.cells()) {
            if (NativeVehicleStatus.supports(cell.state())) meters.add(cell.pos(), cell.state(), cell.appearance(), false);
        }
        meters.levels = new int[2 * meters.offsets.size()];
        return meters;
    }

    private void add(BlockPos pos, BlockState state, CompoundTag tag, boolean server) {
        if (tag == null) return;
        var family = ((NativeScreenBlock)state.getBlock()).family();
        for (int slot = 0; slot < 2; slot++) {
            if (!family.supportsSlot(slot)) continue;
            String name = slot == 0 ? "RedstonePrimary" : "RedstoneSecondary";
            var rows = NativeScreenControls.read(tag.getCompound(name), family.standaloneControlRows(slot));
            if (!rows.enabled()) continue;
            for (int row = 0; row < rows.rows().size(); row++) {
                var value = rows.rows().get(row);
                if (!value.status()) continue;
                offsets.computeIfAbsent(pos, ignored -> offsets.size() * 2);
                if (server) inputs.add(new Input(pos, offsets.get(pos) + slot, row, value.channels()));
            }
        }
    }

    boolean sample(NativeGroundVehicle craft, NativeVehicleResources resources) {
        boolean changed = false;
        // Bound wireless lookups for large moving hulls and rotate through all panels.
        int count = Math.min(96, inputs.size());
        for (int i = 0; i < count; i++) {
            var input = inputs.get(cursor++);
            if (cursor == inputs.size()) cursor = 0;
            int shift = input.row * 4;
            int previous = levels[input.surface];
            int signal = resources.channelLevel(craft, input.pos, input.channels);
            int next = (previous & ~(15 << shift)) | (signal << shift);
            if (previous != next) {
                levels[input.surface] = next;
                changed = true;
            }
        }
        return changed;
    }

    CompoundTag publicData() {
        var tag = new CompoundTag();
        tag.putIntArray("Levels", levels.clone());
        return tag;
    }

    void receive(CompoundTag tag) {
        if (tag.get("Levels") instanceof net.minecraft.nbt.IntArrayTag values && values.size() == levels.length)
            levels = values.getAsIntArray();
    }

    int surface(BlockPos pos, int slot) {
        var offset = offsets.get(pos);
        return offset == null ? 0 : levels[offset + slot];
    }

    static void project(CompoundTag source, CompoundTag target, String name, int capacity) {
        if (!source.contains(name, Tag.TAG_COMPOUND)) return;
        var rows = NativeScreenControls.read(source.getCompound(name), capacity);
        if (!rows.enabled()) return;
        var result = rows.configuration();
        for (var entry : result.getList("Rows", Tag.TAG_COMPOUND))
            ((CompoundTag)entry).remove("RedstoneChannels");
        target.put(name, result);
    }
}
