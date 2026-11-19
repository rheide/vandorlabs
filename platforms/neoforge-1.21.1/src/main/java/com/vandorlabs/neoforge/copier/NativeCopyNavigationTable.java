package com.vandorlabs.neoforge.copier;

import com.vandorlabs.navigation.NavigationLayout;
import com.vandorlabs.neoforge.navigation.NativeNavigationTableAssemblies;
import com.vandorlabs.neoforge.navigation.NativeNavigationTableBlock;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/** Admit a complete stationary table and preserve the physical chip and module cells. */
final class NativeCopyNavigationTable {
    static void requireComplete(List<NativeRawCell> cells) {
        var tables = new LinkedHashMap<BlockPos, NativeRawCell>();
        for (var cell : cells) {
            if (cell.state.getBlock() instanceof NativeNavigationTableBlock) tables.put(cell.pos, cell);
        }
        if (tables.isEmpty()) return;

        var block = (NativeNavigationTableBlock) tables.values().iterator().next().state.getBlock();
        var facings = new LinkedHashMap<NavigationLayout.Cell, NavigationLayout.Facing>();
        var data = new LinkedHashMap<BlockPos, CompoundTag>();
        BlockPos chipStorage = null;
        BlockPos moduleStorage = null;
        for (var entry : tables.entrySet()) {
            var cell = entry.getValue();
            var raw = cell.tile();
            require(cell.state.getBlock() == block && raw != null && raw.getBoolean("NavigationFormed")
                && !raw.contains("NativeNavigationVisual"), "Copy requires a complete stationary Navigation Table");
            facings.put(layoutCell(cell.pos), NavigationLayout.Facing.valueOf(facing(cell).name()));
            data.put(cell.pos, raw);
            if (raw.contains("NavigationChip")) {
                require(chipStorage == null, "Multiple physical Navigation Chips in one table");
                chipStorage = cell.pos;
            }
            if (raw.contains("NavigationModule")) {
                require(moduleStorage == null, "Multiple physical Navigation Modules in one table");
                moduleStorage = cell.pos;
            }
        }

        var first = facings.values().iterator().next();
        if (block.kind == NavigationLayout.Kind.ANGLED) {
            require(facings.values().stream().allMatch(face -> face == first), "Angled Navigation Table facings disagree");
        }
        var layout = block.kind == NavigationLayout.Kind.FLAT
            ? NavigationLayout.mixedFlat(facings, true)
            : NavigationLayout.build(block.kind, first, facings.keySet(), true);
        var owner = pos(layout.chipOwner);
        var origin = pos(layout.originCell);
        var bay = tables.keySet().stream().filter(at -> !at.equals(owner))
            .min(NativeNavigationTableAssemblies.mountingOrder(owner)).orElse(null);
        require(bay != null || moduleStorage == null, "Navigation Table has a module without a bay");
        var bayFace = bay == null ? facing(tables.get(owner))
            : NativeNavigationTableAssemblies.exposedBayFace(bay, facing(tables.get(bay)), tables.keySet());
        var common = data.get(owner);
        for (var entry : data.entrySet()) {
            var at = entry.getKey();
            var raw = entry.getValue();
            require(tables.get(at).state.getValue(NativeNavigationTableBlock.CONNECTIONS) == layout.tiles.get(layoutCell(at)).connections
                && raw.getInt("NavigationWidth") == layout.width && raw.getInt("NavigationDepth") == layout.depth
                && raw.getInt("NavigationFacing") == Direction.valueOf(layout.facing.name()).get3DDataValue()
                && raw.getInt("NavigationModuleFacing") == bayFace.get3DDataValue()
                && raw.getBoolean("NavigationBay") == at.equals(bay)
                && raw.getInt("NavigationTexture") == common.getInt("NavigationTexture")
                && raw.getBoolean("NavigationExplicit") == common.getBoolean("NavigationExplicit")
                && Arrays.equals(raw.getIntArray("NavigationChannels"), common.getIntArray("NavigationChannels")),
                "Inconsistent saved Navigation Table layout or settings");
            requireOffset(raw, "NavigationOwner", at, owner);
            requireOffset(raw, "NavigationOrigin", at, origin);
            requireOffset(raw, "NavigationChipStorage", at, chipStorage == null ? owner : chipStorage);
            requireOffset(raw, "NavigationModuleStorage", at, moduleStorage == null ? bay == null ? owner : bay : moduleStorage);
        }
    }

    static Set<BlockPos> boundary(List<NativeRawCell> cells) {
        var members = new HashSet<BlockPos>();
        for (var cell : cells) if (cell.state.getBlock() instanceof NativeNavigationTableBlock) members.add(cell.pos);
        var result = new HashSet<BlockPos>();
        for (var pos : members) for (var side : Direction.Plane.HORIZONTAL) result.add(pos.relative(side));
        result.removeAll(members);
        return Set.copyOf(result);
    }

    static void requireIsolated(ServerLevel world, Set<BlockPos> boundary) {
        for (var pos : boundary) {
            require(world.hasChunkAt(pos) && !(world.getBlockState(pos).getBlock() instanceof NativeNavigationTableBlock),
                "Load the Navigation Table boundary and keep it clear of adjacent tables");
        }
    }

    private static NavigationLayout.Cell layoutCell(BlockPos pos) {
        return new NavigationLayout.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos pos(NavigationLayout.Cell cell) {
        return new BlockPos(cell.x, cell.y, cell.z);
    }

    private static Direction facing(NativeRawCell cell) {
        return cell.state.getValue(NativeNavigationTableBlock.FACING);
    }

    private static void requireOffset(CompoundTag raw, String key, BlockPos at, BlockPos target) {
        require(raw.contains(key, Tag.TAG_LONG) && raw.getLong(key) == target.subtract(at).asLong(),
            "Incomplete or inconsistent Navigation Table ownership in " + key);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private NativeCopyNavigationTable() {}
}
