package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.builders.NativeBuilderFormation;
import com.vandorlabs.neoforge.builders.NativeMaxwellTank;
import com.vandorlabs.neoforge.navigation.observer.*;
import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.neoforge.reservoir.NativeBufferBindings;
import com.vandorlabs.neoforge.reservoir.NativeBufferBlock;
import com.vandorlabs.neoforge.storage.NativeStorageInventory;
import com.vandorlabs.neoforge.storage.NativeStorageRegistrations;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Exact carried flows run with one real external wireless transmitter and native configured owners. */
final class NativeVehicleResourceFixture {
    static final int FUEL_REFILL = 131;
    private static final int POWER = 777, DRAIN = 60;
    private final ServerLevel world;
    private final BlockPos root, engine, powerSource, powerSink, tank, pump, matterSource, matterSink, observer, observerBattery;
    private final ChannelList enabled = ChannelList.of(702), survey = ChannelList.of(703);
    private final ChannelList batteryFullness = ChannelList.of(704), tankFullness = ChannelList.of(705);
    private final ItemStack transmitter;
    private boolean observerRequested, on, radar;

    NativeVehicleResourceFixture(ServerLevel world, BlockPos root, BlockPos engine, Fluid fuel) {
        this.world = world;
        this.root = root;
        this.engine = engine;
        transmitter = NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(), 3);
        NativeTransmitterItem.configure(transmitter, enabled);
        powerSource = root.south().above();
        powerSink = powerSource.south();
        pump = engine.east().above().north();
        tank = pump.above();
        matterSource = root.above();
        matterSink = matterSource.above();
        observer = root.west(3).south().above(2);
        observerBattery = observer.east().south().below();

        battery(powerSource, Direction.NORTH, POWER, 50);
        port(powerSink, NativePortKind.POWER, Direction.NORTH, raw -> {});
        port(pump, NativePortKind.FLUID, Direction.UP, raw -> {
            var settings = ResourcePortSettings.placed(Direction.UP.get3DDataValue())
                .withFace(Direction.DOWN.get3DDataValue(), ResourcePortSettings.Face.DISABLED)
                .withFace(Direction.SOUTH.get3DDataValue(), ResourcePortSettings.Face.OUTPUT)
                .withTransfer(40, true, ResourcePortSettings.OnTexture.TRANSFERRING);
            NativePortData.write(raw, new NativePortData.Configuration(settings, 1, enabled));
        });
        put(tank, NativeBufferBindings.TANK.defaultBlockState().setValue(NativeBufferBlock.FACING, Direction.DOWN));
        edit(tank, raw -> {
            raw.put("NativeTankFluid", new FluidStack(fuel, FUEL_REFILL).save(world.registryAccess()));
            transmit(raw, tankFullness);
        });
        port(matterSource, NativePortKind.MATTER, Direction.DOWN, raw -> {
            var settings = ResourcePortSettings.placed(Direction.DOWN.get3DDataValue()).withTransfer(3, false,
                ResourcePortSettings.OnTexture.TRANSFERRING);
            NativePortData.write(raw, new NativePortData.Configuration(settings, 2, tankFullness));
            var row = new CompoundTag();
            row.putInt("Slot", 0);
            row.put("Item", parcel().save(world.registryAccess()));
            var items = new ListTag();
            items.add(row);
            raw.put("PortItems", items);
        });
        put(matterSink, NativeStorageRegistrations.BLOCK.get().defaultBlockState());
        // Three-cell dish spans x=-3..-1. Its lowest power part is one block above its dedicated battery.
        put(root.west().south(2).above(), Blocks.IRON_BLOCK.defaultBlockState());
        battery(observerBattery, Direction.DOWN, DRAIN, 20);
        var core = NativeObserverBindings.CORES.get("standalone_dish");
        put(observer, core.defaultBlockState());
        var layout = new CoreLayout(3, 3, 3, Direction.NORTH.get3DDataValue(), 0, 0);
        for (int x = 0; x < layout.width; x++) for (int y = 0; y < layout.height; y++) for (int z = 0; z < layout.depth; z++) {
            var pos = observer.offset(layout.x(x, z), y, layout.z(x, z));
            if (!pos.equals(observer)) put(pos, layout.frame(x, y, z)
                ? NativeBuilderFormation.matter().defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    boolean formed(ServerPlayer actor) {
        var core = (NativeObserverEntity)world.getBlockEntity(observer);
        if (!observerRequested) {
            if (!NativeObserverTransactions.request(core, actor, true)) throw new AssertionError("Native Survey Dish formation refused");
            observerRequested = true;
            return false;
        }
        if (!core.formed() || !core.available()) return false;
        if (core.channels().isEmpty()) core.configure(survey, 64);
        return true;
    }

    boolean transferred(NativeGroundVehicle craft, ServerPlayer actor, int initialFuel) {
        if (!on) {
            var snapshot = craft.snapshot();
            var original = data(snapshot.cells(), powerSource);
            if (original.getInt("Energy") != POWER || data(snapshot.cells(), powerSink).getInt("PortEnergy") != 0
                || portCount(data(snapshot.cells(), matterSource)) != parcel().getCount()
                || matterCount(data(snapshot.cells(), matterSink)) != 0) {
                throw new AssertionError("The native redstone gate must hold the RF until conversion");
            }
            var resources = craft.resources();
            if (resources.channelLevel(craft, matterSource.subtract(root), batteryFullness) != 1
                || resources.channelLevel(craft, matterSource.subtract(root), tankFullness) != 1
                || resources.localLevel(pump.subtract(root)) != 1) {
                throw new AssertionError("Onboard battery and tank publish independent exact analog levels");
            }
            if (!NativeRedstoneChannels.manualToggle(actor, transmitter)) throw new AssertionError("Could not publish the native transmitter");
            on = true;
            return false;
        }
        var snapshot = craft.snapshot();
        var cells = snapshot.cells();
        int received = data(cells, powerSink).getInt("PortEnergy");
        var source = data(cells, powerSource).getInt("Energy");
        int tankMb = amount(data(cells, tank), "NativeTankFluid");
        int pumpMb = amount(data(cells, pump), "PortFluid");
        int fuel = NativeMaxwellTank.read(data(cells, engine), world.registryAccess()).amount();
        int storedMatter = matterCount(data(cells, matterSink));
        int heldMatter = portCount(data(cells, matterSource));
        int debit = DRAIN - data(cells, observerBattery).getInt("Energy");
        if (source + received != POWER || fuel + tankMb + pumpMb != initialFuel + FUEL_REFILL
            || storedMatter + heldMatter != parcel().getCount() || debit < 0 || debit > DRAIN || debit % 20 != 0
            || Math.round(craft.resources().fluidMass() * 1000) != tankMb + pumpMb) {
            throw new AssertionError("Carried ownership totals changed during native transfers");
        }
        var location = BlockPos.containing(craft.toWorld(observer.subtract(root)));
        for (var area : NativeNavigationSources.matching(world, location, survey)) {
            if (area.radarRadius() > 0 && area.stationary() == null) radar = true;
        }
        if (received != POWER || fuel != initialFuel + FUEL_REFILL || storedMatter != parcel().getCount() || debit != DRAIN) return false;
        if (!radar) throw new AssertionError("Carried RF was consumed without an actual Survey Dish scan");
        if (craft.resources().channelLevel(craft, matterSource.subtract(root), batteryFullness) != 0
            || craft.resources().channelLevel(craft, matterSource.subtract(root), tankFullness) != 0
            || craft.resources().localLevel(pump.subtract(root)) != 0) {
            throw new AssertionError("The depleted onboard outputs do not go low");
        }
        if (!NativeRedstoneChannels.manualToggle(actor, transmitter)) throw new AssertionError("Could not stop the native transmitter");
        return true;
    }

    void parked(BlockPos anchor) {
        var energy = world.getBlockEntity(parked(powerSink, anchor)).saveWithFullMetadata(world.registryAccess());
        var matter = world.getBlockEntity(parked(matterSink, anchor)).saveWithFullMetadata(world.registryAccess());
        var dish = (NativeObserverEntity) world.getBlockEntity(parked(observer, anchor));
        if (energy.getInt("PortEnergy") != POWER || matterCount(matter) != parcel().getCount()
            || !dish.formed() || dish.facing() != Direction.EAST || !dish.channels().equals(survey)) {
            throw new AssertionError("Parked native resources or Survey Dish differ from their carried owners");
        }
        var layout = dish.layout();
        for (int x = 0; x < layout.width; x++) for (int y = 0; y < layout.height; y++) for (int z = 0; z < layout.depth; z++) {
            if (NativeObserverBindings.ownerAt(world, dish.cell(x, y, z)) != dish) {
                throw new AssertionError("Rotated Survey Dish part owner changed");
            }
        }
    }

    private BlockPos parked(BlockPos at, BlockPos anchor) {
        return anchor.offset(at.subtract(root).rotate(Rotation.CLOCKWISE_90));
    }

    private CompoundTag data(java.util.List<NativeRawCell> cells, BlockPos pos) {
        var local = pos.subtract(root);
        for (var cell : cells) if (cell.pos.equals(local)) return Objects.requireNonNull(cell.tile());
        throw new AssertionError("Missing carried resource cell " + local);
    }

    private int amount(CompoundTag tag, String key) {
        return tag.contains(key) ? FluidStack.parse(world.registryAccess(), tag.get(key)).orElseThrow().getAmount() : 0;
    }

    private int matterCount(CompoundTag tag) {
        int amount = 0;
        for (var stack : NativeStorageInventory.read(tag, world.registryAccess(), NativeStorageInventory.empty())) {
            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, parcel())) throw new AssertionError("Warehouse item components changed");
            amount += stack.getCount();
        }
        return amount;
    }

    private int portCount(CompoundTag tag) {
        int amount = 0;
        for (var item : tag.getList("PortItems", Tag.TAG_COMPOUND)) {
            var stack = ItemStack.parseOptional(world.registryAccess(), ((CompoundTag)item).getCompound("Item"));
            if (!ItemStack.isSameItemSameComponents(stack, parcel())) throw new AssertionError("Matter Port item components changed");
            amount += stack.getCount();
        }
        return amount;
    }

    private static ItemStack parcel() {
        var stack = new ItemStack(Items.EMERALD, 7);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Vehicle Matter Port cargo"));
        return stack;
    }

    private void gated(CompoundTag tag, Direction face, int rate) {
        var settings = ResourcePortSettings.placed(face.get3DDataValue()).withTransfer(rate, false,
            ResourcePortSettings.OnTexture.TRANSFERRING);
        NativePortData.write(tag, new NativePortData.Configuration(settings, 1, enabled));
    }

    private void battery(BlockPos pos, Direction face, int energy, int rate) {
        put(pos, NativeBufferBindings.BATTERY.defaultBlockState().setValue(NativeBufferBlock.FACING, face));
        edit(pos, raw -> {
            gated(raw, face, rate);
            raw.putInt("Energy", energy);
            if (pos.equals(powerSource)) transmit(raw, batteryFullness);
        });
    }

    private void transmit(CompoundTag raw, ChannelList channels) {
        var forge = raw.getCompound("ForgeData");
        var all = forge.getCompound(TransmissionSettings.SETTINGS);
        var row = new CompoundTag();
        row.putIntArray("Channels", channels.toArray());
        row.putInt("Range", 16);
        row.putBoolean("Local", true);
        all.put("main", row);
        forge.put(TransmissionSettings.SETTINGS, all);
        raw.put("ForgeData", forge);
    }

    private void port(BlockPos pos, NativePortKind kind, Direction face, Consumer<CompoundTag> change) {
        put(pos, NativePortBindings.BLOCKS.get(kind).defaultBlockState().setValue(NativePortBlock.FACING, face));
        edit(pos, change);
    }

    private void edit(BlockPos pos, Consumer<CompoundTag> change) {
        var owner = Objects.requireNonNull(world.getBlockEntity(pos));
        var contents = owner.saveWithFullMetadata(world.registryAccess());
        change.accept(contents);
        owner.loadWithComponents(contents, world.registryAccess());
        owner.setChanged();
    }

    private void put(BlockPos pos, BlockState block) {
        world.setBlock(pos, block, Block.UPDATE_KNOWN_SHAPE);
    }
}
