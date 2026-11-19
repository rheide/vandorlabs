package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.transfer.NativeSnapshotAppearance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Sparse, bounded render projection. The owned snapshot never leaves the physical server. */
final class NativeTurntableVisual {
    private static final int MAX_CELLS = 4096;
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_TILE_BYTES = 48 * 1024;

    record Cell(BlockPos offset, BlockState state, CompoundTag appearance) {
        Cell {
            offset = offset.immutable();
            appearance = appearance.copy();
        }
    }

    private final List<Cell> cells;
    private final AABB sweep;
    private final Vec3 pivot;
    private final Direction normal;

    private NativeTurntableVisual(List<Cell> cells, AABB bounds, Vec3 pivot, Direction normal) {
        this.cells = List.copyOf(cells);
        this.pivot = pivot;
        this.normal = normal;
        double[] reach = {Math.max(Math.abs(bounds.minX - pivot.x), Math.abs(bounds.maxX - pivot.x)),
                Math.max(Math.abs(bounds.minY - pivot.y), Math.abs(bounds.maxY - pivot.y)),
                Math.max(Math.abs(bounds.minZ - pivot.z), Math.abs(bounds.maxZ - pivot.z))};
        int axis = normal.getAxis().ordinal();
        double radius = Math.hypot(reach[(axis + 1) % 3], reach[(axis + 2) % 3]);
        sweep = new AABB(axis == 0 ? bounds.minX : pivot.x - radius,
                axis == 1 ? bounds.minY : pivot.y - radius, axis == 2 ? bounds.minZ : pivot.z - radius,
                axis == 0 ? bounds.maxX : pivot.x + radius, axis == 1 ? bounds.maxY : pivot.y + radius,
                axis == 2 ? bounds.maxZ : pivot.z + radius);
    }

    List<Cell> cells() { return cells; }
    AABB bounds() { return sweep; }
    Vec3 pivot() { return pivot; }
    Direction normal() { return normal; }

    static CompoundTag project(NativeTurntableCapture capture, BlockPos pos, HolderLookup.Provider registries) {
        var list = new ListTag();
        long retained = 0;
        for (var cell : capture.blocks().cells()) {
            if (cell.state.isAir()) continue;
            var tag = new CompoundTag();
            tag.putLong("Offset", cell.pos.subtract(pos).asLong());
            tag.putInt("State", Block.getId(cell.state));
            if (cell.hasTile()) {
                var appearance = NativeSnapshotAppearance.project(cell.state, cell.tile(), registries);
                if (appearance.sizeInBytes() > MAX_TILE_BYTES) throw new IllegalArgumentException("Turntable appearance tile limit");
                tag.put("Appearance", appearance);
            }
            retained += tag.sizeInBytes();
            if (retained > MAX_BYTES || list.size() >= MAX_CELLS) throw new IllegalArgumentException("Turntable public appearance limit");
            list.add(tag);
        }
        var visual = new CompoundTag();
        visual.put("Cells", list);
        var surface = capture.surface();
        visual.putInt("Normal", surface.normal.get3DDataValue());
        visual.putDouble("PivotX", surface.geometry.pivotX - pos.getX());
        visual.putDouble("PivotY", surface.geometry.pivotY - pos.getY());
        visual.putDouble("PivotZ", surface.geometry.pivotZ - pos.getZ());
        if (visual.sizeInBytes() > MAX_BYTES) throw new IllegalArgumentException("Turntable public appearance limit");
        return visual;
    }

    static NativeTurntableVisual read(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.getAllKeys().equals(Set.of("Cells", "Normal", "PivotX", "PivotY", "PivotZ"))
                || !tag.contains("Normal", Tag.TAG_INT) || tag.getInt("Normal") < 0 || tag.getInt("Normal") > 5
                || !pivotField(tag, "PivotX") || !pivotField(tag, "PivotY") || !pivotField(tag, "PivotZ")
                || !(tag.get("Cells") instanceof ListTag list)
                || list.size() > MAX_CELLS || tag.sizeInBytes() > MAX_BYTES
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Malformed Turntable appearance");
        }
        var normal = Direction.from3DDataValue(tag.getInt("Normal"));
        var seen = new HashSet<BlockPos>();
        var cells = new ArrayList<Cell>();
        AABB bounds = null;
        for (Tag value : list) {
            var row = (CompoundTag) value;
            if (!row.contains("Offset", Tag.TAG_LONG) || !row.contains("State", Tag.TAG_INT)
                    || !row.getAllKeys().equals(row.contains("Appearance") ? Set.of("Offset", "State", "Appearance") : Set.of("Offset", "State"))
                    || row.contains("Appearance") && !row.contains("Appearance", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Malformed Turntable model cell");
            }
            var offset = BlockPos.of(row.getLong("Offset"));
            var state = Block.stateById(row.getInt("State"));
            var appearance = row.getCompound("Appearance");
            if (!seen.add(offset) || Math.abs(offset.getX()) > 64 || Math.abs(offset.getY()) > 64
                    || Math.abs(offset.getZ()) > 64 || state.isAir()
                    || appearance.sizeInBytes() > MAX_TILE_BYTES || state.hasBlockEntity() != row.contains("Appearance")
                    || !NativeSnapshotAppearance.canonical(state, appearance, registries)) {
                throw new IllegalArgumentException("Unsupported Turntable model cell");
            }
            NativeTurntableCapture.requireSupported(state, normal);
            cells.add(new Cell(offset, state, appearance));
            var volume = new AABB(offset);
            bounds = bounds == null ? volume : bounds.minmax(volume);
        }
        var pivot = new Vec3(tag.getDouble("PivotX"), tag.getDouble("PivotY"), tag.getDouble("PivotZ"));
        return new NativeTurntableVisual(cells, bounds == null ? new AABB(BlockPos.ZERO) : bounds,
                pivot, normal);
    }

    private static boolean pivotField(CompoundTag tag, String name) {
        return tag.contains(name, Tag.TAG_DOUBLE) && Math.abs(tag.getDouble(name)) <= 64;
    }
}
