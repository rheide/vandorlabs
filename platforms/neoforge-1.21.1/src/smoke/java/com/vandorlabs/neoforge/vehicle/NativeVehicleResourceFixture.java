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
import java.util.List;
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
    private static final boolean MEKANISM = Boolean.getBoolean("vandorlabs.vehicleMekanismTank");
    private static final int MEKANISM_MB = 67;
    private static final int POWER = 777, DRAIN = 60;
    private final ServerLevel world;
    private final BlockPos root, engine, powerSource, powerSink, tank, pump, matterSource, matterSink, observer, observerBattery;
    private final ChannelList enabled = ChannelList.of(702), survey = ChannelList.of(703);
    private final ChannelList batteryFullness = ChannelList.of(704), tankFullness = ChannelList.of(705);
    private final BlockPos monitor;
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
            if (MEKANISM) settings = settings.withFace(Direction.EAST.get3DDataValue(), ResourcePortSettings.Face.INPUT);
            NativePortData.write(raw, new NativePortData.Configuration(settings, 1, enabled));
        });
        put(tank, NativeBufferBindings.TANK.defaultBlockState().setValue(NativeBufferBlock.FACING, Direction.DOWN));
        edit(tank, raw -> {
            raw.put("NativeTankFluid", new FluidStack(fuel, FUEL_REFILL - (MEKANISM ? MEKANISM_MB : 0)).save(world.registryAccess()));
            transmit(raw, tankFullness);
        });
        if (MEKANISM) placeMekanismTank(fuel);
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
        monitor=matterSink.above();
        put(monitor,com.vandorlabs.neoforge.screen.NativeScreenRegistrations.BLOCKS.get(com.vandorlabs.screen.ScreenFamily.SCREEN).get().defaultBlockState());
        edit(monitor,raw->{
            var settings=new CompoundTag();var rows=new net.minecraft.nbt.ListTag();
            for(var bank:List.of(batteryFullness,tankFullness)){
                var row=new CompoundTag();row.putString("Label","Fullness");row.putBoolean("Status",true);
                row.putIntArray("RedstoneChannels",bank.toArray());rows.add(row);
            }
            settings.putString("Title","Stores");settings.putBoolean("Enabled",true);settings.put("Rows",rows);
            raw.put("RedstonePrimary",settings);raw.putInt("PrimarySurfaceTexture",-2);
        });
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
            if(craft.meterSurface(monitor.subtract(root),0)!=0x11)return false;
            var appeared=NativeVehicleVisual.project(snapshot,world.registryAccess());
            var publicCell=NativeVehicleVisual.read(appeared,world.registryAccess()).cells().stream()
                .filter(cell->cell.pos().equals(monitor.subtract(root))).findFirst().orElseThrow();
            var exposed=publicCell.appearance().getCompound("RedstonePrimary").getList("Rows",10);
            if(exposed.size()!=2||!exposed.getCompound(0).getBoolean("Status")||exposed.getCompound(0).contains("RedstoneChannels"))
                throw new AssertionError("Public meter projection must retain bars and exclude channel assignments");
            if (!NativeRedstoneChannels.manualToggle(actor, transmitter)) throw new AssertionError("Could not publish the native transmitter");
            on = true;
            return false;
        }
        var snapshot = craft.snapshot();
        var cells = snapshot.cells();
        int received = data(cells, powerSink).getInt("PortEnergy");
        var source = data(cells, powerSource).getInt("Energy");
        int tankMb = amount(data(cells, tank), "NativeTankFluid");
        if (MEKANISM) tankMb += NativeVehicleMekanismTank.contents(data(cells, pump.east()), world.registryAccess()).getAmount();
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
        if(craft.meterSurface(monitor.subtract(root),0)!=0)return false;
        if (!radar) throw new AssertionError("Carried RF was consumed without an actual Survey Dish scan");
        if (craft.resources().channelLevel(craft, matterSource.subtract(root), batteryFullness) != 0
            || craft.resources().channelLevel(craft, matterSource.subtract(root), tankFullness) != 0
            || craft.resources().localLevel(pump.subtract(root)) != 0) {
            throw new AssertionError("The depleted onboard outputs do not go low");
        }
        if (!NativeRedstoneChannels.manualToggle(actor, transmitter)) throw new AssertionError("Could not stop the native transmitter");
        return true;
    }

    private void placeMekanismTank(Fluid fuel) {
        var pos = pump.east();
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.parse("mekanism:basic_fluid_tank"));
        if (!NativeVehicleMekanismTank.supports(block.defaultBlockState())) throw new AssertionError("Installed Mekanism tank missing");
        put(pos, block.defaultBlockState());
        var tile = world.getBlockEntity(pos);
        var controller = (NativeVehicleControllerEntity) world.getBlockEntity(root);
        var otherOwner = new java.util.UUID(1, 2);
        edit(pos, raw -> {
            var security = raw.getCompound("component_security");
            security.putUUID("owner", otherOwner);
            security.putInt("security_mode", 1);
            raw.put("component_security", security);
        });
        boolean rejected = false;
        try {
            NativeVehicleMekanismAccess.require(controller.ownerId(), world, pos, tile.getBlockState(), tile);
        } catch (IllegalArgumentException denied) {
            rejected = denied.getMessage().startsWith("Mekanism security refused");
        }
        if (!rejected) throw new AssertionError("A different owner's private Mekanism tank was accessible");
        edit(pos, raw -> raw.getCompound("component_security").putInt("security_mode", 0));
        NativeVehicleMekanismAccess.require(controller.ownerId(), world, pos, tile.getBlockState(), tile);
        tile.getPersistentData().putString("FixtureReceipt", "finite tank");
        var fluid = world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, Direction.WEST);
        if (fluid == null || fluid.fill(new FluidStack(fuel, MEKANISM_MB),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != MEKANISM_MB) {
            throw new AssertionError("Genuine Mekanism capability did not store the flight fuel");
        }
    }

    void parked(BlockPos anchor) {
        if (MEKANISM) {
            var pos = parked(pump.east(), anchor);
            var tile = world.getBlockEntity(pos);
            var fluid = world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
            if (tile == null || !NativeVehicleMekanismTank.supports(tile) || fluid == null || !fluid.getFluidInTank(0).isEmpty()
                    || !tile.getPersistentData().getString("FixtureReceipt").equals("finite tank")) {
                throw new AssertionError("The rotated Mekanism tank must contain exactly its remaining physical contents");
            }
            System.out.println("[vandorlabs][vehicle-transfer] installed Mekanism tank PASS " + MEKANISM_MB
                + " mB through onboard pump to Maxwell; unique parked store");
        }
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
