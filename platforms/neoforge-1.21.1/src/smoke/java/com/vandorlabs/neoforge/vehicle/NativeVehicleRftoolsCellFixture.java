package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.ports.NativePortBindings;
import com.vandorlabs.neoforge.ports.NativePortBlock;
import com.vandorlabs.neoforge.ports.NativePortData;
import com.vandorlabs.neoforge.ports.NativePortKind;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Installed finite Dimensional Cells with independent, exactly conserved Forge Energy. */
final class NativeVehicleRftoolsCellFixture {
    private static final int FROM_CELL = 91, TO_CELL = 117;
    private final ServerLevel world;
    private final BlockPos root, source, target, producer, receiver, blank;
    private final IEnergyStorage sourceInput;
    private IItemHandlerModifiable cachedItems;
    private boolean held;

    NativeVehicleRftoolsCellFixture(ServerLevel world, BlockPos root, ChannelList channels) {
        this.world = world;
        this.root = root;
        source = root.north(3).above();
        target = source.west();
        receiver = source.east();
        producer = target.west();
        blank = source.north();
        for (int z = 1; z <= 3; z++) put(root.north(z), Blocks.IRON_BLOCK.defaultBlockState());
        var cell = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("rftoolspower:dimensionalcell")).defaultBlockState();
        var simple = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("rftoolspower:dimensionalcell_simple")).defaultBlockState();
        var advanced = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("rftoolspower:dimensionalcell_advanced")).defaultBlockState();
        var networked = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("rftoolspower:cell1")).defaultBlockState();
        need(NativeVehicleRftoolsCell.supports(cell) && NativeVehicleRftoolsCell.supports(simple)
            && NativeVehicleRftoolsCell.supports(advanced)
            && !networked.isAir() && !NativeVehicleRftoolsCell.supports(networked), "installed finite tier whitelist excludes the regular network Cell");
        put(source, side(side(cell, Direction.WEST, "input"), Direction.EAST, "output"));
        put(target, side(simple, Direction.WEST, "input"));
        put(blank, advanced);
        sourceInput = energy(source, Direction.WEST);
        need(sourceInput != null && sourceInput.getMaxEnergyStored() == 1_000_000
            && sourceInput.receiveEnergy(6000, true) == 5000 && sourceInput.getEnergyStored() == 0
            && sourceInput.receiveEnergy(FROM_CELL, false) == FROM_CELL
            && energy(source, Direction.NORTH).receiveEnergy(1, true) == 0
            && contents(source) == FROM_CELL, "real sided live capability primes only local energy");
        port(producer, Direction.EAST, true, TO_CELL, channels);
        port(receiver, Direction.WEST, false, 0, channels);
    }

    void verifyRefusals() {
        var handler = world.getCapability(Capabilities.ItemHandler.BLOCK, target, Direction.WEST);
        need(handler instanceof IItemHandlerModifiable, "actual native storage-card inventory");
        cachedItems = (IItemHandlerModifiable)handler;
        setSecurityChannel(19);
        refuse("RFTools Cell belongs to a different owner or security channel");
        setSecurityChannel(-1);
        cachedItems.setStackInSlot(0, card());
        refuse("Unlink the RFTools Dimensional Cell");
        cachedItems.setStackInSlot(0, ItemStack.EMPTY);
        cachedItems.setStackInSlot(1, card());
        refuse("Empty the RFTools Cell's card and charging slots");
        cachedItems.setStackInSlot(1, ItemStack.EMPTY);
        need(contents(target) == 0, "linked and card-bearing refusal preserved the local owner");
    }

    void held() {
        need(world.getBlockEntity(source) != null && sourceInput.receiveEnergy(7, false) == 0,
            "the previously obtained physical energy handler is frozen");
        cachedItems.setStackInSlot(0, card());
        cachedItems.setStackInSlot(1, card());
        need(cachedItems.getStackInSlot(0).isEmpty() && cachedItems.getStackInSlot(1).isEmpty(),
            "the previously obtained inventory cannot link the held cell or insert a card");
        held = true;
    }

    private static ItemStack card() {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("rftoolspower:powercell_card")));
    }

    private void setSecurityChannel(int channel) {
        var tile = world.getBlockEntity(target);
        try {
            tile.getClass().getMethod("setSecurityChannel", int.class).invoke(tile, channel);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Installed RFTools security channel setter", failure);
        }
    }

    void before(NativeGroundVehicle craft) {
        need(held && sourceInput.receiveEnergy(7, false) == 0 && carried(craft, source) == FROM_CELL
            && carried(craft, target) == 0 && carried(craft, blank) == 0 && carried(craft, producer) == TO_CELL && carried(craft, receiver) == 0,
            "no early transfer or writes through an old cell handler");
    }

    boolean transferred(NativeGroundVehicle craft) {
        int given = carried(craft, receiver), received = carried(craft, target);
        need(carried(craft, source) + given == FROM_CELL && carried(craft, producer) + received == TO_CELL,
            "both independent FE ownership sums remain exact");
        return given == FROM_CELL && received == TO_CELL;
    }

    void parked(BlockPos anchor) {
        var from = parked(anchor, source);
        var to = parked(anchor, target);
        var in = energy(from, Direction.NORTH);
        need(in != null && in.getEnergyStored() == 0 && energy(to, Direction.NORTH).getEnergyStored() == TO_CELL
            && energy(from, Direction.SOUTH).receiveEnergy(7, true) == 0,
            "rotated standalone side permissions and exact buffer owners");
        need(in.receiveEnergy(7, true) == 7 && contents(from) == 0
            && in.receiveEnergy(7, false) == 7 && contents(from) == 7 && sourceInput.receiveEnergy(7, false) == 0,
            "new parked handler resumes real writes and stale original stays frozen");
        need(raw(parked(anchor, receiver)).getInt("PortEnergy") == FROM_CELL
            && raw(parked(anchor, producer)).getInt("PortEnergy") == 0, "physical native RF endpoints are retained exactly");
        need(contents(parked(anchor, blank)) == 0, "the untouched Advanced Cell has one empty parked owner");
        System.out.println("[vandorlabs][vehicle-transfer] installed RFTools cells PASS 91 FE out and 117 FE in, "
            + "three finite tiers, security/card/link refusal, stale energy and card guards, rotated physical intake");
    }

    private void refuse(String expected) {
        try {
            NativeVehicleScan.scan(world, root, ((NativeVehicleControllerEntity)world.getBlockEntity(root)).ownerId());
        } catch (IllegalArgumentException refused) {
            need(refused.getMessage().contains(expected), "expected " + expected + ", got " + refused.getMessage());
            return;
        }
        throw new AssertionError("RFTools Cell fixture expected refusal: " + expected);
    }

    private void port(BlockPos at, Direction face, boolean output, int amount, ChannelList bank) {
        put(at, NativePortBindings.BLOCKS.get(NativePortKind.POWER).defaultBlockState().setValue(NativePortBlock.FACING, face));
        var raw = raw(at);
        var settings = ResourcePortSettings.placed(face.getOpposite().get3DDataValue())
            .withFace(face.getOpposite().get3DDataValue(), ResourcePortSettings.Face.DISABLED)
            .withFace(face.get3DDataValue(), output ? ResourcePortSettings.Face.OUTPUT : ResourcePortSettings.Face.INPUT);
        NativePortData.write(raw, new NativePortData.Configuration(settings, 1, bank));
        raw.putInt("PortEnergy", amount);
        world.getBlockEntity(at).loadWithComponents(raw, world.registryAccess());
    }

    private static BlockState side(BlockState state, Direction direction, String name) {
        return side(state, state.getBlock().getStateDefinition().getProperty(direction.getName()), name);
    }

    private static <T extends Comparable<T>> BlockState side(BlockState state, Property<T> property, String name) {
        return state.setValue(property, property.getValue(name).orElseThrow());
    }

    private void put(BlockPos pos, BlockState state) { world.setBlock(pos, state, Block.UPDATE_ALL); }

    private IEnergyStorage energy(BlockPos pos, Direction direction) {
        return world.getCapability(Capabilities.EnergyStorage.BLOCK, pos, direction);
    }

    private CompoundTag raw(BlockPos pos) { return world.getBlockEntity(pos).saveWithFullMetadata(world.registryAccess()); }

    private int contents(BlockPos pos) { return NativeVehicleRftoolsCell.contents(raw(pos)); }

    private int carried(NativeGroundVehicle craft, BlockPos pos) {
        var cell = craft.snapshot().cells().stream().filter(value -> value.pos.equals(pos.subtract(root))).findFirst().orElseThrow();
        return NativeVehicleRftoolsCell.supports(cell.state) ? NativeVehicleRftoolsCell.contents(cell.tile()) : cell.tile().getInt("PortEnergy");
    }

    private BlockPos parked(BlockPos anchor, BlockPos original) {
        return anchor.offset(original.subtract(root).rotate(Rotation.CLOCKWISE_90));
    }

    private static void need(boolean present, String evidence) {
        if (!present) throw new AssertionError("RFTools Cell fixture: " + evidence);
    }
}
