package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.neoforge.gear.NativeGearResourceTransfer;
import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.reservoir.*;
import com.vandorlabs.neoforge.storage.*;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.ports.ResourcePortSettings.Face;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelSpatialPolicy;
import com.vandorlabs.redstone.TransmissionSettings;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Adjacent carried owners transfer locally. Physical tile capabilities and neighbor writes stay parked. */
final class NativeVehicleResources {
    private static final int ROUTES_PER_TICK = 192;
    private static final int MAXWELL_DYNAMIC_BYTES = 96;
    private final Map<BlockPos, NativeRawCell> updates = new HashMap<>();
    private final Map<BlockPos, Integer> growth = new HashMap<>();
    private final Map<BlockPos, NativeRawCell> states = new HashMap<>();
    private final List<Route> routes = new ArrayList<>();
    private final Map<BlockPos, List<Endpoint>> mounted;
    private final List<ArmLink> armLinks = new ArrayList<>();
    private final NativeVehicleArms arms;
    private final Map<BlockPos, BufferSignal> outputPositions = new HashMap<>();
    private final Map<Integer, List<BufferSignal>> outputChannels = new HashMap<>();
    private final List<NativeVehicleObservers.Receiver> observers;
    private final NativeVehiclePropulsion propulsion;
    private final NativeVehicleMeters controls;
    private NativeTransferSnapshot savedInput, saved;
    private int next, nextArm, availableBytes, extraBytes, savedArmRevision;
    private long tick = Long.MIN_VALUE, fluidMassMb;
    private boolean changed;

    NativeVehicleResources(NativeTransferSnapshot snapshot, HolderLookup.Provider registries,
                           NativeVehiclePropulsion propulsion, NativeVehicleMeters controls) {
        this.propulsion = propulsion;
        this.controls = controls;
        var mounted = new LinkedHashMap<BlockPos, List<Endpoint>>();
        availableBytes = NativeVehicleStructure.LIMITS.bytes() - snapshot.decodedBytes - controls.reservedBytes();
        for (var cell : snapshot.cells()) {
            states.put(cell.pos, cell);
            if (!cell.hasTile()) continue;
            var endpoint = Endpoint.owned(cell, registries);
            if (endpoint != null) {
                add(mounted, cell.pos, endpoint);
                if (endpoint.kind == NativePortKind.FLUID) fluidMassMb += endpoint.fluid.getAmount();
                if (cell.state.getBlock() instanceof NativeBufferBlock buffer) {
                    var output = BufferSignal.read(endpoint, buffer);
                    if (output.local) outputPositions.put(cell.pos, output);
                    for (int i = 0; i < output.channels.size(); i++) {
                        outputChannels.computeIfAbsent(output.channels.get(i), unused -> new ArrayList<>()).add(output);
                    }
                }
            }
            var tank = propulsion.tank(cell.pos);
            if (tank == null) continue;
            availableBytes -= MAXWELL_DYNAMIC_BYTES;
            var core = (NativeBuilderCoreBlock)cell.state.getBlock();
            var facing = cell.state.getValue(NativeBuilderCoreBlock.FACING);
            var layout = NativeBuilderFormation.savedLayout(core.design, facing, cell.tile());
            for (var side : new Direction[]{facing, facing.getCounterClockWise(), facing.getClockWise()}) {
                int x = side == facing ? MaxwellEngine.faceCenter(layout.width) : side == facing.getCounterClockWise() ? 0 : layout.width - 1;
                int z = side == facing ? 0 : MaxwellEngine.faceCenter(layout.depth);
                var inlet = cell.pos.offset(layout.x(x, z), MaxwellEngine.faceCenter(layout.height), layout.z(x, z));
                add(mounted, inlet, new Endpoint(cell, side, tank));
            }
        }
        observers = NativeVehicleObservers.build(snapshot.cells(), states);
        for (var observer : observers) add(mounted, observer.inlet, new Endpoint(states.get(observer.core), observer));
        arms = new NativeVehicleArms(states, registries);
        availableBytes -= arms.reservedBytes();
        if (availableBytes < 0) throw new IllegalArgumentException("Vehicle snapshot has no space for dynamic machine data");
        for (var arm : arms.arms()) {
            var mode = arm.gear.settings().resourceTransfer();
            if (mode == ArmResource.DISABLED) continue;
            var base = mounted.get(arm.cell.pos.relative(arm.gear.mount().getOpposite()));
            if (base != null) armLinks.add(new ArmLink(arm, List.copyOf(base)));
        }
        this.mounted = Map.copyOf(mounted);
        for (var entry : mounted.entrySet()) {
            for (var source : entry.getValue()) for (var side : Direction.values()) {
                if (!source.emits(side)) continue;
                var neighbors = mounted.get(entry.getKey().relative(side));
                if (neighbors == null) continue;
                for (var target : neighbors) {
                    if (source.cell.pos.equals(target.cell.pos) || source.kind != target.kind || !target.accepts(side.getOpposite())) continue;
                    if (source.config != null || target.config != null && target.config.settings().pullInputs) {
                        routes.add(new Route(source, target));
                    }
                }
            }
        }
    }

    private void add(Map<BlockPos, List<Endpoint>> mounted, BlockPos pos, Endpoint endpoint) {
        mounted.computeIfAbsent(pos, unused -> new ArrayList<>()).add(endpoint);
    }

    List<NativeVehicleObservers.Receiver> observers() { return observers; }

    double fluidMass() { return fluidMassMb / 1000D; }

    static double fluidMass(NativeTransferSnapshot snapshot, HolderLookup.Provider registries) {
        long amount = 0;
        for (var cell : snapshot.cells()) {
            var block = cell.state.getBlock();
            if (block instanceof NativeBufferBlock buffer && !buffer.battery) {
                amount += fluidContents(cell.tile(), "NativeTankFluid", NativeHexTankEntity.CAPACITY, registries).getAmount();
            } else if (block instanceof NativePortBlock port && port.kind == NativePortKind.FLUID) {
                amount += fluidContents(cell.tile(), "PortFluid", NativeFluidPortEntity.CAPACITY, registries).getAmount();
            }
        }
        return amount / 1000D;
    }

    void tick(NativeGroundVehicle craft) {
        if (!(craft.level() instanceof ServerLevel level) || tick == level.getGameTime()) return;
        tick = level.getGameTime();
        for (var observer : observers) observer.sample(craft, this);
        arms.tick(craft, this);
        int armCount = armLinks.size(), armWork = Math.min(ROUTES_PER_TICK, armCount);
        for (int i = 0; i < armWork; i++) {
            relay(craft, armLinks.get((nextArm + i) % armCount), level.registryAccess());
        }
        if (armCount != 0) nextArm = (nextArm + (armWork == armCount ? 1 : armWork)) % armCount;
        if (routes.isEmpty()) return;
        int size = routes.size(), work = Math.min(ROUTES_PER_TICK, size);
        for (int i = 0; i < work; i++) {
            var route = routes.get((next + i) % size);
            transfer(craft, route.source, route.target, level.registryAccess());
        }
        next = (next + (work == size ? 1 : work)) % size;
    }

    boolean armsReadyToPark() { return arms.readyToPark(); }
    int armRevision() { return arms.revision(); }
    CompoundTag armPoses() { return arms.poses(); }
    List<net.minecraft.world.phys.AABB> armCollision() { return arms.collision(); }

    private void relay(NativeGroundVehicle craft, ArmLink link, HolderLookup.Provider registries) {
        var gear = link.arm.gear;
        var contact = NativeGearResourceTransfer.carriedEndpoint(gear);
        if (contact == null) return;
        var far = mounted.get(contact.cell());
        if (far == null) return;
        var kind = switch (gear.settings().resourceTransfer()) {
            case POWER -> NativePortKind.POWER;
            case FLUID -> NativePortKind.FLUID;
            case ITEM -> NativePortKind.MATTER;
            case DISABLED -> null;
        };
        boolean inward = gear.settings().resourceOut();
        var sources = inward ? far : link.base;
        var receivers = inward ? link.base : far;
        var from = inward ? contact.face() : gear.mount();
        var to = inward ? gear.mount() : contact.face();
        for (var source : sources) {
            if (source.kind != kind || !source.emits(from)) continue;
            for (var target : receivers) {
                if (source.cell.pos.equals(target.cell.pos) || target.kind != kind || !target.accepts(to)) continue;
                if (source.config != null || target.config != null && target.config.settings().pullInputs) {
                    transfer(craft, source, target, registries);
                }
            }
        }
    }

    private void transfer(NativeGroundVehicle craft, Endpoint source, Endpoint target, HolderLookup.Provider registries) {
        prepare(craft, source);
        prepare(craft, target);
        if (!source.active || !target.active || source.remaining == 0) return;
        int moved = switch (source.kind) {
            case POWER -> power(source, target);
            case FLUID -> fluid(source, target, registries);
            case MATTER -> items(source, target, registries);
        };
        source.remaining -= moved;
    }

    private void prepare(NativeGroundVehicle craft, Endpoint endpoint) {
        if (endpoint.budgetTick == tick) return;
        endpoint.budgetTick = tick;
        endpoint.remaining = endpoint.limit;
        endpoint.active = endpoint.config == null || activated(craft, endpoint);
    }

    private boolean activated(NativeGroundVehicle craft, Endpoint endpoint) {
        var config = endpoint.config;
        if (config.redstoneMode() == 0) return true;
        int signal = config.channels().isEmpty() ? localLevel(endpoint.cell.pos) : channelLevel(craft, endpoint.cell.pos, config.channels());
        return (signal > 0) == (config.redstoneMode() == 1);
    }

    int localLevel(BlockPos receiver) {
        int level = 0;
        for (var side : Direction.values()) {
            var adjacent = receiver.relative(side);
            var neighbor = states.get(adjacent);
            if (neighbor != null && neighbor.state.is(Blocks.REDSTONE_BLOCK)) return 15;
            var output = outputPositions.get(adjacent);
            if (output != null) level = Math.max(level, output.level());
            level = Math.max(level, controls.localOutput(adjacent));
        }
        return level;
    }

    boolean hasLocalOutputs() { return !outputPositions.isEmpty() || controls.hasLocalOutput(); }

    int channelLevel(NativeGroundVehicle craft, BlockPos receiver, ChannelList selected) {
        if (selected.isEmpty()) return 0;
        var world = (ServerLevel)craft.level();
        var physical = BlockPos.containing(NativeVehicleObservers.worldPoint(craft, receiver));
        int signal = controls.channelOutput(receiver, selected);
        if (signal == 15) return signal;
        for (int i = 0; i < selected.size(); i++) {
            int channel = selected.get(i);
            signal = Math.max(signal, NativeRedstoneChannels.level(world, physical, channel));
            if (signal == 15) break;
            var outputs = outputChannels.get(channel);
            if (outputs != null) for (var output : outputs) {
                var at = output.source.cell.pos;
                if (ChannelSpatialPolicy.finiteContains(at.getX(), at.getY(), at.getZ(),
                    receiver.getX(), receiver.getY(), receiver.getZ(), output.range)) signal = Math.max(signal, output.level());
            }
            if (signal == 15) break;
        }
        return signal;
    }

    NativeTransferSnapshot snapshot(NativeTransferSnapshot withPropulsion, HolderLookup.Provider registries) {
        if (updates.isEmpty() && arms.revision() == 0) return withPropulsion;
        if (!changed && savedInput == withPropulsion && savedArmRevision == arms.revision()) return saved;
        var cells = new ArrayList<NativeRawCell>(withPropulsion.cells().size());
        for (var cell : withPropulsion.cells()) cells.add(arms.updated(updates.getOrDefault(cell.pos, cell), registries));
        savedArmRevision = arms.revision();
        saved = new NativeTransferSnapshot(cells, NativeVehicleStructure.LIMITS);
        savedInput = withPropulsion;
        changed = false;
        return saved;
    }

    private int power(Endpoint from, Endpoint to) {
        if (to.observer != null) {
            if (!to.observer.requested) return 0;
            int received = to.observer.power.receive(tick, Math.min(from.remaining, from.energy), false);
            if (received > 0) {
                from.energy -= received;
                var tag = from.cell.tile();
                tag.putInt(from.key, from.energy);
                store(from, tag);
            }
            return received;
        }
        int amount = Math.min(from.remaining, Math.min(from.energy, to.capacity - to.energy));
        if (amount <= 0) return 0;
        var first = from.cell.tile();
        var second = to.cell.tile();
        first.putInt(from.key, from.energy - amount);
        second.putInt(to.key, to.energy + amount);
        if (!checkBytes(from, first, to, second)) return 0;
        from.energy -= amount;
        to.energy += amount;
        store(from, first);
        store(to, second);
        return amount;
    }

    private int fluid(Endpoint from, Endpoint to, HolderLookup.Provider registries) {
        var current = from.fluid;
        if (current.isEmpty()) return 0;
        int offered = Math.min(from.remaining, current.getAmount());
        int amount;
        if (to.fuel != null) {
            amount = to.inlet.fill(current.copyWithAmount(offered), IFluidHandler.FluidAction.SIMULATE);
        } else {
            if (!to.fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(current, to.fluid)) return 0;
            amount = Math.min(offered, to.capacity - to.fluid.getAmount());
        }
        if (amount <= 0) return 0;
        var left = current.copyWithAmount(current.getAmount() - amount);
        var oldTarget = to.fuel == null ? to.fluid : to.fuel.contents();
        var filled = current.copyWithAmount(oldTarget.getAmount() + amount);
        var first = fluidTag(from.cell, from.key, left, registries);
        var second = fluidTag(to.cell, to.key, filled, registries);
        if (!checkBytes(from, first, to, second)) return 0;
        if (to.fuel != null) {
            if (to.inlet.fill(current.copyWithAmount(amount), IFluidHandler.FluidAction.EXECUTE) != amount) {
                throw new IllegalStateException("Carried Maxwell inlet changed during local transfer");
            }
            propulsion.fuelChanged();
            fluidMassMb -= amount;
            recordGrowth(to, second);
        } else {
            to.fluid = filled;
            store(to, second);
        }
        from.fluid = left;
        store(from, first);
        return amount;
    }

    private int items(Endpoint from, Endpoint to, HolderLookup.Provider registries) {
        var source = from.inventory;
        var target = to.inventory;
        for (int n = 0; n < source.size(); n++) {
            int slot = (from.itemCursor + n) % source.size();
            var stack = source.get(slot);
            if (stack.isEmpty()) continue;
            int remaining = Math.min(stack.getCount(), from.remaining);
            var nextTarget = NonNullList.withSize(target.size(), ItemStack.EMPTY);
            for (int i = 0; i < target.size(); i++) nextTarget.set(i, target.get(i));
            for (int i = 0; i < target.size() && remaining > 0; i++) {
                var old = target.get(i);
                if (!old.isEmpty() && !ItemStack.isSameItemSameComponents(old, stack)) continue;
                int take = Math.min(remaining, Math.min(64, stack.getMaxStackSize()) - old.getCount());
                if (take <= 0) continue;
                nextTarget.set(i, stack.copyWithCount(old.getCount() + take));
                remaining -= take;
            }
            int moved = Math.min(stack.getCount(), from.remaining) - remaining;
            if (moved == 0) continue;
            var nextSource = NonNullList.withSize(source.size(), ItemStack.EMPTY);
            for (int i = 0; i < source.size(); i++) nextSource.set(i, source.get(i));
            nextSource.set(slot, stack.copyWithCount(stack.getCount() - moved));
            CompoundTag first, second;
            try {
                first = itemTag(from, nextSource, registries);
                second = itemTag(to, nextTarget, registries);
            } catch (IllegalArgumentException unsupported) {
                continue;
            }
            if (!checkBytes(from, first, to, second)) continue;
            from.inventory = nextSource;
            to.inventory = nextTarget;
            from.itemCursor = (slot + 1) % source.size();
            store(from, first);
            store(to, second);
            return moved;
        }
        return 0;
    }

    private boolean checkBytes(Endpoint a, CompoundTag first, Endpoint b, CompoundTag second) {
        int max = NativeVehicleStructure.LIMITS.tileBytes();
        return first.sizeInBytes() <= max && second.sizeInBytes() <= max - (b.fuel == null ? 0 : MAXWELL_DYNAMIC_BYTES)
            && extraBytes + growthDelta(a, first) + growthDelta(b, second) <= availableBytes;
    }

    private int growthDelta(Endpoint endpoint, CompoundTag next) {
        return Math.max(0, next.sizeInBytes() - endpoint.originalBytes) - growth.getOrDefault(endpoint.cell.pos, 0);
    }

    private void recordGrowth(Endpoint endpoint, CompoundTag tag) {
        extraBytes += growthDelta(endpoint, tag);
        growth.put(endpoint.cell.pos, Math.max(0, tag.sizeInBytes() - endpoint.originalBytes));
    }

    private void store(Endpoint endpoint, CompoundTag tag) {
        recordGrowth(endpoint, tag);
        var cell = endpoint.cell;
        updates.put(cell.pos, new NativeRawCell(cell.pos, cell.state, tag, NativeVehicleStructure.LIMITS.tileBytes()));
        changed = true;
    }

    private static FluidStack fluidContents(CompoundTag raw, String key, int capacity, HolderLookup.Provider registries) {
        if (!raw.contains(key)) return FluidStack.EMPTY;
        var fluid = FluidStack.parse(registries, raw.get(key))
            .orElseThrow(() -> new IllegalArgumentException("Invalid carried tank contents"));
        if (fluid.isEmpty() || fluid.getAmount() > capacity || !fluid.save(registries).equals(raw.get(key))) {
            throw new IllegalArgumentException("Invalid carried tank identity or amount");
        }
        return fluid;
    }

    private static CompoundTag fluidTag(NativeRawCell cell, String key, FluidStack contents, HolderLookup.Provider registries) {
        var tag = cell.tile();
        if (contents.isEmpty()) tag.remove(key);
        else tag.put(key, contents.save(registries));
        return tag;
    }

    private static CompoundTag itemTag(Endpoint endpoint, NonNullList<ItemStack> items, HolderLookup.Provider registries) {
        var tag = endpoint.cell.tile();
        if (endpoint.config != null) {
            var rows = new ListTag();
            for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) {
                var entry = new CompoundTag();
                entry.putInt("Slot", i);
                entry.put("Item", items.get(i).save(registries));
                rows.add(entry);
            }
            tag.put("PortItems", rows);
            Endpoint.readMatter(tag, NonNullList.withSize(items.size(), ItemStack.EMPTY), registries);
        } else if (endpoint.cell.state.getBlock() instanceof NativeStorageBlock) {
            NativeStorageInventory.write(tag, items, registries);
            NativeStorageInventory.read(tag, registries, NativeStorageInventory.empty());
        } else {
            ContainerHelper.saveAllItems(tag, items, registries);
        }
        return tag;
    }

    private record Route(Endpoint source, Endpoint target) {}
    private record ArmLink(NativeVehicleArms.Arm arm, List<Endpoint> base) {}

    private record BufferSignal(Endpoint source, ChannelList channels, int range, boolean local) {
        static BufferSignal read(Endpoint source, NativeBufferBlock buffer) {
            var main = source.cell.tile().getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");
            var channels = TransmissionSettings.readChannels(main.contains("Channels", Tag.TAG_INT_ARRAY),
                main.getIntArray("Channels"), ChannelList.EMPTY);
            int range = TransmissionSettings.readRange(main.contains("Range", Tag.TAG_INT), main.getInt("Range"), true);
            boolean local = TransmissionSettings.readLocal(main.contains("Local", Tag.TAG_BYTE), main.getBoolean("Local"), !buffer.battery);
            return new BufferSignal(source, channels, range, local);
        }

        int level() {
            int amount = source.kind == NativePortKind.POWER ? source.energy : source.fluid.getAmount();
            return NativeBufferOutput.level(amount, source.capacity);
        }
    }

    private static final class Endpoint {
        final NativeRawCell cell;
        final NativePortKind kind;
        final NativePortData.Configuration config;
        final String key;
        final int capacity, limit, originalBytes;
        Direction.Axis axis;
        Direction inletSide;
        NativeMaxwellTank fuel;
        NativeVehicleObservers.Receiver observer;
        IFluidHandler inlet;
        int remaining, energy, itemCursor;
        long budgetTick = Long.MIN_VALUE;
        boolean active = true;
        FluidStack fluid = FluidStack.EMPTY;
        NonNullList<ItemStack> inventory;

        Endpoint(NativeRawCell cell, NativePortKind kind, NativePortData.Configuration config, String key, int capacity) {
            this.cell = cell;
            this.kind = kind;
            this.config = config;
            this.key = key;
            this.capacity = capacity;
            var raw = cell.tile();
            originalBytes = raw.sizeInBytes();
            long requested = config == null ? 0 : config.settings().throughput;
            int legacy = raw.getInt("BatteryLegacyRate");
            limit = Math.min(legacy == 0 ? Integer.MAX_VALUE : legacy,
                (int)Math.min(Integer.MAX_VALUE, requested == 0 ? Integer.MAX_VALUE : requested));
            remaining = limit;
        }

        Endpoint(NativeRawCell cell, Direction inletSide, NativeMaxwellTank tank) {
            this(cell, NativePortKind.FLUID, null, NativeMaxwellTank.FUEL_TAG, MaxwellEngine.CAPACITY);
            this.inletSide = inletSide;
            fuel = tank;
            inlet = tank.inlet(() -> true, () -> {});
        }

        Endpoint(NativeRawCell cell, NativeVehicleObservers.Receiver observer) {
            this(cell, NativePortKind.POWER, null, "", com.vandorlabs.navigation.ObserverPower.RF_PER_TICK);
            this.inletSide = Direction.DOWN;
            this.observer = observer;
        }

        boolean emits(Direction face) {
            if (inletSide != null) return false;
            return config == null ? axis == null || axis == face.getAxis() : config.settings().face(face.get3DDataValue()) == Face.OUTPUT;
        }

        boolean accepts(Direction face) {
            if (inletSide != null) return inletSide == face;
            return config == null ? axis == null || axis == face.getAxis() : config.settings().face(face.get3DDataValue()) == Face.INPUT;
        }

        static Endpoint owned(NativeRawCell cell, HolderLookup.Provider registries) {
            var block = cell.state.getBlock();
            var raw = cell.tile();
            Endpoint result;
            if (block instanceof NativeBufferBlock buffer) {
                result = buffer.battery
                    ? new Endpoint(cell, NativePortKind.POWER, NativePortData.read(raw), "Energy", NativeBatteryEntity.CAPACITY)
                    : new Endpoint(cell, NativePortKind.FLUID, null, "NativeTankFluid", NativeHexTankEntity.CAPACITY);
                if (!buffer.battery) result.axis = cell.state.getValue(NativeBufferBlock.FACING).getAxis();
            } else if (block instanceof NativePortBlock port) {
                var config = NativePortData.read(raw);
                result = switch (port.kind) {
                    case POWER -> new Endpoint(cell, port.kind, config, "PortEnergy", NativePowerPortEntity.CAPACITY);
                    case FLUID -> new Endpoint(cell, port.kind, config, "PortFluid", NativeFluidPortEntity.CAPACITY);
                    case MATTER -> new Endpoint(cell, port.kind, config, "PortItems", NativeMatterPortEntity.SLOTS);
                };
            } else if (block instanceof NativeStorageBlock || block instanceof BarrelBlock || block instanceof ChestBlock) {
                if (raw.contains("LootTable")) throw new IllegalArgumentException("Open the loot container before moving it");
                result = new Endpoint(cell, NativePortKind.MATTER, null, "Items", 27);
            } else return null;

            switch (result.kind) {
                case POWER -> {
                    if (raw.contains(result.key) && !raw.contains(result.key, Tag.TAG_INT)) {
                        throw new IllegalArgumentException("Invalid carried energy type");
                    }
                    result.energy = raw.getInt(result.key);
                    if (result.energy < 0 || result.energy > result.capacity) throw new IllegalArgumentException("Invalid carried energy amount");
                }
                case FLUID -> result.fluid = fluidContents(raw, result.key, result.capacity, registries);
                case MATTER -> {
                    result.inventory = NonNullList.withSize(result.capacity, ItemStack.EMPTY);
                    if (block instanceof NativeStorageBlock) result.inventory = NativeStorageInventory.read(raw, registries, result.inventory);
                    else if (result.config != null) readMatter(raw, result.inventory, registries);
                    else ContainerHelper.loadAllItems(raw, result.inventory, registries);
                }
            }
            return result;
        }

        private static void readMatter(CompoundTag raw, NonNullList<ItemStack> items, HolderLookup.Provider registries) {
            if (raw.contains("PortItems") && !raw.contains("PortItems", Tag.TAG_LIST)) throw new IllegalArgumentException("Invalid carried matter inventory");
            var rows = raw.getList("PortItems", Tag.TAG_COMPOUND);
            if (raw.get("PortItems") instanceof ListTag list && rows != list && !list.isEmpty()
                || rows.size() > items.size()) throw new IllegalArgumentException("Invalid carried matter rows");
            var budget = new com.vandorlabs.neoforge.inventory.NativeInventoryItems.Budget();
            for (var item : rows) {
                var row = (CompoundTag)item;
                int slot = row.getInt("Slot");
                if (!row.contains("Slot", Tag.TAG_INT) || !row.contains("Item", Tag.TAG_COMPOUND)
                    || slot < 0 || slot >= items.size() || !items.get(slot).isEmpty()) {
                    throw new IllegalArgumentException("Invalid carried matter slot");
                }
                var stack = com.vandorlabs.neoforge.inventory.NativeInventoryItems.modern(row.getCompound("Item"), registries, budget);
                if (stack.isEmpty() || stack.getCount() > Math.min(64, stack.getMaxStackSize())) {
                    throw new IllegalArgumentException("Invalid carried matter stack");
                }
                items.set(slot, stack);
            }
        }
    }
}
