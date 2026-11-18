package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import com.vandorlabs.neoforge.surface.NativeSurfaceEntity;
import com.vandorlabs.neoforge.surface.NativeSurfaceLayout;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeSurfacePrism;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Read-only before image for the first, block-only Turntable transfer slice. */
public record NativeTurntableCapture(NativeSurfaceLayout surface, NativeSurfacePrism prism,
                                     NativeTransferSnapshot blocks) {
    private static final int MAX_BLOCKS = 4096;
    private static final int MAX_TILE_BYTES = 2 * 1024 * 1024;
    private static final NativeTransferSnapshot.Limits LIMITS =
            new NativeTransferSnapshot.Limits(16384, MAX_TILE_BYTES, 8 * 1024 * 1024, 32 * 1024 * 1024);

    public static NativeTurntableCapture prepare(ServerLevel world, BlockPos controller, UUID key,
                                                 Direction facing, int height, ServerPlayer actor) {
        if (!world.getServer().isSameThread() || actor == null || actor.serverLevel() != world
                || !actor.isAlive() || actor.isSpectator()) {
            throw new IllegalArgumentException("Turntable capture requires its physical server and an active player");
        }
        var seed = controller.relative(facing);
        var members = NativeSurfaceLayout.connected(world, seed, pos -> true, true);
        var surface = new NativeSurfaceLayout(members, world.getBlockState(seed).getValue(NativeSurfaceBlock.FACING), false);
        var prism = NativeSurfacePrism.of(surface, height);
        prism.requireLoaded(world);
        if (prism.contains(controller)) throw new IllegalArgumentException("Keep the controller outside the scan volume");
        permission(world, actor, controller);
        for (var pos : surface.cells) {
            permission(world, actor, pos);
            if (!(world.getBlockEntity(pos) instanceof NativeSurfaceEntity tile) || !tile.canClaim(controller, key)) {
                throw new IllegalArgumentException("The Surface is already claimed or unavailable");
            }
        }
        var bounds = prism.bounds();
        if (!world.getEntities(actor, bounds).isEmpty() || actor.getBoundingBox().intersects(bounds)) {
            throw new IllegalArgumentException("Clear entities and players from the scan volume");
        }

        var cells = new ArrayList<NativeRawCell>();
        int nonAir = 0;
        long tileBytes = 0;
        for (var cursor : BlockPos.betweenClosed(prism.min(), prism.max())) {
            var pos = cursor.immutable();
            permission(world, actor, pos);
            var state = world.getBlockState(pos);
            var tile = world.getBlockEntity(pos);
            if (!state.isAir()) {
                if (++nonAir > MAX_BLOCKS) throw new IllegalArgumentException("Turntable payload exceeds 4096 blocks");
                requireSupported(state);
                if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, state, actor)).isCanceled()) {
                    throw new IllegalArgumentException("A protection rule prevented scanning at " + pos.toShortString());
                }
            }
            var saved = tile == null ? null : tile.saveWithFullMetadata(world.registryAccess());
            if (saved != null) {
                tileBytes += saved.sizeInBytes();
                if (tileBytes > LIMITS.bytes()) throw new IllegalArgumentException("Turntable tile data exceeds 8 MiB");
            }
            var cell = new NativeRawCell(pos, state, saved, MAX_TILE_BYTES);
            cell.stage(world, candidate -> candidate.getClass() == SignBlockEntity.class
                    || candidate.getClass() == HangingSignBlockEntity.class, Objects::equals);
            cells.add(cell);
        }
        var snapshot = new NativeTransferSnapshot(cells, LIMITS);
        if (!snapshot.matches(world, Objects::equals)) {
            throw new IllegalArgumentException("World contents changed while scanning");
        }
        return new NativeTurntableCapture(surface, prism, snapshot);
    }

    private static void requireSupported(BlockState state) {
        var block = state.getBlock();
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        boolean simple = block.getClass() == net.minecraft.world.level.block.Block.class
                || block instanceof RotatedPillarBlock || block instanceof SlabBlock || block instanceof StairBlock
                || block instanceof net.minecraft.world.level.block.SignBlock
                || block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock
                || block instanceof net.minecraft.world.level.block.WallHangingSignBlock;
        if (!id.getNamespace().equals("minecraft") || !simple || !state.getFluidState().isEmpty()) {
            throw new IllegalArgumentException("No Turntable capture adapter for " + id);
        }
    }

    private static void permission(ServerLevel world, ServerPlayer actor, BlockPos pos) {
        if (!world.mayInteract(actor, pos) || !actor.mayUseItemAt(pos, Direction.UP, actor.getMainHandItem())
                || NativeTransferHolds.reserved(world, pos)
                || com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.foreignToCellReservation(world, pos)) {
            throw new IllegalArgumentException("Turntable scan permission or ownership refused");
        }
    }
}
