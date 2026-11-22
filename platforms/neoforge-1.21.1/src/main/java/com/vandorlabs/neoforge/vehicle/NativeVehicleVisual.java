package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.transfer.NativeSnapshotAppearance;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The public model of a carried craft. Inventory and tile ownership data stay on the server. */
public final class NativeVehicleVisual {
    public static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_TILE_BYTES = 48 * 1024;
    private static final int MAX_CELLS = 2048;
    private static final int MAX_OFFSET = 32;

    public record Cell(BlockPos pos, BlockState state, CompoundTag appearance) {
        public Cell {
            pos = pos.immutable();
            appearance = appearance.copy();
        }

        @Override public CompoundTag appearance() { return appearance.copy(); }
    }

    private final List<Cell> cells;

    private NativeVehicleVisual(List<Cell> cells) {
        this.cells = List.copyOf(cells);
    }

    public List<Cell> cells() { return cells; }

    static CompoundTag project(NativeTransferSnapshot snapshot, HolderLookup.Provider registries) {
        var rows = new ListTag();
        long retainedBytes = 128;
        for (var source : snapshot.cells()) {
            if (source.state.isAir()) continue;
            var row = new CompoundTag();
            row.putLong("Offset", source.pos.asLong());
            row.putInt("State", Block.getId(source.state));
            if (source.hasTile()) {
                var appearance = projectCell(source.state, source.tile(), registries);
                if (appearance.sizeInBytes() > MAX_TILE_BYTES) throw new IllegalArgumentException("Vehicle appearance tile limit");
                row.put("Appearance", appearance);
            }
            retainedBytes += row.sizeInBytes();
            if (rows.size() >= MAX_CELLS || retainedBytes > MAX_BYTES) throw new IllegalArgumentException("Vehicle appearance limit");
            rows.add(row);
        }
        var tag = new CompoundTag();
        tag.put("Cells", rows);
        return tag;
    }

    static NativeVehicleVisual read(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.getAllKeys().equals(Set.of("Cells")) || tag.sizeInBytes() > MAX_BYTES
                || !(tag.get("Cells") instanceof ListTag rows) || rows.isEmpty()
                || rows.size() > MAX_CELLS || rows.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Malformed vehicle appearance");
        }
        var seen = new HashSet<BlockPos>();
        var cells = new ArrayList<Cell>(rows.size());
        for (var entry : rows) {
            var row = (CompoundTag) entry;
            boolean tile = row.contains("Appearance");
            if (!row.getAllKeys().equals(tile ? Set.of("Offset", "State", "Appearance") : Set.of("Offset", "State"))
                    || !row.contains("Offset", Tag.TAG_LONG) || !row.contains("State", Tag.TAG_INT)
                    || tile && !row.contains("Appearance", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Malformed vehicle appearance cell");
            }
            var at = BlockPos.of(row.getLong("Offset"));
            var state = Block.stateById(row.getInt("State"));
            var appearance = row.getCompound("Appearance");
            if (!bounded(at) || !seen.add(at) || state.isAir() || tile != state.hasBlockEntity()
                    || appearance.sizeInBytes() > MAX_TILE_BYTES
                    || !projectCell(state, appearance, registries).equals(appearance)) {
                throw new IllegalArgumentException("Unsupported vehicle appearance cell");
            }
            cells.add(new Cell(at, state, appearance));
        }
        return new NativeVehicleVisual(cells);
    }

    private static CompoundTag projectCell(BlockState state, CompoundTag raw, HolderLookup.Provider registries) {
        return NativeVehicleArms.block(state) ? NativeVehicleArms.appearance(raw)
            : NativeSnapshotAppearance.project(state, raw, registries);
    }

    static boolean bounded(BlockPos pos) {
        Objects.requireNonNull(pos);
        return Math.abs(pos.getX()) <= MAX_OFFSET && Math.abs(pos.getY()) <= MAX_OFFSET && Math.abs(pos.getZ()) <= MAX_OFFSET;
    }
}
