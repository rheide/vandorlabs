package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import com.vandorlabs.neoforge.surface.NativeSurfaceEntity;
import com.vandorlabs.neoforge.surface.NativeSurfaceLayout;
import com.vandorlabs.neoforge.transfer.NativeCellTransactions;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Durable native handoff between loaded cells and the controller's owned capture. */
final class NativeTurntableTransfers {
    private static final int TILE_BYTES = 8 * 1024 * 1024;
    private static final NativeTransferSnapshot.Limits LIMITS =
            new NativeTransferSnapshot.Limits(16385, TILE_BYTES, 16 * 1024 * 1024, 48 * 1024 * 1024);

    record Receipt(boolean restoring, NativeTurntableCapture capture, List<BlockPos> changed) {}

    static void register() { NativeCellTransactions.register(FAMILY); }

    static NativeCellTransactions.Request prepare(NativeTurntableEntity root, ServerPlayer actor, boolean restoring, int quarter) {
        var world = actor.serverLevel();
        if (!root.installed(world) || !com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor, root)
                || NativeCellTransactions.busy(world, root.getBlockPos(), root.key())) {
            throw new IllegalArgumentException("The Turntable controller is unavailable or busy");
        }
        if (restoring != (root.capture() != null)) {
            throw new IllegalArgumentException(restoring ? "Scan the Surface first" : "Restore the captured contents before scanning");
        }
        var pos = root.getBlockPos();
        var capture = restoring ? root.capture()
                : NativeTurntableCapture.prepare(world, pos, root.key(), root.facing(), root.settings().height, actor);
        var endpoint = restoring ? cells(capture.atHeading(quarter)) : Map.<BlockPos, NativeRawCell>of();
        var originals = cells(capture.blocks());
        capture.prism().requireLoaded(world);

        var rootBefore = root.saveWithFullMetadata(world.registryAccess());
        var rootAfter = rootBefore.copy();
        if (restoring) rootAfter.remove(NativeTurntableEntity.CAPTURE);
        else rootAfter.put(NativeTurntableEntity.CAPTURE, capture.save());
        var before = new ArrayList<NativeRawCell>();
        var after = new ArrayList<NativeRawCell>();
        before.add(new NativeRawCell(pos, root.getBlockState(), rootBefore, TILE_BYTES));
        after.add(new NativeRawCell(pos, root.getBlockState(), rootAfter, TILE_BYTES));
        var held = new HashSet<>(capture.surface().cells);
        held.add(pos);
        for (var p : capture.prism().cells()) {
            held.add(p);
            if (restoring) {
                var current = world.getBlockState(p);
                if (!current.isAir() || world.getBlockEntity(p) != null) {
                    throw new IllegalArgumentException("Restore space occupied at " + p.toShortString());
                }
                before.add(new NativeRawCell(p, current, null, TILE_BYTES));
                after.add(endpoint.get(p));
            } else {
                var original = originals.get(p);
                before.add(original);
                after.add(original.state.isAir() ? original : new NativeRawCell(p, Blocks.AIR.defaultBlockState(), null, TILE_BYTES));
            }
        }
        var recipe = new CompoundTag();
        recipe.putBoolean("Restore", restoring);
        recipe.putInt("Heading", restoring ? Math.floorMod(quarter, 4) : 0);
        return new NativeCellTransactions.Request(pos, root.key(), new NativeTransferSnapshot(before, LIMITS),
                new NativeTransferSnapshot(after, LIMITS), held, recipe);
    }

    /** Null is a pending admission while recovery or disk capacity is unavailable. */
    static NativeCellTransactions.Operation<Receipt> submit(ServerLevel world, ServerPlayer actor,
                                                             NativeCellTransactions.Request request) {
        return NativeCellTransactions.submit(world, FAMILY, actor, request);
    }

    private static Map<BlockPos, NativeRawCell> cells(NativeTransferSnapshot snapshot) {
        var indexed = new HashMap<BlockPos, NativeRawCell>();
        for (var cell : snapshot.cells()) indexed.put(cell.pos, cell);
        return indexed;
    }

    private static Receipt decode(HolderLookup.Provider registries, NativeCellTransactions.Header header,
                                  NativeTransferSnapshot before, NativeTransferSnapshot after,
                                  Set<BlockPos> held, CompoundTag raw) {
        if (!raw.getAllKeys().equals(Set.of("Restore", "Heading"))
                || !raw.contains("Restore", Tag.TAG_BYTE) || raw.getByte("Restore") < 0 || raw.getByte("Restore") > 1
                || !raw.contains("Heading", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Malformed Turntable receipt");
        }
        boolean restoring = raw.getBoolean("Restore");
        int quarter = raw.getInt("Heading");
        if (quarter < 0 || quarter > 3 || !restoring && quarter != 0) {
            throw new IllegalArgumentException("Invalid Turntable receipt heading");
        }
        var original = before.cells().getFirst();
        var next = after.cells().getFirst();
        if (!original.pos.equals(header.root()) || !next.pos.equals(header.root())
                || !(original.state.getBlock() instanceof NativeTurntableBlock) || original.state != next.state
                || !original.hasTile() || !next.hasTile()) {
            throw new IllegalArgumentException("Missing Turntable controller in transfer");
        }
        var owned = (restoring ? original : next).tile();
        var unowned = (restoring ? next : original).tile();
        if (!owned.hasUUID(NativeTurntableEntity.KEY) || !header.owner().equals(owned.getUUID(NativeTurntableEntity.KEY))
                || !owned.contains(NativeTurntableEntity.CAPTURE, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid Turntable capture owner");
        }
        var capture = NativeTurntableCapture.read(registries, owned.getCompound(NativeTurntableEntity.CAPTURE), header.root());
        owned.remove(NativeTurntableEntity.CAPTURE);
        if (!owned.equals(unowned) || !capture.surface().cells.contains(header.root().relative(original.state.getValue(NativeTurntableBlock.FACING)))) {
            throw new IllegalArgumentException("Turntable controller fields or mounting changed");
        }
        var expectedHeld = new HashSet<>(capture.surface().cells);
        expectedHeld.add(header.root());
        expectedHeld.addAll(capture.prism().cells());
        if (!held.equals(expectedHeld) || before.cells().size() != capture.blocks().cells().size() + 1) {
            throw new IllegalArgumentException("Incomplete Turntable receipt footprint");
        }
        var payload = cells(restoring ? capture.atHeading(quarter) : capture.blocks());
        var changed = new ArrayList<BlockPos>();
        changed.add(header.root());
        for (int i = 1; i < before.cells().size(); i++) {
            var source = before.cells().get(i);
            var target = after.cells().get(i);
            var cell = payload.get(source.pos);
            var empty = restoring ? source : target;
            if (cell == null || !cell.identical(restoring ? target : source) || !empty.state.isAir() || empty.hasTile()) {
                throw new IllegalArgumentException("Turntable receipt block differs from the captured owner");
            }
            if (!source.identical(target)) changed.add(source.pos);
        }
        return new Receipt(restoring, capture, List.copyOf(changed));
    }

    private static final NativeCellTransactions.Family<Receipt> FAMILY = new NativeCellTransactions.Family<>() {
        @Override public ResourceLocation id() { return NativeTurntableBindings.ID; }
        @Override public NativeTransferSnapshot.Limits limits() { return LIMITS; }
        @Override public Receipt readRecipe(HolderLookup.Provider registries, NativeCellTransactions.Header header,
                                             NativeTransferSnapshot before, NativeTransferSnapshot after,
                                             Set<BlockPos> held, CompoundTag encoded) {
            return decode(registries, header, before, after, held, encoded);
        }

        @Override public void authorize(ServerLevel world, NativeCellTransactions.Header header,
                                        ServerPlayer actor, Receipt recipe) {
            var pos = header.root();
            var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !(chunk.getBlockEntities().get(pos) instanceof NativeTurntableEntity root)
                    || !root.installed(world) || !root.key().equals(header.owner())
                    || actor.distanceToSqr(Vec3.atCenterOf(pos)) > 64) {
                throw new IllegalArgumentException("The Turntable controller or authorization changed");
            }
            var capture = recipe.capture();
            capture.prism().requireLoaded(world);
            var bounds = capture.prism().bounds();
            if (!world.getEntities(null, bounds).isEmpty() || actor.getBoundingBox().intersects(bounds)) {
                throw new IllegalArgumentException("Clear all entities and players from the Turntable volume");
            }
            permission(world, header, actor, pos);
            if (!recipe.restoring()) {
                var current = NativeSurfaceLayout.connected(world, pos.relative(root.facing()), p -> true, true);
                if (!current.equals(Set.copyOf(capture.surface().cells))) {
                    throw new IllegalArgumentException("The Turntable Surface footprint changed");
                }
                for (var p : current) {
                    permission(world, header, actor, p);
                    if (!(world.getBlockEntity(p) instanceof NativeSurfaceEntity tile)
                            || tile.normal() != capture.surface().normal || !tile.canClaim(pos, root.key())) {
                        throw new IllegalArgumentException("The Turntable Surface is claimed or unavailable");
                    }
                }
            }
            for (var p : capture.prism().cells()) permission(world, header, actor, p);
        }

        @Override public BlockEntity stage(ServerLevel world, NativeCellTransactions.Header header,
                                          NativeRawCell cell, Receipt recipe) {
            return cell.stage(world, tile -> tile instanceof NativeTurntableEntity owner
                    ? owner.available() : NativeTurntableCapture.supportsTile(tile), this::same);
        }

        @Override public boolean same(CompoundTag expected, CompoundTag actual) {
            if (Objects.equals(expected, actual)) return true;
            if (expected == null || actual == null) return false;
            return normalize(expected).equals(normalize(actual));
        }

        @Override public void publish(ServerLevel world, NativeCellTransactions.Header header,
                                     NativeRawCell before, NativeRawCell after, BlockEntity staged,
                                     NativeTransferHolds.Lease lease, Receipt recipe) {
            if (world.getBlockState(after.pos) != after.state) {
                try (var write = lease.allowOnce(after.pos)) {
                    if (!world.setBlock(after.pos, after.state, Block.UPDATE_KNOWN_SHAPE)) {
                        throw new IllegalStateException("Turntable block publication refused");
                    }
                }
            }
            if (after.hasTile()) {
                if (staged == null) throw new IllegalStateException("Missing prepared Turntable tile");
                world.removeBlockEntity(after.pos);
                world.setBlockEntity(staged);
                staged.setChanged();
            } else if (world.getBlockEntity(after.pos) != null) {
                throw new IllegalStateException("An unexpected tile survived Turntable publication");
            }
        }

        @Override public void released(ServerLevel world, NativeCellTransactions.Header header,
                                      boolean rollback, String error, Receipt recipe) {
            for (var p : recipe.changed()) {
                if (!world.hasChunkAt(p)) continue;
                world.invalidateCapabilities(p);
                var state = world.getBlockState(p);
                world.sendBlockUpdated(p, state, state, Block.UPDATE_CLIENTS);
            }
        }
    };

    private static void permission(ServerLevel world, NativeCellTransactions.Header header, ServerPlayer player, BlockPos pos) {
        if (!world.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, player.getMainHandItem())
                || NativeRampHeldOwners.foreignToCellReservation(world, pos)
                || NativeTransferHolds.reserved(world, pos) && !NativeCellTransactions.reservedBy(world, header, pos)) {
            throw new IllegalArgumentException("Turntable transfer permission or ownership refused");
        }
    }

    private static CompoundTag normalize(CompoundTag raw) {
        var canonical = raw.copy();
        if (canonical.contains("keepPacked", Tag.TAG_BYTE) && !canonical.getBoolean("keepPacked")) canonical.remove("keepPacked");
        if (canonical.contains("NeoForgeData", Tag.TAG_COMPOUND) && canonical.getCompound("NeoForgeData").isEmpty()) {
            canonical.remove("NeoForgeData");
        }
        return canonical;
    }

    private NativeTurntableTransfers() {}
}
