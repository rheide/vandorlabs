package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import com.vandorlabs.neoforge.surface.NativeSurfaceEntity;
import com.vandorlabs.neoforge.surface.NativeSurfaceLayout;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeSurfacePrism;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.turntable.TurntableGeometry;
import com.vandorlabs.turntable.TurntableSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Surface", surface.save());
        tag.putInt("Height", depth());
        tag.put("Cells", blocks.write());
        return tag;
    }

    private int depth() {
        var extent = prism.max().subtract(prism.min());
        return 1 + surface.normal.getAxis().choose(extent.getX(), extent.getY(), extent.getZ());
    }

    public static NativeTurntableCapture read(HolderLookup.Provider registries, CompoundTag tag, BlockPos controller) {
        if (!tag.getAllKeys().equals(Set.of("Surface", "Height", "Cells"))
                || !tag.contains("Surface", Tag.TAG_COMPOUND) || !tag.contains("Cells", Tag.TAG_COMPOUND)
                || !tag.contains("Height", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid Turntable capture envelope");
        }
        var surface = NativeSurfaceLayout.read(tag.getCompound("Surface"), controller, false);
        var prism = NativeSurfacePrism.of(surface, tag.getInt("Height"));
        var snapshot = NativeTransferSnapshot.read(registries, tag.getCompound("Cells"), LIMITS, ignored -> Map.of());
        var extent = prism.max().subtract(prism.min());
        int volume = (extent.getX() + 1) * (extent.getY() + 1) * (extent.getZ() + 1);
        if (snapshot.cells().size() != volume || prism.contains(controller)) {
            throw new IllegalArgumentException("Incomplete Turntable before image");
        }
        int nonAir = 0;
        for (var cell : snapshot.cells()) {
            if (!prism.contains(cell.pos)) throw new IllegalArgumentException("Turntable cell outside saved prism");
            if (!cell.state.isAir()) {
                if (++nonAir > MAX_BLOCKS) throw new IllegalArgumentException("Turntable payload exceeds 4096 blocks");
                requireSupported(cell.state);
            }
        }
        return new NativeTurntableCapture(surface, prism, snapshot);
    }

    public static NativeTurntableCapture prepare(ServerLevel world, BlockPos controller, UUID key,
                                                 Direction facing, int height, ServerPlayer actor) {
        if (!world.getServer().isSameThread() || actor == null || actor.serverLevel() != world
                || !actor.isAlive() || actor.isSpectator()) {
            throw new IllegalArgumentException("Turntable capture requires its physical server and an active player");
        }
        if (height < TurntableSettings.MIN_HEIGHT || height > TurntableSettings.MAX_HEIGHT) {
            throw new IllegalArgumentException("Scan height must be between 1 and 16 blocks");
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
            cell.stage(world, NativeTurntableCapture::supportsTile, Objects::equals);
            cells.add(cell);
        }
        var snapshot = new NativeTransferSnapshot(cells, LIMITS);
        if (!snapshot.matches(world, Objects::equals)) {
            throw new IllegalArgumentException("World contents changed while scanning");
        }
        return new NativeTurntableCapture(surface, prism, snapshot);
    }

    /** Prepare an unpublished endpoint. The transfer still needs clearance and durable ownership checks. */
    public NativeTransferSnapshot atHeading(int quarter) {
        int heading = Math.floorMod(quarter, 4);
        if (heading == 0) return blocks;
        var layout = surface.geometry;
        var result = new ArrayList<NativeRawCell>(blocks.cells().size());
        for (var cell : blocks.cells()) {
            int[] target = TurntableGeometry.rotateCell(cell.pos.getX(), cell.pos.getY(), cell.pos.getZ(),
                    layout.pivotX, layout.pivotY, layout.pivotZ, layout.face, heading);
            var pos = new BlockPos(target[0], target[1], target[2]);
            if (!prism.contains(pos)) throw new IllegalArgumentException("Turntable endpoint leaves its scan volume");
            var raw = cell.tile();
            if (raw != null) {
                raw.putInt("x", pos.getX());
                raw.putInt("y", pos.getY());
                raw.putInt("z", pos.getZ());
            }
            result.add(new NativeRawCell(pos, rotatedState(cell.state, heading), raw, MAX_TILE_BYTES));
        }
        result.sort(Comparator.comparing(cell -> cell.pos));
        return new NativeTransferSnapshot(result, LIMITS);
    }

    private BlockState rotatedState(BlockState state, int heading) {
        var face = surface.normal;
        if (face.getAxis() == Direction.Axis.Y) {
            return state.rotate(switch (Math.floorMod(heading * face.getStepY(), 4)) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            });
        }
        if (state.getBlock() instanceof RotatedPillarBlock) {
            var axis = state.getValue(RotatedPillarBlock.AXIS);
            double[] vector = TurntableGeometry.rotate(axis == Direction.Axis.X ? 1 : 0,
                    axis == Direction.Axis.Y ? 1 : 0, axis == Direction.Axis.Z ? 1 : 0,
                    surface.geometry.face, heading * 90);
            return state.setValue(RotatedPillarBlock.AXIS, Direction.getNearest(vector[0], vector[1], vector[2]).getAxis());
        }
        if (state.isAir() || state.getBlock().getClass() == net.minecraft.world.level.block.Block.class) return state;
        throw new IllegalArgumentException("No native sideways orientation for the captured block");
    }

    static boolean supportsTile(BlockEntity candidate) {
        return candidate.getClass() == SignBlockEntity.class || candidate.getClass() == HangingSignBlockEntity.class;
    }

    static void requireSupported(BlockState state) {
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
