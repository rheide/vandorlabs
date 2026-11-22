package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** Loaded-only discovery rooted at the Vehicle Controller. */
final class NativeVehicleScan {
    record Result(NativeTransferSnapshot physical, NativeTransferSnapshot local, BlockPos pilot, Direction facing) {}

    static Result scan(ServerLevel world, BlockPos controller) {
        if (!world.getServer().isSameThread()) throw new IllegalStateException("Vehicle scan requires its server thread");
        var visited = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        var cells = new ArrayList<NativeRawCell>();
        BlockPos pilot = null;
        Direction facing = null;
        long tileBytes = 0;
        visited.add(controller);
        pending.add(controller);
        while (!pending.isEmpty()) {
            var pos = pending.removeFirst();
            if (!NativeVehicleStructure.within(pos.subtract(controller)) || cells.size() == NativeVehicleStructure.LIMITS.cells()) {
                throw new IllegalArgumentException("Disconnect the hull from terrain or reduce its size");
            }
            var chunk = requireLoaded(world, pos);
            var state = chunk.getBlockState(pos);
            NativeVehicleStructure.requireSupported(state);
            if (state.getDestroySpeed(world, pos) < 0) throw new IllegalArgumentException("The hull contains an immovable block at " + pos.toShortString());
            if (NativeVehicleStructure.pilot(state)) {
                if (pilot != null) throw new IllegalArgumentException("Use one Pilot Seat in the hull");
                pilot = pos.subtract(controller);
                facing = state.getValue(NativeSeatBlock.FACING);
            }
            var tile = world.getBlockEntity(pos);
            if (tile != null && !NativeVehicleStructure.supportsTile(tile)) {
                throw new IllegalArgumentException("Unsupported or retained vehicle data at " + pos.toShortString());
            }
            var raw = tile == null ? null : tile.saveWithFullMetadata(world.registryAccess());
            if (raw != null && (tileBytes += raw.sizeInBytes()) > NativeVehicleStructure.LIMITS.bytes()) {
                throw new IllegalArgumentException("Vehicle storage exceeds the capture byte bound");
            }
            var cell = new NativeRawCell(pos, state, raw, NativeVehicleStructure.LIMITS.tileBytes());
            try {
                NativeVehicleStructure.stage(cell, world);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Cannot carry " + state.getBlock().getName().getString()
                    + " at " + pos.toShortString() + ". " + invalid.getMessage(), invalid);
            }
            cells.add(cell);
            for (var part : NativeVehicleStructure.requiredParts(pos, state)) {
                if (visited.add(part)) pending.add(part);
            }
            for (var side : Direction.values()) {
                if (!NativeVehicleStructure.connectingFace(state, side)) continue;
                var other = pos.relative(side);
                if (visited.contains(other)) continue;
                var neighbor = requireLoaded(world, other).getBlockState(other);
                if (!neighbor.isAir() && NativeVehicleStructure.touches(state, neighbor, side) && visited.add(other)) {
                    pending.add(other);
                }
            }
        }
        var physical = new NativeTransferSnapshot(cells, NativeVehicleStructure.LIMITS);
        var local = NativeVehicleStructure.translate(physical, BlockPos.ZERO.subtract(controller), Rotation.NONE);
        NativeVehicleStructure.validate(local, pilot, facing, world.registryAccess());
        return new Result(physical, local, pilot, facing);
    }

    /** Checks for hull edits in the gap before the durable reservation becomes active. */
    static void requireBoundary(ServerLevel world, NativeTransferSnapshot physical, Set<BlockPos> hull) {
        for (var cell : physical.cells()) for (var side : Direction.values()) {
            if (!NativeVehicleStructure.connectingFace(cell.state, side)) continue;
            var neighboring = cell.pos.relative(side);
            if (hull.contains(neighboring)) continue;
            var state = requireLoaded(world, neighboring).getBlockState(neighboring);
            if (!state.isAir() && NativeVehicleStructure.touches(cell.state, state, side)) {
                throw new IllegalArgumentException("The vehicle hull connected to another block at " + neighboring.toShortString());
            }
        }
    }

    static LevelChunk requireLoaded(ServerLevel world, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos) || !world.getWorldBorder().isWithinBounds(pos)) {
            throw new IllegalArgumentException("Vehicle exceeds the world bounds at " + pos.toShortString());
        }
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) throw new IllegalArgumentException("Load the complete vehicle and its boundary first");
        return chunk;
    }

    static AABB bounds(NativeTransferSnapshot physical) {
        var cells = physical.cells();
        var bounds = new AABB(cells.getFirst().pos);
        for (int i = 1; i < cells.size(); i++) bounds = bounds.minmax(new AABB(cells.get(i).pos));
        return bounds;
    }

    private NativeVehicleScan() {}
}
