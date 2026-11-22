package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.builders.NativeBuilderProtection;
import com.vandorlabs.neoforge.builders.NativeBuilderTransfer;
import com.vandorlabs.neoforge.navigation.observer.NativeObserverProtection;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.transfer.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** The loaded hull and its entity exchange ownership through one durable cell/actor journal. */
@EventBusSubscriber(modid = "vandorlabs")
public final class NativeVehicleTransfer {
    private static final ResourceLocation FAMILY_ID = ResourceLocation.fromNamespaceAndPath("vandorlabs", "vehicle_transfer");
    private static final String ESCROW = "VandorLabsVehicleEscrow";
    private static final Set<String> RECIPE_FIELDS = Set.of("Schema", "Parking", "Pilot", "Facing", "Quarter", "Entity");
    private static final Set<String> MARKER_FIELDS = Set.of("Operation", "Owner", "Actor", "Root");
    private static final NativeTransferSnapshot.Limits LIMITS = NativeVehicleStructure.LIMITS;
    private static final Map<ServerLevel, Set<UUID>> PENDING_PARKS = new IdentityHashMap<>();
    private static final ThreadLocal<BreakProbe> PROBE = new ThreadLocal<>();
    private record BreakProbe(BlockEvent.BreakEvent event, NativeCellTransactions.Header header) {}

    public static void register() {
        NativeCellTransactions.register(FAMILY);
    }

    public static boolean holdsTerrain(ServerLevel world, BlockPos pos) {
        return NativeCellTransactions.reservedByFamily(world, FAMILY_ID, pos);
    }

    public static boolean holdsScreenChannels(ServerLevel world, BlockPos pos) {
        return NativeCellTransactions.reservedByFamily(world, FAMILY_ID, pos)
            || NativeCellTransactions.preparingByFamily(world, FAMILY_ID, pos);
    }

    public static void assemble(ServerPlayer player, BlockPos controller) {
        var world = player.serverLevel();
        try {
            requireEntry(world, player);
            NativeVehicleScan.requireLoaded(world, controller);
            if (!(world.getBlockEntity(controller) instanceof NativeVehicleControllerEntity root)
                || root.ownerId() == null || !NativeConfigurationAccess.canEditDialog(player, root) || !root.mayOperate(player)) {
                throw new IllegalArgumentException("The installed Vehicle Controller did not authorize assembly");
            }
            var hull = NativeVehicleScan.scan(world, controller, player.getUUID());
            var craft = create(world);
            craft.setOwner(root.ownerId());
            craft.setPos(controller.getX() + .5, controller.getY(), controller.getZ() + .5);
            craft.install(hull.local(), hull.pilot(), hull.facing());
            var empty = air(hull.physical());
            submit(world, player, controller, root.ownerId(), hull.physical(), empty,
                recipe(false, 0, hull.pilot(), hull.facing(), craft));
        } catch (RuntimeException failure) {
            refused(player, failure);
        }
    }

    public static void park(ServerPlayer player, NativeGroundVehicle craft) {
        var world = player.serverLevel();
        try {
            requireEntry(world, player);
            if (!mayPark(world, player, craft) || escrowed(craft)) {
                throw new IllegalArgumentException("Only the owner can park a settled, empty vehicle");
            }
            if (!craft.resources().armsReadyToPark()) throw new IllegalArgumentException("Deploy and settle all carried Arms before parking");
            var pos = anchor(craft);
            int turn = quarter(craft.getYRot());
            var placed = NativeVehicleStructure.translate(craft.snapshot(), pos, rotation(turn));
            var original = new ArrayList<NativeRawCell>(placed.cells().size());
            for (var cell : placed.cells()) {
                var current = NativeVehicleScan.requireLoaded(world, cell.pos).getBlockState(cell.pos);
                if (!current.isAir() || world.getBlockEntity(cell.pos) != null) {
                    throw new IllegalArgumentException("Parking is obstructed at " + cell.pos.toShortString());
                }
                original.add(new NativeRawCell(cell.pos, current, null, LIMITS.tileBytes()));
            }
            submit(world, player, pos, craft.owner(), new NativeTransferSnapshot(original, LIMITS), placed,
                recipe(true, turn, craft.pilotSeat(), craft.facing(), craft));
        } catch (RuntimeException failure) {
            refused(player, failure);
        }
    }

    private static void requireEntry(ServerLevel world, ServerPlayer player) {
        if (!world.getServer().isSameThread() || !player.isAlive() || player.isSpectator() || player.isRemoved() || PROBE.get() != null) {
            throw new IllegalArgumentException("Vehicle transfer requires an active player on the server thread");
        }
        if (world.noSave) throw new IllegalArgumentException("Enable world saving before moving vehicle ownership");
    }

    private static void submit(ServerLevel world, ServerPlayer actor, BlockPos root, UUID owner,
                               NativeTransferSnapshot before, NativeTransferSnapshot after, CompoundTag data) {
        var held = before.cells().stream().map(cell -> cell.pos).toList();
        var op = NativeCellTransactions.submit(world, FAMILY, actor,
            new NativeCellTransactions.Request(root, owner, before, after, held, data));
        if (op == null) throw new IllegalArgumentException("Vehicle transfer is waiting for journal capacity or recovery");
        if (data.getBoolean("Parking")) {
            PENDING_PARKS.computeIfAbsent(world, unused -> new HashSet<>()).add(data.getCompound("Entity").getUUID("UUID"));
        }
        actor.displayClientMessage(Component.literal(data.getBoolean("Parking") ? "Preparing vehicle parking" : "Preparing vehicle assembly"), true);
    }

    private static NativeTransferSnapshot air(NativeTransferSnapshot occupied) {
        var cells = new ArrayList<NativeRawCell>(occupied.cells().size());
        for (var cell : occupied.cells()) cells.add(new NativeRawCell(cell.pos, Blocks.AIR.defaultBlockState(), null, LIMITS.tileBytes()));
        return new NativeTransferSnapshot(cells, LIMITS);
    }

    private static boolean mayPark(ServerLevel world, ServerPlayer player, NativeGroundVehicle craft) {
        return craft.level() == world && !craft.isRemoved() && world.getEntity(craft.getUUID()) == craft
            && player.getUUID().equals(craft.owner()) && craft.canPark() && craft.getPassengers().isEmpty()
            && player.distanceToSqr(craft) <= 64;
    }

    static BlockPos anchor(NativeGroundVehicle craft) {
        return BlockPos.containing(craft.getX(), Math.round(craft.getY()), craft.getZ());
    }

    private static int quarter(float yaw) {
        int nearest = Math.round(yaw / 90);
        if (!Float.isFinite(yaw) || Math.abs(Mth.wrapDegrees(yaw - nearest * 90F)) > 12) {
            throw new IllegalArgumentException("Align the craft within 12 degrees of a cardinal heading before parking");
        }
        return Math.floorMod(nearest, 4);
    }

    private static Rotation rotation(int turn) {
        return switch (turn) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Invalid vehicle parking heading");
        };
    }

    private static NativeGroundVehicle create(ServerLevel world) {
        var craft = NativeVehicleBindings.VEHICLE.create(world);
        if (craft == null) throw new IllegalStateException("Missing native vehicle entity factory");
        return craft;
    }

    private static CompoundTag recipe(boolean parking, int turn, BlockPos pilot, Direction facing, NativeGroundVehicle craft) {
        var encoded = new CompoundTag();
        encoded.putInt("Schema", 1);
        encoded.putBoolean("Parking", parking);
        encoded.putInt("Quarter", turn);
        encoded.putLong("Pilot", pilot.asLong());
        encoded.putInt("Facing", facing.get3DDataValue());
        encoded.put("Entity", canonicalActor(save(craft)));
        return encoded;
    }

    private static CompoundTag save(NativeGroundVehicle craft) {
        return craft.saveWithoutId(new CompoundTag());
    }

    private static final class Receipt {
        final boolean parking;
        final UUID identity;
        final ChunkPos entityChunk;
        final CompoundTag original;
        final NativeTransferSnapshot occupied;
        final Set<BlockPos> held;
        NativeEntityCheckpoint checkpoint;
        boolean checkpointRollback;
        NativeGroundVehicle staged;
        Future<Void> retirement;
        boolean retirementRollback;

        Receipt(boolean parking, UUID identity, ChunkPos entityChunk, CompoundTag original,
                NativeTransferSnapshot occupied, Set<BlockPos> held) {
            this.parking = parking;
            this.identity = identity;
            this.entityChunk = entityChunk;
            this.original = original.copy();
            this.occupied = occupied;
            this.held = held;
        }

        boolean expectsActor(boolean rollback) {
            return parking == rollback;
        }

        NativeEntityCheckpoint checkpoint(ServerLevel world, NativeCellTransactions.Header header, boolean rollback) {
            if (checkpoint == null || checkpointRollback != rollback) {
                checkpointRollback = rollback;
                checkpoint = new NativeEntityCheckpoint(world, List.of(entityChunk), () -> {
                    if (!NativeCellTransactions.reservedBy(world, header, header.root())) {
                        throw new IllegalStateException("Vehicle actor checkpoint lost its cell authority");
                    }
                }, 1, (chunk, saved) -> requireActorRecord(header, this, rollback, saved));
            }
            return checkpoint;
        }

        boolean retire(ServerLevel world, NativeCellTransactions.Header header, boolean rollback) {
            if (!parking) return true;
            if (retirement == null || retirementRollback != rollback) {
                try {
                    retirement = NativeVehicleRetirements.checkpoint(world, identity, header.owner(), header.operation(), rollback);
                    retirementRollback = rollback;
                } catch (RejectedExecutionException busy) {
                    return false;
                }
            }
            if (!retirement.isDone()) return false;
            try {
                retirement.get();
                return true;
            } catch (ExecutionException unavailable) {
                retirement = null;
                throw new CompletionException(unavailable.getCause());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            }
        }
    }

    private static Receipt decode(HolderLookup.Provider registries, NativeCellTransactions.Header header,
                                  NativeTransferSnapshot before, NativeTransferSnapshot after, Set<BlockPos> held,
                                  CompoundTag encoded) {
        if (!encoded.getAllKeys().equals(RECIPE_FIELDS) || !encoded.contains("Schema", Tag.TAG_INT)
            || encoded.getInt("Schema") != 1 || !encoded.contains("Parking", Tag.TAG_BYTE)
            || encoded.getByte("Parking") < 0 || encoded.getByte("Parking") > 1 || !encoded.contains("Pilot", Tag.TAG_LONG)
            || !encoded.contains("Facing", Tag.TAG_INT) || !encoded.contains("Quarter", Tag.TAG_INT)
            || !encoded.contains("Entity", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Malformed Vehicle transfer recipe");
        }
        var parking = encoded.getBoolean("Parking");
        int turn = encoded.getInt("Quarter");
        var rotation = rotation(turn);
        var pilot = BlockPos.of(encoded.getLong("Pilot"));
        int heading = encoded.getInt("Facing");
        if (heading < 2 || heading > 5 || !parking && turn != 0) throw new IllegalArgumentException("Invalid vehicle receipt heading");
        var direction = Direction.from3DDataValue(heading);
        var original = encoded.getCompound("Entity");
        if (!original.hasUUID("UUID") || original.contains("Passengers") || original.contains("id")
            || original.getCompound("NeoForgeData").contains(ESCROW)) {
            throw new IllegalArgumentException("Invalid Vehicle entity owner receipt");
        }
        var point = original.getList("Pos", Tag.TAG_DOUBLE);
        var angle = original.getList("Rotation", Tag.TAG_FLOAT);
        if (point.size() != 3 || angle.size() != 2 || !Double.isFinite(point.getDouble(0))
            || !Double.isFinite(point.getDouble(1)) || !Double.isFinite(point.getDouble(2)) || quarter(angle.getFloat(0)) != turn
            || !Float.isFinite(angle.getFloat(1))) {
            throw new IllegalArgumentException("Malformed Vehicle actor pose receipt");
        }
        var position = BlockPos.containing(point.getDouble(0), Math.round(point.getDouble(1)), point.getDouble(2));
        if (!position.equals(header.root()) || !parking && (point.getDouble(0) != position.getX() + .5
            || point.getDouble(1) != position.getY() || point.getDouble(2) != position.getZ() + .5 || angle.getFloat(0) != 0)) {
            throw new IllegalArgumentException("Vehicle actor pose differs from its journal anchor");
        }
        var occupied = parking ? after : before;
        var empty = parking ? before : after;
        if (!held.equals(new HashSet<>(occupied.cells().stream().map(cell -> cell.pos).toList()))) {
            throw new IllegalArgumentException("Vehicle journal holds cells outside the occupied hull");
        }
        for (var cell : empty.cells()) if (!cell.state.isAir() || cell.hasTile()) {
            throw new IllegalArgumentException("Vehicle parking and assembly require an empty counterpart");
        }
        var hull = NativeVehicleStructure.translate(occupied, BlockPos.ZERO.subtract(header.root()), Rotation.NONE);
        var local = NativeVehicleStructure.translate(hull, BlockPos.ZERO, rotation(Math.floorMod(-turn, 4)));
        NativeVehicleStructure.validate(local, pilot, direction, registries);
        var controller = local.cells().stream().filter(cell -> cell.pos.equals(BlockPos.ZERO)).findFirst().orElseThrow().tile();
        var body = original.getCompound("Vehicle");
        if (!controller.hasUUID("VehicleOwner") || !controller.getUUID("VehicleOwner").equals(header.owner())
            || !body.hasUUID("Owner") || !body.getUUID("Owner").equals(header.owner())
            || !body.contains("Pilot", Tag.TAG_LONG) || body.getLong("Pilot") != pilot.asLong()
            || !body.contains("Facing", Tag.TAG_INT) || body.getInt("Facing") != heading
            || !body.getCompound("Blocks").equals(local.write())) {
            throw new IllegalArgumentException("Vehicle entity hull and exclusive block owner differ");
        }
        NativeGroundVehicle.readSavedVehicle(original, registries);
        var reproduced = NativeVehicleStructure.translate(local, header.root(), rotation);
        for (int i = 0; i < reproduced.cells().size(); i++) if (!reproduced.cells().get(i).identical(occupied.cells().get(i))) {
            throw new IllegalArgumentException("Vehicle terrain receipt is not a reversible hull");
        }
        return new Receipt(parking, original.getUUID("UUID"), new ChunkPos(position), original, occupied, held);
    }

    private static final NativeCellTransactions.Family<Receipt> FAMILY = new NativeCellTransactions.Family<>() {
        @Override public ResourceLocation id() { return FAMILY_ID; }
        @Override public NativeTransferSnapshot.Limits limits() { return LIMITS; }

        @Override public Receipt readRecipe(HolderLookup.Provider registries, NativeCellTransactions.Header header,
                                           NativeTransferSnapshot before, NativeTransferSnapshot after,
                                           Set<BlockPos> held, CompoundTag encoded) {
            return decode(registries, header, before, after, held, encoded);
        }

        @Override public void authorize(ServerLevel world, NativeCellTransactions.Header header, ServerPlayer actor, Receipt receipt) {
            var root = header.root();
            NativeVehicleScan.requireLoaded(world, root);
            NativeGroundVehicle craft = null;
            if (receipt.parking) {
                if (!(world.getEntity(receipt.identity) instanceof NativeGroundVehicle current)
                    || !mayPark(world, actor, current) || !current.resources().armsReadyToPark() || !sameActor(receipt, current, null)) {
                    throw new IllegalArgumentException("The vehicle or its owner changed before parking");
                }
                craft = current;
            } else {
                if (!(world.getBlockEntity(root) instanceof NativeVehicleControllerEntity controller)
                    || controller.isRemoved() || !header.owner().equals(controller.ownerId()) || !controller.mayOperate(actor)
                    || actor.distanceToSqr(root.getCenter()) > 64) {
                    throw new IllegalArgumentException("The Vehicle Controller or its authorization changed");
                }
                NativeVehicleScan.requireBoundary(world, receipt.occupied, receipt.held);
            }
            var volume = NativeVehicleScan.bounds(receipt.occupied).inflate(.125, 0, .125);
            if (!world.getEntities(craft, volume).isEmpty()) {
                throw new IllegalArgumentException("Clear players and other entities from the vehicle hull");
            }
            for (var cell : receipt.occupied.cells()) {
                var chunk = NativeVehicleScan.requireLoaded(world, cell.pos);
                requirePermission(world, header, actor, cell.pos);
                if (receipt.parking) {
                    if (!chunk.getBlockState(cell.pos).isAir() || world.getBlockEntity(cell.pos) != null) {
                        throw new IllegalArgumentException("Parking is obstructed at " + cell.pos.toShortString());
                    }
                    placeProbe(world, actor, cell);
                } else {
                    breakProbe(world, header, actor, cell);
                }
            }
        }

        @Override public BlockEntity stage(ServerLevel world, NativeCellTransactions.Header header, NativeRawCell cell, Receipt receipt) {
            return NativeVehicleStructure.stage(cell, world);
        }

        @Override public boolean same(CompoundTag expected, CompoundTag actual) { return sameTile(expected, actual); }

        @Override public void publish(ServerLevel world, NativeCellTransactions.Header header, NativeRawCell before,
                                      NativeRawCell after, BlockEntity staged, NativeTransferHolds.Lease lease, Receipt receipt) {
            var chunk = NativeVehicleScan.requireLoaded(world, after.pos);
            if (chunk.getBlockState(after.pos) != after.state) {
                // Detach contents before onRemove can spill a chest, barrel or storage inventory.
                if (before.hasTile()) world.removeBlockEntity(after.pos);
                try (var scope = lease.allowOnce(after.pos)) {
                    int flags = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON;
                    boolean written = NativeBuilderTransfer.builder(before.state) || NativeBuilderTransfer.builder(after.state)
                        ? NativeBuilderProtection.set(world, after.pos, after.state, flags)
                        : NativeVehicleObservers.block(before.state) || NativeVehicleObservers.block(after.state)
                            ? NativeObserverProtection.set(world, after.pos, after.state, flags)
                            : world.setBlock(after.pos, after.state, flags);
                    if (!written) {
                        throw new IllegalStateException("Vehicle block publication refused at " + after.pos.toShortString());
                    }
                }
            }
            if (after.hasTile()) {
                if (staged == null) throw new IllegalStateException("Missing staged Vehicle tile");
                world.removeBlockEntity(after.pos);
                world.setBlockEntity(staged);
                staged.setChanged();
            } else if (world.getBlockEntity(after.pos) != null) {
                throw new IllegalStateException("Unexpected tile survived Vehicle publication");
            }
            chunk.setUnsaved(true);
        }

        @Override public boolean intermediate(ServerLevel world, NativeCellTransactions.Header header,
                                              NativeRawCell before, NativeRawCell after, Receipt receipt) {
            var occupied = receipt.parking ? after : before;
            return occupied.hasTile() && world.getBlockState(occupied.pos) == occupied.state
                && world.getBlockEntity(occupied.pos) == null;
        }

        @Override public boolean readyAdditional(ServerLevel world, NativeCellTransactions.Header header, boolean rollback, Receipt receipt) {
            if (!receipt.checkpoint(world, header, rollback).loaded()) return false;
            if (receipt.expectsActor(rollback) && receipt.staged == null) {
                var staged = create(world);
                staged.load(receipt.original.copy());
                if (!sameActor(receipt, staged, null)) throw new IllegalStateException("Vehicle actor factory changed its saved receipt");
                receipt.staged = staged;
            }
            return true;
        }

        @Override public void installAdditional(ServerLevel world, NativeCellTransactions.Header header, Receipt receipt) {
            if (receipt.parking) removeActor(world, header, receipt, false);
            else restoreActor(world, header, receipt, false);
        }

        @Override public void beforeRollbackAdditional(ServerLevel world, NativeCellTransactions.Header header, Receipt receipt) {
            if (!receipt.parking) removeActor(world, header, receipt, true);
        }

        @Override public void rollbackAdditional(ServerLevel world, NativeCellTransactions.Header header, Receipt receipt) {
            if (receipt.parking) restoreActor(world, header, receipt, true);
        }

        @Override public boolean additionalMatches(ServerLevel world, NativeCellTransactions.Header header, boolean rollback, Receipt receipt) {
            var actor = actor(world, header, receipt, rollback);
            return receipt.expectsActor(rollback) ? actor instanceof NativeGroundVehicle craft && sameActor(receipt, craft, marker(header))
                : actor == null;
        }

        @Override public boolean checkpointAdditional(ServerLevel world, NativeCellTransactions.Header header, boolean rollback, Receipt receipt) {
            return receipt.checkpoint(world, header, rollback).poll() && receipt.retire(world, header, rollback);
        }

        @Override public void released(ServerLevel world, NativeCellTransactions.Header header, boolean rollback, String error, Receipt receipt) {
            var pending = PENDING_PARKS.get(world);
            if (pending != null) {
                pending.remove(receipt.identity);
                if (pending.isEmpty()) PENDING_PARKS.remove(world);
            }
            for (var position : receipt.held) {
                if (world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) continue;
                world.invalidateCapabilities(position);
                var tile = world.getBlockEntity(position);
                if (tile instanceof com.vandorlabs.neoforge.coupler.NativeCouplerEntity coupler) {
                    NativeRedstoneChannels.queueRegistration(coupler);
                }
                if (tile instanceof NativeScreenEntity screen) NativeRedstoneChannels.queueRegistration(screen);
                var state = world.getBlockState(position);
                world.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
            }
            if (receipt.expectsActor(rollback) && world.getEntity(receipt.identity) instanceof NativeGroundVehicle craft
                && craft.getPersistentData().getCompound(ESCROW).equals(marker(header))) {
                // APPLIED assembly has a fresh identity. ROLLED_BACK parking already cleared retirement.
                NativeVehicleRetirements.active(craft);
                craft.getPersistentData().remove(ESCROW);
            }
            var player = world.getServer().getPlayerList().getPlayer(header.actor());
            if (rollback && !error.isEmpty()) com.mojang.logging.LogUtils.getLogger()
                .warn("Vehicle transfer rolled back at {}: {}", header.root().toShortString(), error);
            if (player != null) player.displayClientMessage(Component.literal(rollback
                ? error.isEmpty() ? "Vehicle transfer rolled back" : error
                : receipt.parking ? "Vehicle parked" : "Vehicle assembled"), true);
        }
    };

    private static Entity actor(ServerLevel world, NativeCellTransactions.Header header, Receipt receipt, boolean rollback) {
        var actual = receipt.checkpoint(world, header, rollback).actor(receipt.identity);
        var visible = world.getEntity(receipt.identity);
        if (visible != null && visible != actual) {
            if (world.getServer().getTickCount() != 0 || !receipt.parking
                || !(visible instanceof NativeGroundVehicle stale) || !header.owner().equals(stale.owner())) {
                throw new IllegalStateException("Vehicle actor moved outside its escrow chunk");
            }
            // Native storage may still have the same UUID in the preceding entity chunk.
            // Only the journal's captured pose supplies the restored actor during cold parking recovery.
            visible.discard();
        }
        return actual == null || actual.isRemoved() ? null : actual;
    }

    private static void restoreActor(ServerLevel world, NativeCellTransactions.Header header, Receipt receipt, boolean rollback) {
        var marker = marker(header);
        var there = actor(world, header, receipt, rollback);
        if (there != null) {
            if (!(there instanceof NativeGroundVehicle current)
                || !sameActor(receipt, current, marker) && !(rollback && sameActor(receipt, current, null))) {
                throw new IllegalStateException("Vehicle recovery found another entity owner");
            }
            current.getPersistentData().put(ESCROW, marker);
            return;
        }
        var staged = receipt.staged;
        if (staged == null || staged.isRemoved()) throw new IllegalStateException("Missing staged Vehicle actor");
        staged.getPersistentData().put(ESCROW, marker);
        if (!world.addFreshEntity(staged)) throw new IllegalStateException("Vehicle actor publication refused");
    }

    private static void removeActor(ServerLevel world, NativeCellTransactions.Header header, Receipt receipt, boolean rollback) {
        var there = actor(world, header, receipt, rollback);
        if (there == null && rollback) return;
        if (!(there instanceof NativeGroundVehicle craft) || !sameActor(receipt, craft, rollback ? marker(header) : null)) {
            throw new IllegalStateException("Vehicle owner changed before removal");
        }
        craft.discard();
    }

    private static boolean sameActor(Receipt receipt, NativeGroundVehicle craft, CompoundTag marker) {
        return craft.snapshot() != null && craft.getPassengers().isEmpty() && craft.getUUID().equals(receipt.identity)
            && actorDataMatches(receipt, save(craft), marker);
    }

    private static boolean actorDataMatches(Receipt receipt, CompoundTag raw, CompoundTag marker) {
        var canonical = raw.copy();
        var data = canonical.getCompound("NeoForgeData");
        if (marker == null ? data.contains(ESCROW) : !data.getCompound(ESCROW).equals(marker)) return false;
        data.remove(ESCROW);
        return canonicalActor(canonical).equals(receipt.original);
    }

    private static CompoundTag canonicalActor(CompoundTag raw) {
        if (raw.contains("NeoForgeData", Tag.TAG_COMPOUND) && raw.getCompound("NeoForgeData").isEmpty()) {
            raw.remove("NeoForgeData");
        }
        return raw;
    }

    private static void requireActorRecord(NativeCellTransactions.Header header, Receipt receipt,
                                           boolean rollback, CompoundTag chunk) {
        CompoundTag saved = null;
        if (chunk != null) for (var item : chunk.getList("Entities", Tag.TAG_COMPOUND)) {
            var raw = (CompoundTag) item;
            if (!raw.hasUUID("UUID") || !raw.getUUID("UUID").equals(receipt.identity)) continue;
            if (saved != null) throw new IllegalStateException("Duplicate Vehicle identity in saved entity checkpoint");
            saved = raw.copy();
        }
        if ((saved != null) != receipt.expectsActor(rollback)) {
            throw new IllegalStateException("Missing or unexpected Vehicle at the entity checkpoint");
        }
        if (saved == null) return;
        var expected = BuiltInRegistries.ENTITY_TYPE.getKey(NativeVehicleBindings.VEHICLE);
        if (!saved.getString("id").equals(expected.toString())) throw new IllegalStateException("Saved Vehicle type differs");
        saved.remove("id");
        if (!actorDataMatches(receipt, saved, marker(header))) throw new IllegalStateException("Saved Vehicle ownership receipt differs");
    }

    private static CompoundTag marker(NativeCellTransactions.Header header) {
        var data = new CompoundTag();
        data.putUUID("Operation", header.operation());
        data.putUUID("Owner", header.owner());
        data.putUUID("Actor", header.actor());
        data.putLong("Root", header.root().asLong());
        return data;
    }

    /** Pending and recovered actors remain inert until both physical save receipts are durable. */
    public static boolean escrowed(NativeGroundVehicle craft) {
        if (!(craft.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return false;
        var pending = PENDING_PARKS.get(world);
        if (pending != null && pending.contains(craft.getUUID())) return true;
        var data = craft.getPersistentData();
        if (!data.contains(ESCROW)) return retirementBlocks(world, craft);
        var saved = data.getCompound(ESCROW);
        if (!saved.getAllKeys().equals(MARKER_FIELDS) || !saved.hasUUID("Operation") || !saved.hasUUID("Owner")
            || !saved.hasUUID("Actor") || !saved.contains("Root", Tag.TAG_LONG)) return true;
        var root = BlockPos.of(saved.getLong("Root"));
        if (!saved.getUUID("Owner").equals(craft.owner()) || !root.equals(anchor(craft))) return true;
        var state = NativeCellTransactions.escrowState(world, FAMILY, saved.getUUID("Operation"), root,
            craft.owner(), saved.getUUID("Actor"), receipt -> receipt.identity.equals(craft.getUUID()));
        if (state != NativeCellTransactions.EscrowState.ABSENT) return true;
        if (retirementBlocks(world, craft)) return true;
        var chunk = world.getChunkSource().getChunkNow(root.getX() >> 4, root.getZ() >> 4);
        if (chunk == null || !chunk.getBlockState(root).isAir()) return true;
        data.remove(ESCROW);
        return false;
    }

    private static boolean retirementBlocks(ServerLevel world, NativeGroundVehicle craft) {
        var state = NativeVehicleRetirements.admission(world, craft);
        if (state == NativeVehicleRetirements.State.RETIRED) craft.discard();
        return state != NativeVehicleRetirements.State.ACTIVE;
    }

    private static void requirePermission(ServerLevel world, NativeCellTransactions.Header header, ServerPlayer actor, BlockPos pos) {
        if (!world.mayInteract(actor, pos) || !actor.mayUseItemAt(pos, Direction.UP, actor.getMainHandItem())
            || NativeRampHeldOwners.foreignToCellReservation(world, pos)
            || NativeTransferHolds.reserved(world, pos) && !NativeCellTransactions.reservedBy(world, header, pos)) {
            throw new IllegalArgumentException("A protection or ownership rule refused " + pos.toShortString());
        }
    }

    private static void breakProbe(ServerLevel world, NativeCellTransactions.Header header, ServerPlayer player, NativeRawCell cell) {
        if (player.blockActionRestricted(world, cell.pos, player.gameMode.getGameModeForPlayer())) {
            throw new IllegalArgumentException("The player cannot remove vehicle cell " + cell.pos.toShortString());
        }
        var tile = world.getBlockEntity(cell.pos);
        NativeVehicleScan.requireStorageAccess(world, cell.pos, cell.state, tile, header.owner(), player.getUUID());
        if (NativeVehicleRftoolsCell.supports(cell.state)) {
            NativeVehicleRftoolsCell.contents(tile.saveWithFullMetadata(world.registryAccess()));
        }
        var event = new BlockEvent.BreakEvent(world, cell.pos, cell.state, player);
        var previous = PROBE.get();
        PROBE.set(new BreakProbe(event, header));
        try {
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                throw new IllegalArgumentException("A break protection rule refused " + cell.pos.toShortString());
            }
        } finally {
            if (previous == null) PROBE.remove(); else PROBE.set(previous);
        }
    }

    /** This proof applies only to the observed event object. It grants no block/inventory write. */
    public static boolean isAuthorizedBreakProbe(BlockEvent.BreakEvent event) {
        var probe = PROBE.get();
        return probe != null && probe.event == event && event.getLevel() instanceof ServerLevel world
            && NativeCellTransactions.reservedBy(world, probe.header, event.getPos());
    }

    /** Suppress only native dismantling during this exact read-only Maxwell probe. */
    public static boolean isBuilderBreakProbe(BlockEvent.BreakEvent event) {
        var probe = PROBE.get();
        return probe != null && probe.event == event && NativeBuilderTransfer.builder(event.getState());
    }

    /** Suppress only native dismantling during this exact read-only observer probe. */
    public static boolean isObserverBreakProbe(BlockEvent.BreakEvent event) {
        var probe = PROBE.get();
        return probe != null && probe.event == event && NativeVehicleObservers.block(event.getState());
    }

    private static void placeProbe(ServerLevel world, ServerPlayer player, NativeRawCell cell) {
        var below = NativeVehicleScan.requireLoaded(world, cell.pos.below()).getBlockState(cell.pos.below());
        var previous = BlockSnapshot.create(world.dimension(), world, cell.pos);
        var event = new BlockEvent.EntityPlaceEvent(previous, below, player) {
            @Override public BlockState getPlacedBlock() { return cell.state; }
            @Override public BlockState getState() { return cell.state; }
        };
        var previousProbe = PROBE.get();
        PROBE.set(new BreakProbe(null, null));
        try {
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                throw new IllegalArgumentException("A placement protection rule refused " + cell.pos.toShortString());
            }
        } finally {
            if (previousProbe == null) PROBE.remove(); else PROBE.set(previousProbe);
        }
    }

    static boolean sameTile(CompoundTag expected, CompoundTag actual) {
        if (Objects.equals(expected, actual)) return true;
        if (expected == null || actual == null) return false;
        return canonicalTile(expected).equals(canonicalTile(actual));
    }

    private static CompoundTag canonicalTile(CompoundTag raw) {
        var result = raw.copy();
        if (result.contains("keepPacked", Tag.TAG_BYTE) && !result.getBoolean("keepPacked")) result.remove("keepPacked");
        if (result.contains("NeoForgeData", Tag.TAG_COMPOUND) && result.getCompound("NeoForgeData").isEmpty()) {
            result.remove("NeoForgeData");
        }
        return result;
    }

    private static void refused(ServerPlayer player, RuntimeException failure) {
        player.displayClientMessage(Component.literal(Objects.toString(failure.getMessage(), "Vehicle transfer refused")), true);
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel world) PENDING_PARKS.remove(world);
    }

    private NativeVehicleTransfer() {}
}
