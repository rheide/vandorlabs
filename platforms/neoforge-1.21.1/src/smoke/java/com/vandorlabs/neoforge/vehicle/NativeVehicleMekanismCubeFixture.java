package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.ports.NativePortBindings;
import com.vandorlabs.neoforge.ports.NativePortBlock;
import com.vandorlabs.neoforge.ports.NativePortData;
import com.vandorlabs.neoforge.ports.NativePortKind;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Genuine optional cube owners, real sided FE capability and an indivisible Mekanism Joule remainder. */
final class NativeVehicleMekanismCubeFixture {
    private static final int OUTPUT_FE = 8, INPUT_FE = 11;
    private static final long OUTPUT_WITH_REMAINDER = 21L;
    private final ServerLevel world;
    private final BlockPos root, sourceCube, targetCube, inputPort, outputPort, closedPort;
    private final UUID owner;
    private final IEnergyStorage originalOutput;
    private final IEnergyStorage originalTarget;
    private boolean heldChecked;
    private int lastInput = INPUT_FE;

    NativeVehicleMekanismCubeFixture(ServerLevel world, BlockPos root, ChannelList enable) {
        this.world = world;
        this.root = root;
        owner = ((NativeVehicleControllerEntity)world.getBlockEntity(root)).ownerId();
        sourceCube = root.north(2).above();
        inputPort = root.north().above();
        targetCube = inputPort.east();
        outputPort = sourceCube.east();
        closedPort = sourceCube.west();
        put(root.north(), Blocks.IRON_BLOCK.defaultBlockState());
        put(root.north(2), Blocks.IRON_BLOCK.defaultBlockState());
        var source = cube(sourceCube, Direction.EAST, true);
        cube(targetCube, Direction.WEST, false);
        originalOutput = capability(sourceCube, Direction.EAST);
        originalTarget = capability(targetCube, Direction.WEST);
        require(originalOutput != null && originalTarget != null && capability(sourceCube, Direction.NORTH) == null, "sided installed capability");
        var input = capability(sourceCube, Direction.WEST);
        require(input != null && input.receiveEnergy(1, false) == 0 && input.receiveEnergy(OUTPUT_FE, false) == OUTPUT_FE,
            "Mekanism quantizes a genuine default fractional FE bridge");
        require(contents(sourceCube) == 20 && originalOutput.getEnergyStored() == OUTPUT_FE, "real FE converted to stored Joules");
        setStored(source, OUTPUT_WITH_REMAINDER);
        put(sourceCube.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        rawPort(inputPort, Direction.EAST, true, INPUT_FE, enable);
        rawPort(outputPort, Direction.WEST, false, 0, enable);
        rawPort(closedPort, Direction.EAST, false, 0, enable);
    }

    void verifyRefusals() {
        var source = world.getBlockEntity(sourceCube);
        var correct = source.saveWithFullMetadata(world.registryAccess());
        var wrongOwner = correct.copy();
        wrongOwner.getCompound("component_security").putUUID("owner", new UUID(0, 99));
        source.loadWithComponents(wrongOwner, world.registryAccess());
        requireRefusal("Mekanism security refused");
        source.loadWithComponents(correct.copy(), world.registryAccess());

        var occupied = correct.copy();
        var row = new CompoundTag();
        row.putByte("slot", (byte)0);
        var tablet = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:energy_tablet"));
        row.put("item", new ItemStack(tablet).save(world.registryAccess()));
        var rows = new ListTag();
        rows.add(row);
        occupied.put("items", rows);
        source.loadWithComponents(occupied, world.registryAccess());
        require(!packed(sourceCube).getList("items", 10).isEmpty(), "actual Cube retained its charging item");
        requireRefusal("Empty both Mekanism Energy Cube charging slots");
        clearSlots(source);
        source.loadWithComponents(correct, world.registryAccess());
        require(contents(sourceCube) == OUTPUT_WITH_REMAINDER, "failed capture retained the exact source");
    }

    private void requireRefusal(String reason) {
        try {
            NativeVehicleScan.scan(world, root);
        } catch (IllegalArgumentException refused) {
            require(refused.getMessage().contains(reason), "expected refusal " + reason + " got " + refused.getMessage());
            return;
        }
        throw new AssertionError("Expected Cube capture refusal: " + reason);
    }

    void held() {
        require(world.getBlockEntity(sourceCube) != null, "cube must still be physical when held");
        require(originalOutput.extractEnergy(2, false) == 0 && originalTarget.receiveEnergy(2, false) == 0,
            "retained physical Mekanism capability cannot spend energy while held");
        heldChecked = true;
    }

    void before(NativeGroundVehicle craft) {
        require(heldChecked && originalOutput.extractEnergy(2, false) == 0 && originalTarget.receiveEnergy(2, false) == 0,
            "removed Cube wrappers cannot spend energy");
        require(carriedJoules(craft, sourceCube) == OUTPUT_WITH_REMAINDER && carriedJoules(craft, targetCube) == 0
            && carriedFe(craft, outputPort) == 0 && carriedFe(craft, inputPort) == INPUT_FE,
            "wireless gate keeps both mixed directions at their initial amounts");
    }

    boolean transferred(NativeGroundVehicle craft) {
        int output = carriedFe(craft, outputPort), remaining = carriedFe(craft, inputPort);
        long from = carriedJoules(craft, sourceCube), to = carriedJoules(craft, targetCube);
        require(output % 2 == 0 && from + output / 2L * 5 == OUTPUT_WITH_REMAINDER
            && (INPUT_FE - remaining) % 2 == 0 && to == (INPUT_FE - remaining) / 2L * 5,
            "conserved physical Joules and Forge Energy in both directions");
        require(lastInput - remaining >= 0 && lastInput - remaining <= 2 && carriedFe(craft, closedPort) == 0,
            "real blocked side and complete FE/J units within a 3 FE source limit");
        lastInput = remaining;
        return output == OUTPUT_FE && remaining == 1 && from == 1 && to == 25;
    }

    void parked(BlockPos anchor) {
        var from = parked(sourceCube, anchor);
        var to = parked(targetCube, anchor);
        var restored = capability(to, Direction.NORTH);
        require(restored != null && restored.getEnergyStored() == 10 && contents(from) == 1 && contents(to) == 25,
            "rotated Mekanism sides and exact parked energy remain");
        require(capability(from, Direction.SOUTH) != null && capability(from, Direction.EAST) == null
            && packed(from).getCompound("component_security").getUUID("owner").equals(owner)
            && packed(from).getCompound("NeoForgeData").getString("FixtureEnergyReceipt").equals("finite cube"),
            "rotated side masks, private owner and opaque permitted data preserved");
        require(packed(parked(outputPort, anchor)).getInt("PortEnergy") == OUTPUT_FE
            && packed(parked(inputPort, anchor)).getInt("PortEnergy") == 1, "parked FE buffers preserve exactly one owner");
        require(world.getBlockState(sourceCube).isAir() && world.getBlockState(targetCube).isAir(), "no original Cube remains");
        System.out.println("[vandorlabs][vehicle-transfer] installed Mekanism cubes PASS 8 FE out, 10 FE in, "
            + "odd Joule and FE remainders, protected side/owner/slot, cached handler frozen, rotated park");
    }

    private BlockEntity cube(BlockPos at, Direction facing, boolean source) {
        var id = ResourceLocation.parse(source ? "mekanism:basic_energy_cube" : "mekanism:advanced_energy_cube");
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        require(NativeVehicleMekanismCube.supports(state), "real installed finite Mekanism tier");
        for (var property : state.getProperties()) if (property instanceof DirectionProperty direction && direction.getName().equals("facing")) {
            state = state.setValue(direction, facing);
            break;
        }
        put(at, state);
        var tile = world.getBlockEntity(at);
        configureSides(tile, source);
        var settings = tile.saveWithFullMetadata(world.registryAccess());
        var security = new CompoundTag();
        security.putInt("security_mode", 1);
        security.putUUID("owner", owner);
        settings.put("component_security", security);
        tile.loadWithComponents(settings, world.registryAccess());
        tile.getPersistentData().putString("FixtureEnergyReceipt", "finite cube");
        return tile;
    }

    private static void configureSides(BlockEntity tile, boolean source) {
        try {
            var type = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            var relative = Class.forName("mekanism.api.RelativeSide");
            var mode = Class.forName("mekanism.common.tile.component.config.DataType");
            var config = tile.getClass().getMethod("getConfig").invoke(tile);
            var bank = config.getClass().getMethod("getConfig", type).invoke(config, type.getField("ENERGY").get(null));
            var set = bank.getClass().getMethod("setDataType", mode, relative);
            for (var side : relative.getEnumConstants()) set.invoke(bank, mode.getField("NONE").get(null), side);
            set.invoke(bank, mode.getField(source ? "OUTPUT" : "INPUT").get(null), relative.getField("FRONT").get(null));
            if (source) set.invoke(bank, mode.getField("INPUT").get(null), relative.getField("BACK").get(null));
            bank.getClass().getMethod("setEjecting", boolean.class).invoke(bank, source);
            var control = tile.getClass().getMethod("getControlType").getReturnType();
            tile.getClass().getMethod("setControlType", control).invoke(tile, control.getField(source ? "HIGH" : "DISABLED").get(null));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Installed Mekanism Cube configuration API", failure);
        }
    }

    private static void clearSlots(BlockEntity tile) {
        try {
            var slots = (java.util.List<?>)tile.getClass().getMethod("getInventorySlots", Direction.class).invoke(tile, new Object[]{null});
            var assign = Class.forName("mekanism.api.inventory.IInventorySlot").getMethod("setStack", ItemStack.class);
            for (var slot : slots) assign.invoke(slot, ItemStack.EMPTY);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Installed Mekanism charging-slot API", failure);
        }
    }

    private static void setStored(BlockEntity tile, long joules) {
        try {
            var container = tile.getClass().getMethod("getEnergyContainer").invoke(tile);
            container.getClass().getMethod("setEnergy", long.class).invoke(container, joules);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Installed Mekanism Cube internal residue fixture", failure);
        }
    }

    private void rawPort(BlockPos at, Direction side, boolean activeOutput, int energy, ChannelList bank) {
        put(at, NativePortBindings.BLOCKS.get(NativePortKind.POWER).defaultBlockState().setValue(NativePortBlock.FACING, side));
        var tile = world.getBlockEntity(at);
        var tag = packed(at);
        var settings = ResourcePortSettings.placed(side.getOpposite().get3DDataValue())
            .withFace(side.getOpposite().get3DDataValue(), ResourcePortSettings.Face.DISABLED)
            .withFace(side.get3DDataValue(), activeOutput ? ResourcePortSettings.Face.OUTPUT : ResourcePortSettings.Face.INPUT)
            .withTransfer(activeOutput ? 3 : 0, false, ResourcePortSettings.OnTexture.TRANSFERRING);
        NativePortData.write(tag, new NativePortData.Configuration(settings, 1, bank));
        tag.putInt("PortEnergy", energy);
        tile.loadWithComponents(tag, world.registryAccess());
    }

    private IEnergyStorage capability(BlockPos at, Direction side) {
        return world.getCapability(Capabilities.EnergyStorage.BLOCK, at, side);
    }

    private CompoundTag packed(BlockPos at) {
        return world.getBlockEntity(at).saveWithFullMetadata(world.registryAccess());
    }

    private long contents(BlockPos at) { return NativeVehicleMekanismCube.contents(packed(at)); }

    private long carriedJoules(NativeGroundVehicle craft, BlockPos at) {
        return NativeVehicleMekanismCube.contents(carried(craft, at));
    }

    private int carriedFe(NativeGroundVehicle craft, BlockPos at) { return carried(craft, at).getInt("PortEnergy"); }

    private CompoundTag carried(NativeGroundVehicle craft, BlockPos at) {
        var local = at.subtract(root);
        return craft.snapshot().cells().stream().filter(cell -> cell.pos.equals(local)).findFirst().orElseThrow().tile();
    }

    private BlockPos parked(BlockPos from, BlockPos anchor) {
        return anchor.offset(from.subtract(root).rotate(Rotation.CLOCKWISE_90));
    }

    private void put(BlockPos at, net.minecraft.world.level.block.state.BlockState state) {
        world.setBlock(at, state, Block.UPDATE_ALL);
    }

    private static void require(boolean condition, String evidence) {
        if (!condition) throw new AssertionError("Mekanism Cube fixture: " + evidence);
    }
}
