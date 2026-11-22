package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.neoforge.builders.NativeBuilderCoreBlock;
import com.vandorlabs.neoforge.builders.NativeBuilderFormation;
import com.vandorlabs.neoforge.builders.NativeBuilderPartBlock;
import com.vandorlabs.neoforge.builders.NativeDriveDesign;
import com.vandorlabs.neoforge.builders.NativeDriveSettings;
import com.vandorlabs.neoforge.builders.NativeMaxwellTank;
import com.vandorlabs.neoforge.propulsion.NativePropulsionBlock;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.redstone.ChannelList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Runtime propulsion owns only the changing Maxwell fuel and throttle fields of the installed hull. */
public final class NativeVehiclePropulsion {
    public record Readout(double mass, double effectiveMass, int drives, int fuel, int capacity,
                          double thrust, double takeoffMass, double ceiling, boolean canHover) {
        static final Readout EMPTY = new Readout(0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    public static Readout preview(NativeTransferSnapshot blocks, ServerLevel level, BlockPos origin) {
        return new NativeVehiclePropulsion(blocks, level.registryAccess()).readout(level, origin::offset);
    }

    private record Drive(NativeRawCell core, NativeDriveSettings settings, ChannelList channels, int width,
                         NativeMaxwellTank tank, int localPower, BlockPos powerPort) {}

    private final Map<BlockPos, Drive> drives = new LinkedHashMap<>();
    private final NativeTransferSnapshot source;
    private NativeTransferSnapshot saved;
    private double dryMass;
    private int plates, repulsors, verticalThrusters;
    private boolean thrust;
    private boolean changed;

    NativeVehiclePropulsion(NativeTransferSnapshot source, HolderLookup.Provider registries) {
        this.source = source;
        saved = source;
        var cells = new LinkedHashMap<BlockPos, NativeRawCell>();
        for (var cell : source.cells()) cells.put(cell.pos, cell);
        for (var cell : source.cells()) {
            if (cell.state.getBlock() instanceof NativeBuilderCoreBlock core
                    && core.design.drive() == NativeDriveDesign.LONGBURN
                    && cell.state.getValue(NativeBuilderCoreBlock.FORMED)) {
                var tag = cell.tile();
                var facing = cell.state.getValue(NativeBuilderCoreBlock.FACING);
                var layout = NativeBuilderFormation.savedLayout(core.design, facing, tag);
                var power = cell.pos.offset(layout.x(layout.width - 1, layout.depth - 1), 0,
                        layout.z(layout.width - 1, layout.depth - 1));
                int localPower = 0;
                for (int x = 0; x < layout.width && localPower == 0; x++) {
                    for (int y = 0; y < layout.height && localPower == 0; y++) {
                        for (int z = 0; z < layout.depth && localPower == 0; z++) {
                            if (!layout.frame(x, y, z)) continue;
                            var part = cell.pos.offset(layout.x(x, z), y, layout.z(x, z));
                            for (var direction : Direction.values()) {
                                var neighbor = cells.get(part.relative(direction));
                                if (neighbor != null && neighbor.state.is(Blocks.REDSTONE_BLOCK)) localPower = 15;
                            }
                        }
                    }
                }
                drives.put(cell.pos, new Drive(cell, NativeDriveSettings.readMaxwell(tag),
                        ChannelList.of(tag.getIntArray("Channels")), layout.width,
                        NativeMaxwellTank.read(tag, registries), localPower, power));
                dryMass += MaxwellEngine.dryMass(layout.width);
                continue;
            }
            if (cell.state.getBlock() instanceof NativeBuilderPartBlock) continue;
            dryMass += blockMass(cell.state);
            var tile = cell.tile();
            if (tile != null) dryMass += cargoMass(tile, registries);
            if (cell.state.getBlock() instanceof NativePropulsionBlock nozzle) {
                switch (nozzle.definition().id) {
                    case "antigravity_plate" -> plates++;
                    case "repulsor_array" -> repulsors++;
                    case "vertical_hover_thruster" -> verticalThrusters++;
                    default -> thrust = true;
                }
            }
        }
    }

    boolean canHover() { return plates + repulsors + verticalThrusters >= 3 && thrust; }
    boolean hasThruster() { return thrust; }
    boolean hasMaxwell() { return !drives.isEmpty(); }

    NativeMaxwellTank tank(BlockPos core) {
        var drive = drives.get(core);
        return drive != null ? drive.tank : null;
    }

    void fuelChanged() { changed = true; }

    private static double blockMass(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        double density = density(name);
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) return density * .5;
        if (state.getBlock() instanceof net.minecraft.world.level.block.StairBlock) return density * .75;
        if (state.getBlock() instanceof NativePropulsionBlock || name.contains("screen") || name.contains("seat")) return density * .25;
        return density;
    }

    private static double density(String name) {
        double mass = name.contains("gold") || name.contains("netherite") ? 7.5
                : name.contains("copper") ? 3.5
                : name.contains("iron") || name.contains("steel") || name.contains("metal") ? 3
                : name.contains("diamond") ? 1.4
                : name.contains("dirt") || name.contains("sand") || name.contains("gravel") ? .6
                : name.contains("ice") ? .37
                : name.contains("plank") || name.contains("_log") || name.contains("_wood") || name.contains("bamboo") ? .25
                : name.contains("wool") || name.contains("leather") ? .08 : 1;
        return name.contains("ore") ? Math.max(1, 1 + .2 * (mass - 1)) : mass;
    }

    private static double cargoMass(CompoundTag tile, HolderLookup.Provider registries) {
        double mass = 0;
        for (String name : new String[]{"Items", "Inventory", "Buffer"}) {
            if (tile.get(name) instanceof CompoundTag inventory) {
                mass += cargoMass(inventory, registries);
            } else {
                for (var stack : tile.getList(name, Tag.TAG_COMPOUND)) {
                    mass += net.minecraft.world.item.ItemStack.parse(registries, stack)
                            .map(item -> itemMass(item, 0)).orElse(0D);
                }
            }
        }
        return mass;
    }

    private static double itemMass(net.minecraft.world.item.ItemStack stack, int depth) {
        double mass = stack.getItem() instanceof net.minecraft.world.item.BlockItem item ? blockMass(item.getBlock().defaultBlockState())
                : .05 * density(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        if (depth < 8) {
            var container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (container != null) {
                for (var nested : container.nonEmptyItems()) mass += itemMass(nested, depth + 1);
            }
        }
        return stack.getCount() * mass;
    }

    private int fuel() {
        int amount = 0;
        for (var drive : drives.values()) amount += drive.tank.amount();
        return amount;
    }

    double mass() {
        return FlightEnvelope.effectiveMass(dryMass + fuel() / 1000D, plates, repulsors);
    }

    Readout readout(NativeGroundVehicle craft) {
        return readout((ServerLevel)craft.level(), pos -> BlockPos.containing(craft.toWorld(pos)));
    }

    private Readout readout(ServerLevel level, Function<BlockPos, BlockPos> positions) {
        int fuel = fuel();
        double mass = dryMass + fuel / 1000D;
        double effective = FlightEnvelope.effectiveMass(mass, plates, repulsors);
        double available = 0;
        for (var drive : drives.values()) {
            int signal = signalAt(drive, level, positions);
            available += MaxwellEngine.thrustUnits(drive.width) * drive.tank.throttle(drive.settings, signal, true) / 15;
        }
        return new Readout(mass, effective, drives.size(), fuel, drives.size() * MaxwellEngine.CAPACITY, available,
                FlightEnvelope.takeoffMass(available), FlightEnvelope.maximumAltitude(effective, available), canHover());
    }

    private static int signalAt(Drive drive, ServerLevel world, Function<BlockPos, BlockPos> positions) {
        int signal = drive.localPower;
        if (!drive.settings.redstone() || drive.channels.isEmpty() || signal == 15) return signal;
        var pos = positions.apply(drive.powerPort);
        for (int index = 0; index < drive.channels.size(); index++) {
            signal = Math.max(signal, NativeRedstoneChannels.level(world, pos, drive.channels.get(index)));
        }
        return signal;
    }

    double thrust(NativeGroundVehicle craft, boolean demand, boolean burn) {
        double total = 0;
        for (var drive : drives.values()) {
            int signal = signalAt(drive, (ServerLevel)craft.level(), pos -> BlockPos.containing(craft.toWorld(pos)));
            int level = drive.tank.throttle(drive.settings, signal, demand);
            if (burn) {
                int previous = drive.tank.activeLevel();
                boolean publicChange = drive.tank.tick(level, craft.level().getGameTime());
                changed |= publicChange || previous != 0 || level != 0;
                level = drive.tank.activeLevel();
            }
            total += MaxwellEngine.thrustUnits(drive.width) * level / 15;
        }
        return total;
    }

    NativeTransferSnapshot snapshot(HolderLookup.Provider registries) {
        if (!changed) return saved;
        var cells = new ArrayList<NativeRawCell>(source.cells().size());
        for (var cell : source.cells()) {
            var drive = drives.get(cell.pos);
            if (drive == null) {
                cells.add(cell);
                continue;
            }
            var updated = drive.core.tile();
            drive.tank.write(updated, registries);
            cells.add(new NativeRawCell(cell.pos, cell.state, updated, NativeVehicleStructure.LIMITS.tileBytes()));
        }
        saved = new NativeTransferSnapshot(cells, NativeVehicleStructure.LIMITS);
        changed = false;
        return saved;
    }
}
