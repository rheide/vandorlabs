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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Runtime propulsion owns only the changing Maxwell fuel and throttle fields of the installed hull. */
public final class NativeVehiclePropulsion {
    private static final int GAUGE_LIMIT = 2 * (NativeVehicleStructure.LIMITS.cells() /
            (MaxwellEngine.BASE_WIDTH * MaxwellEngine.BASE_WIDTH * MaxwellEngine.depth(MaxwellEngine.BASE_WIDTH)));
    public record Readout(double mass, double effectiveMass, int drives, int fuel, int capacity,
                          double thrust, double takeoffMass, double ratedTakeoffMass, double ceiling, boolean canHover) {
        static final Readout EMPTY = new Readout(0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    public static Readout preview(NativeTransferSnapshot blocks, ServerLevel level, BlockPos origin) {
        var drive = new NativeVehiclePropulsion(blocks, level.registryAccess());
        return drive.readout(NativeVehicleResources.fluidMass(blocks, level.registryAccess()), value -> drive.previewSignal(value, level, origin));
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

    NativeDriveSettings settings(BlockPos core) { return drives.get(core).settings; }
    ChannelList channels(BlockPos core) { return drives.get(core).channels; }

    boolean configure(BlockPos core, NativeDriveSettings edit, ChannelList channels) {
        var old = drives.get(core);
        if (old == null) return false;
        var settings = NativeDriveSettings.maxwell(edit.redstone(), edit.inverted(), edit.levels());
        if (settings.equals(old.settings) && channels.equals(old.channels)) return true;
        var raw = old.core.tile();
        raw.put("Drive", settings.save());
        raw.putIntArray("Channels", channels.toArray());
        if (raw.sizeInBytes() > NativeVehicleStructure.LIMITS.tileBytes() - NativeVehicleResources.MAXWELL_DYNAMIC_BYTES) return false;
        var cell = new NativeRawCell(core, old.core.state, raw, NativeVehicleStructure.LIMITS.tileBytes());
        drives.put(core, new Drive(cell, settings, channels, old.width, old.tank, old.localPower, old.powerPort));
        changed = true;
        return true;
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
        for (var value : tile.getList("PortItems", Tag.TAG_COMPOUND)) {
            var stack = ((CompoundTag)value).getCompound("Item");
            mass += net.minecraft.world.item.ItemStack.parse(registries, stack).map(item -> itemMass(item, 0)).orElse(0D);
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

    static boolean gaugeArrayValid(int[] gauges) {
        return gauges.length <= GAUGE_LIMIT && gauges.length % 2 == 0;
    }

    int[] gauges() {
        int[] gauges = new int[drives.size() * 2];
        int index = 0;
        for (var drive : drives.values()) {
            gauges[index++] = gaugePosition(drive.core.pos);
            gauges[index++] = drive.tank.amount() << 4 | drive.tank.activeLevel();
        }
        return gauges;
    }

    static int gaugePosition(BlockPos pos) {
        return (pos.getX() + 32) << 14 | (pos.getY() + 32) << 7 | (pos.getZ() + 32);
    }

    private int fuel() {
        int amount = 0;
        for (var drive : drives.values()) amount += drive.tank.amount();
        return amount;
    }

    double mass(NativeGroundVehicle craft) {
        double loaded = dryMass + craft.resources().fluidMass() + fuel() / 1000D;
        return FlightEnvelope.effectiveMass(loaded, plates, repulsors);
    }

    Readout readout(NativeGroundVehicle craft) {
        return readout(craft.resources().fluidMass(), drive -> signalAt(drive, craft));
    }

    private Readout readout(double fluidMass, java.util.function.ToIntFunction<Drive> signals) {
        int fuel = fuel();
        double mass = dryMass + fluidMass + fuel / 1000D;
        double effective = FlightEnvelope.effectiveMass(mass, plates, repulsors);
        double available = 0, rated = 0;
        for (var drive : drives.values()) {
            int signal = signals.applyAsInt(drive);
            double full = MaxwellEngine.thrustUnits(drive.width);
            rated += full;
            available += full * drive.tank.throttle(drive.settings, signal, true) / 15;
        }
        return new Readout(mass, effective, drives.size(), fuel, drives.size() * MaxwellEngine.CAPACITY, available,
                FlightEnvelope.takeoffMass(available), FlightEnvelope.takeoffMass(rated), FlightEnvelope.maximumAltitude(effective, available), canHover());
    }

    private int previewSignal(Drive drive, ServerLevel world, BlockPos origin) {
        int signal = drive.localPower;
        if (!drive.settings.redstone() || signal == 15) return signal;
        var port = origin.offset(drive.powerPort);
        for (int index = 0; index < drive.channels.size(); index++) {
            signal = Math.max(signal, NativeRedstoneChannels.level(world, port, drive.channels.get(index)));
        }
        return signal;
    }

    private static int signalAt(Drive drive, NativeGroundVehicle craft) {
        int signal = drive.localPower;
        if (!drive.settings.redstone() || signal == 15) return signal;
        var resources = craft.resources();
        return Math.max(signal, Math.max(resources.localLevel(drive.powerPort),
                resources.channelLevel(craft, drive.powerPort, drive.channels)));
    }

    double thrust(NativeGroundVehicle craft, boolean demand, boolean burn) {
        double total = 0;
        for (var drive : drives.values()) {
            int signal = signalAt(drive, craft);
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
