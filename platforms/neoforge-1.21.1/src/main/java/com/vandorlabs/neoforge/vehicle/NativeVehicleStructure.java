package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.NativeMountedSwitchBlock;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.neoforge.gear.NativeGearEntity;
import com.vandorlabs.neoforge.landing.NativeSideWheel;
import com.vandorlabs.neoforge.navigation.observer.*;
import com.vandorlabs.neoforge.transfer.NativeConstructorFrame;
import com.vandorlabs.neoforge.ports.NativeResourcePortEntity;
import com.vandorlabs.neoforge.reservoir.NativeBufferEntity;
import com.vandorlabs.neoforge.seating.NativeChairEntity;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.coupler.NativeCouplerBlock;
import com.vandorlabs.neoforge.coupler.NativeCouplerEntity;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import com.vandorlabs.neoforge.storage.NativeStorageEntity;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Canonical controller-relative hull, with explicit adapters for tiles that own contents. */
public final class NativeVehicleStructure {
    static final int RADIUS = 32;
    public static final NativeTransferSnapshot.Limits LIMITS =
        new NativeTransferSnapshot.Limits(2048, 128 * 1024, 1536 * 1024, 16 * 1024 * 1024);

    public static void validate(NativeTransferSnapshot snapshot, BlockPos pilot, Direction facing, HolderLookup.Provider registries) {
        if (snapshot == null || pilot == null || facing == null || !facing.getAxis().isHorizontal()
            || snapshot.cells().size() > LIMITS.cells() || snapshot.decodedBytes > LIMITS.bytes()) {
            throw new IllegalArgumentException("Invalid vehicle hull");
        }
        var cells = new HashMap<BlockPos, NativeRawCell>();
        int controllers = 0;
        int pilots = 0;
        for (var cell : snapshot.cells()) {
            if (!within(cell.pos) || cells.put(cell.pos, cell) != null) {
                throw new IllegalArgumentException("Vehicle cell outside its controller-relative bounds");
            }
            requireSupported(cell.state);
            if (cell.state.is(NativeVehicleBindings.CONTROLLER)) controllers++;
            if (pilot(cell.state)) {
                if (!cell.pos.equals(pilot) || cell.state.getValue(NativeSeatBlock.FACING) != facing) {
                    throw new IllegalArgumentException("The Pilot Seat position or heading changed");
                }
                pilots++;
            }
            var raw = cell.tile();
            if (raw != null) {
                if (raw.sizeInBytes() > LIMITS.tileBytes()) throw new IllegalArgumentException("Vehicle tile data exceeds its byte bound");
                requireCoordinates(cell.pos, raw);
            }
        }
        if (controllers != 1 || pilots != 1 || cells.size() < 4
            || !cells.containsKey(BlockPos.ZERO) || !cells.get(BlockPos.ZERO).state.is(NativeVehicleBindings.CONTROLLER)) {
            throw new IllegalArgumentException("Connect one Vehicle Controller and one complete Pilot Seat to a hull");
        }
        for (var cell : cells.values()) {
            for (var position : requiredParts(cell.pos, cell.state)) {
                var member = cells.get(position);
                if (member == null || !sameAssembly(cell, member)) {
                    throw new IllegalArgumentException("Include every part of the Pilot Seat and each Wheel");
                }
            }
            if (cell.state.getBlock() instanceof NativeCredentialReaderBlock reader
                && !cells.containsKey(cell.pos.relative(reader.supportDirection(cell.state)))) {
                throw new IllegalArgumentException("The Access Card Slot must retain its hull mount");
            }
        }
        var connected = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        connected.add(BlockPos.ZERO);
        pending.add(BlockPos.ZERO);
        while (!pending.isEmpty()) {
            var at = pending.removeFirst();
            var state = cells.get(at).state;
            for (var p : requiredParts(at, state)) if (connected.add(p)) pending.add(p);
            for (var side : Direction.values()) {
                var other = at.relative(side);
                var neighbor = cells.get(other);
                if (neighbor != null && touches(state, neighbor.state, side) && connected.add(other)) pending.add(other);
            }
        }
        if (connected.size() != cells.size()) throw new IllegalArgumentException("Vehicle hull contains disconnected cells");
        NativeBuilderTransfer.validateVehicle(snapshot.cells(), registries);
        NativeVehicleObservers.build(snapshot.cells(), cells);
        NativeVehicleArms.validate(cells);
    }

    static boolean pilot(BlockState state) {
        return state.getBlock() instanceof NativeSeatBlock seat && seat.kind == NativeSeatKind.PILOT
            && !state.getValue(NativeSeatBlock.UPPER);
    }

    static List<BlockPos> requiredParts(BlockPos at, BlockState state) {
        if (state.getBlock() instanceof NativeSeatBlock) {
            return List.of(state.getValue(NativeSeatBlock.UPPER) ? at.below() : at.above());
        }
        if (state.getBlock() instanceof NativeSideWheel wheel && wheel.size() == 2) {
            var root = wheel.root(at, state);
            var facing = state.getValue(NativeSideWheel.FACING);
            var result = new ArrayList<BlockPos>(4);
            for (int index = 0; index < 4; index++) result.add(wheel.cell(root, facing, index));
            return result;
        }
        return List.of();
    }

    private static boolean sameAssembly(NativeRawCell a, NativeRawCell b) {
        if (!b.state.is(a.state.getBlock())) return false;
        if (a.state.getBlock() instanceof NativeSeatBlock) {
            return a.state.getValue(NativeSeatBlock.UPPER) != b.state.getValue(NativeSeatBlock.UPPER)
                && a.state.getValue(NativeSeatBlock.FACING) == b.state.getValue(NativeSeatBlock.FACING);
        }
        var wheel = (NativeSideWheel) a.state.getBlock();
        return a.state.getValue(NativeSideWheel.FACING) == b.state.getValue(NativeSideWheel.FACING)
            && wheel.root(a.pos, a.state).equals(wheel.root(b.pos, b.state));
    }

    /**
     * Standalone side wheels connect only to their mounting slab and their own members.
     * They never join a vehicle to the roadway or to a neighboring vehicle.
     */
    static boolean touches(BlockState a, BlockState b, Direction fromA) {
        if (a.getBlock() instanceof NativeCouplerBlock && b.getBlock() instanceof NativeCouplerBlock)
            return NativeCouplerBlock.pair(a, b, fromA);
        return mountingFace(a, fromA) && mountingFace(b, fromA.getOpposite());
    }

    private static boolean mountingFace(BlockState state, Direction face) {
        return state.getBlock() instanceof NativeCouplerBlock
            ? face == NativeCouplerBlock.facing(state).getOpposite() : connectingFace(state, face);
    }

    static boolean connectingFace(BlockState state, Direction face) {
        if (state.getBlock() instanceof NativeCouplerBlock) {
            var head = NativeCouplerBlock.facing(state);
            return face == head.getOpposite() || face == head && state.getValue(NativeCouplerBlock.LOCKED);
        }
        if (state.getBlock() instanceof NativeSideWheel) {
            return state.getValue(NativeSideWheel.PART) == 0
                && face == state.getValue(NativeSideWheel.FACING).getOpposite();
        }
        if (state.getBlock() instanceof NativeCredentialReaderBlock reader) return face == reader.supportDirection(state);
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            var type = state.getValue(BlockStateProperties.SLAB_TYPE);
            if (type == SlabType.BOTTOM && face == Direction.UP || type == SlabType.TOP && face == Direction.DOWN) return false;
        }
        return true;
    }

    static boolean within(BlockPos local) {
        return Math.abs((long) local.getX()) <= RADIUS && Math.abs((long) local.getY()) <= RADIUS
            && Math.abs((long) local.getZ()) <= RADIUS;
    }

    static void requireSupported(BlockState state) {
        var block = state.getBlock();
        var identity = BuiltInRegistries.BLOCK.getKey(block);
        boolean plainVanilla = identity.getNamespace().equals("minecraft") && (
            block.getClass() == Block.class || block instanceof RotatedPillarBlock || block instanceof SlabBlock
            || block instanceof StairBlock || block instanceof HalfTransparentBlock || block instanceof StainedGlassBlock
            || block instanceof SignBlock || block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock
            || block instanceof ChestBlock || block instanceof BarrelBlock);
        boolean nativeBlock = state.is(NativeVehicleBindings.CONTROLLER) || block instanceof NativeSideWheel
            || block instanceof NativeBuilderCoreBlock core && core.design.drive() == NativeDriveDesign.LONGBURN
            || block instanceof NativeBuilderPartBlock || NativeVehicleObservers.block(state)
            || block instanceof NativeCouplerBlock
            || block instanceof NativeCredentialReaderBlock reader && !reader.chip()
            || block instanceof NativeSeatBlock seat && seat.kind == NativeSeatKind.PILOT
            || block instanceof com.vandorlabs.neoforge.ports.NativePortBlock
            || block instanceof com.vandorlabs.neoforge.propulsion.NativePropulsionBlock
            || NativeVehicleArms.block(state) || NativeVehicleStatus.supports(state)
            || identity.getNamespace().equals("vandorlabs") && Set.of("battery_block", "hexagonal_fuel_tank", "programmable_storage").contains(identity.getPath())
            || programmable(state);
        if ((!plainVanilla && !nativeBlock && !NativeVehicleMekanismTank.supports(state)) || state.isAir() || !state.getFluidState().isEmpty()
            || block instanceof ChestBlock && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            throw new IllegalArgumentException("Vehicle cannot move " + identity);
        }
        var heading = state;
        for (int turn = 0; turn < 4; turn++) heading = rotateState(heading, Rotation.CLOCKWISE_90);
        if (heading != state) throw new IllegalArgumentException("Vehicle rotation cannot preserve " + identity);
    }

    static BlockEntity stage(NativeRawCell cell, ServerLevel world) {
        var staged = cell.stage(world, NativeVehicleStructure::supportsFactory, NativeVehicleTransfer::sameTile);
        if (staged != null && !supportsTile(staged)) throw new IllegalArgumentException("Unsupported loaded Vehicle tile");
        return staged;
    }

    private static boolean supportsFactory(BlockEntity tile) {
        // Formed parts and configured readers become valid only after their exact saved fields load.
        return tile instanceof NativeBuilderPartEntity || tile instanceof NativeObserverPartEntity
            || tile instanceof NativeCredentialReaderEntity || tile instanceof NativeGearEntity || supportsTile(tile);
    }

    static boolean supportsTile(BlockEntity tile) {
        if (tile instanceof NativeVehicleControllerEntity || NativeVehicleMekanismTank.supports(tile)) return true;
        if (tile instanceof NativeGearEntity gear) {
            return NativeVehicleArms.block(gear.getBlockState()) && !gear.inert() && gear.owner() != null
                && gear.payload() == null && !gear.hasStoredSections();
        }
        if (tile instanceof com.vandorlabs.neoforge.propulsion.NativePropulsionEntity propulsion) return !propulsion.inert();
        if (tile instanceof com.vandorlabs.neoforge.screen.NativeScreenEntity screen) {
            return NativeVehicleStatus.supports(screen.getBlockState()) && !screen.inert();
        }
        if (tile instanceof NativeBuilderEntity builder) {
            return builder.maxwell() && builder.formed() && !builder.inert() && !builder.pendingFormation();
        }
        if (tile instanceof NativeBuilderPartEntity part) return !part.inert();
        if (tile instanceof NativeObserverEntity observer) {
            return observer.formed() && !observer.inert() && !observer.pendingFormation();
        }
        if (tile instanceof NativeObserverPartEntity part) return !part.inert();
        if (tile instanceof NativeCredentialReaderEntity reader) {
            return !reader.chip() && !reader.inert() && reader.behavior() == 0 && !reader.visual().motion().returning()
                && reader.mountRotation() == reader.getBlockState().getValue(NativeMountedSwitchBlock.ROTATION);
        }
        if (tile instanceof NativeChairEntity seat) return seat.kind() == NativeSeatKind.PILOT && !seat.inert();
        if (tile instanceof NativeCouplerEntity coupler) return !coupler.inert();
        if (tile instanceof NativeResourcePortEntity port) return !port.inert();
        if (tile instanceof NativeBufferEntity buffer) return !buffer.inert();
        if (tile instanceof NativeStorageEntity storage) return !storage.inert();
        if (tile.getClass() == ProgrammableBlockEntity.class) {
            return programmable(tile.getBlockState()) && supportedBuilding(tile);
        }
        if (tile instanceof RandomizableContainerBlockEntity random && random.getLootTable() != null) return false;
        return tile.getClass() == ChestBlockEntity.class || tile.getClass() == TrappedChestBlockEntity.class
            || tile.getClass() == BarrelBlockEntity.class || tile.getClass() == SignBlockEntity.class
            || tile.getClass() == HangingSignBlockEntity.class;
    }

    private static boolean programmable(BlockState state) {
        return state.is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get()) || state.is(VandorLabsBootstrap.PROGRAMMABLE_SLAB.get())
            || state.is(VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get());
    }

    private static boolean supportedBuilding(BlockEntity tile) {
        var raw = tile.saveWithFullMetadata(tile.getLevel().registryAccess());
        var transmission = com.vandorlabs.neoforge.redstone.NativeTransmissionMigration.read(raw);
        return com.vandorlabs.neoforge.screen.NativeScreenOwnership.installable(raw)
            && transmission.installable() && transmission.install().equals(raw);
    }

    static NativeTransferSnapshot translate(NativeTransferSnapshot source, BlockPos anchor, Rotation rotation) {
        var cells = new ArrayList<NativeRawCell>(source.cells().size());
        for (var cell : source.cells()) {
            var target = anchor.offset(cell.pos.rotate(rotation));
            var state = rotateState(cell.state, rotation);
            var tile = cell.tile();
            if (tile != null) {
                tile.putInt("x", target.getX());
                tile.putInt("y", target.getY());
                tile.putInt("z", target.getZ());
                if (rotation != Rotation.NONE && (cell.state.getBlock() instanceof NativeBuilderPartBlock
                    || cell.state.getBlock() instanceof NativeObserverPartBlock)) {
                    if (!tile.contains("ConstructorOwner", Tag.TAG_LONG) || tile.contains("ConstructorMatter")) {
                        throw new IllegalArgumentException("Vehicle machine frame needs native part ownership");
                    }
                    tile.putLong("ConstructorOwner", BlockPos.of(tile.getLong("ConstructorOwner")).rotate(rotation).asLong());
                    NativeConstructorFrame.rotate(tile, rotation::rotate);
                }
                if (rotation != Rotation.NONE && cell.state.getBlock() instanceof NativeCredentialReaderBlock
                    && cell.state.getValue(NativeMountedSwitchBlock.ROTATION) != state.getValue(NativeMountedSwitchBlock.ROTATION)) {
                    tile.putInt(SaveSchema.Redstone.MOUNT_ROTATION, state.getValue(NativeMountedSwitchBlock.ROTATION));
                }
                if (rotation != Rotation.NONE && NativeVehicleArms.block(cell.state)) NativeVehicleArms.rotate(tile, rotation);
                if (rotation != Rotation.NONE && tile.contains("PortFaces", Tag.TAG_INT_ARRAY)) {
                    var old = tile.getIntArray("PortFaces");
                    if (old.length != 6) throw new IllegalArgumentException("Invalid vehicle port face bank");
                    var faces = new int[6];
                    for (var side : Direction.values()) faces[rotation.rotate(side).get3DDataValue()] = old[side.get3DDataValue()];
                    tile.putIntArray("PortFaces", faces);
                }
            }
            cells.add(new NativeRawCell(target, state, tile, LIMITS.tileBytes()));
        }
        return new NativeTransferSnapshot(cells, LIMITS);
    }

    private static BlockState rotateState(BlockState state, Rotation rotation) {
        if (state.getBlock() instanceof NativeBuilderCoreBlock) {
            return state.setValue(NativeBuilderCoreBlock.FACING, rotation.rotate(state.getValue(NativeBuilderCoreBlock.FACING)));
        }
        if (state.getBlock() instanceof NativeObserverCoreBlock) {
            return state.setValue(NativeObserverCoreBlock.FACING, rotation.rotate(state.getValue(NativeObserverCoreBlock.FACING)));
        }
        var rotated = state.rotate(rotation);
        if (state.getBlock() instanceof NativeCredentialReaderBlock reader
            && reader.supportDirection(state).getAxis().isVertical()) {
            var old = Direction.from2DDataValue(state.getValue(NativeMountedSwitchBlock.ROTATION));
            return rotated.setValue(NativeMountedSwitchBlock.ROTATION, rotation.rotate(old).get2DDataValue());
        }
        return rotated;
    }

    private static void requireCoordinates(BlockPos p, CompoundTag raw) {
        if (!raw.contains("x", Tag.TAG_INT) || !raw.contains("y", Tag.TAG_INT) || !raw.contains("z", Tag.TAG_INT)
            || raw.getInt("x") != p.getX() || raw.getInt("y") != p.getY() || raw.getInt("z") != p.getZ()) {
            throw new IllegalArgumentException("Vehicle tile coordinates differ from its cell");
        }
    }

    private NativeVehicleStructure() {}
}
