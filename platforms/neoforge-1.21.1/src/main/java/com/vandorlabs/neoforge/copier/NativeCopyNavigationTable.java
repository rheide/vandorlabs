package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.navigation.NativeNavigationTableBlock;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/** Copy admission for a complete stationary single-cell table and its local physical chip. */
final class NativeCopyNavigationTable {
    private static final List<String> OFFSETS = List.of(
        "NavigationOwner", "NavigationOrigin", "NavigationChipStorage", "NavigationModuleStorage");

    static void requireStandalone(List<NativeRawCell> cells) {
        boolean found = false;
        for (var cell : cells) {
            if (!(cell.state.getBlock() instanceof NativeNavigationTableBlock)) continue;
            var data = cell.tile();
            var facing = cell.state.getValue(NativeNavigationTableBlock.FACING);
            if (found || data == null || cell.state.getValue(NativeNavigationTableBlock.CONNECTIONS) != 0
                || !data.getBoolean("NavigationFormed") || data.getInt("NavigationWidth") != 1
                || data.getInt("NavigationDepth") != 1 || data.getBoolean("NavigationBay")
                || data.getInt("NavigationFacing") != facing.get3DDataValue()
                || data.getInt("NavigationModuleFacing") != facing.get3DDataValue()
                || data.contains("NavigationModule") || data.contains("NativeNavigationVisual")) {
                throw new IllegalArgumentException("Copy requires an isolated one-cell Navigation Table with a local chip slot");
            }
            for (String field : OFFSETS) {
                if (!data.contains(field, Tag.TAG_LONG) || data.getLong(field) != BlockPos.ZERO.asLong()) {
                    throw new IllegalArgumentException("Navigation Table has nonlocal or unknown saved ownership");
                }
            }
            found = true;
        }
    }

    static Set<BlockPos> boundary(List<NativeRawCell> cells) {
        var result = new HashSet<BlockPos>();
        for (var cell : cells) {
            if (!(cell.state.getBlock() instanceof NativeNavigationTableBlock)) continue;
            for (var side : Direction.Plane.HORIZONTAL) result.add(cell.pos.relative(side));
        }
        return Set.copyOf(result);
    }

    static void requireIsolated(ServerLevel world, Set<BlockPos> boundary) {
        for (var pos : boundary) {
            if (!world.hasChunkAt(pos) || world.getBlockState(pos).getBlock() instanceof NativeNavigationTableBlock) {
                throw new IllegalArgumentException("Load the Navigation Table boundary and keep it clear of adjacent tables");
            }
        }
    }

    private NativeCopyNavigationTable() {}
}
