package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.neoforge.builders.NativeBuilderBindings;
import com.vandorlabs.neoforge.builders.NativeBuilderCoreBlock;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.builders.NativeBuilderFormation;
import com.vandorlabs.neoforge.builders.NativeBuilderTransactions;
import com.vandorlabs.neoforge.builders.NativeDriveSettings;
import com.vandorlabs.neoforge.builders.NativeMaxwellTank;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.screen.NativeScreenRegistrations;
import com.vandorlabs.neoforge.seating.NativeSeatBindings;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Two native frames, one journaled vehicle and one independently signaled, loaded drive. */
@EventBusSubscriber(modid = "vandorlabs")
public final class NativeDualMaxwellFixture {
    public static final String PREFIX = "Dual Maxwell: ";
    public static final BlockPos ROOT = new BlockPos(272, 70, 264);
    public static final BlockPos THROTTLE = new BlockPos(0, 1, 3);
    private static final BlockPos[] CORES = {ROOT.east(3), ROOT.east(7)};
    private static final int[] WIDTHS = {3, 5}, INITIAL_FUEL = {3, 8};
    private static final int CHANNEL = 508, LOW = 5;
    private static final double SMALL_THRUST = MaxwellEngine.thrustUnits(WIDTHS[0]);
    private static final double LARGE_THRUST = MaxwellEngine.thrustUnits(WIDTHS[1]);
    private static final double FULL_THRUST = SMALL_THRUST + LARGE_THRUST;
    private static final Fluid[] FUELS = new Fluid[2];
    private static ServerPlayer rider;
    private static NativeGroundVehicle craft;
    private static Stage stage;
    private static int ticks, formed;
    private static boolean descended, returning;
    private static double combinedAltitude;

    private enum Stage { FORM, ASSEMBLE, TAXI_BURN, HOVER_BURN, FLIGHT, CLIMB, LEVEL, LIMIT, RETURN, FINISHED }
    private record Tank(int fuel, int throttle, int fraction) {}

    public static boolean enabled() { return Boolean.getBoolean("vandorlabs.dualMaxwellChecks"); }

    @EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
    public static final class Fuel {
        @SubscribeEvent public static void register(RegisterEvent event) {
            if (!enabled()) return;
            event.register(Registries.FLUID, helper -> {
                FUELS[0] = new WaterFluid.Source();
                FUELS[1] = new WaterFluid.Source();
                helper.register(ResourceLocation.parse("vandorlabs:ethanol"), FUELS[0]);
                helper.register(ResourceLocation.parse("vandorlabs:rocket_fuel"), FUELS[1]);
            });
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        rider = player;
        var world = player.serverLevel();
        var slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = -4; x <= 15; x++) for (int z = -10; z <= 38; z++) {
            world.setBlockAndUpdate(ROOT.offset(x, -2, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = -1; x <= 11; x++) for (int z = 0; z <= 6; z++) {
            world.setBlockAndUpdate(ROOT.offset(x, -1, z), slab);
        }
        for (int z = 1; z <= 3; z++) world.setBlockAndUpdate(ROOT.south(z), Blocks.STONE.defaultBlockState());
        for (int x : new int[]{2, 6}) world.setBlockAndUpdate(ROOT.east(x), Blocks.STONE.defaultBlockState());
        var devices = new String[]{"antigravity_plate", "repulsor_array", "vertical_hover_thruster", "ion_drive"};
        for (int z = 0; z < devices.length; z++) {
            var device = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs", devices[z]));
            world.setBlockAndUpdate(ROOT.west().south(z), device.defaultBlockState());
        }
        world.setBlockAndUpdate(ROOT, NativeVehicleBindings.CONTROLLER.defaultBlockState());
        ((NativeVehicleControllerEntity)world.getBlockEntity(ROOT)).placedBy(player.getUUID());
        var seat = NativeSeatBindings.BLOCKS.get(NativeSeatKind.PILOT).defaultBlockState()
                .setValue(NativeSeatBlock.FACING, Direction.SOUTH);
        world.setBlockAndUpdate(ROOT.east(), seat);
        world.setBlockAndUpdate(ROOT.east().above(), seat.setValue(NativeSeatBlock.UPPER, true));
        var status = ROOT.east().south(3).above();
        world.setBlockAndUpdate(status.below(), Blocks.STONE.defaultBlockState());
        panel(world, status, false);
        panel(world, ROOT.offset(THROTTLE), true);
        for (int i = 0; i < CORES.length; i++) placeDrive(world, CORES[i], WIDTHS[i]);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.teleportTo(ROOT.getX() + 1.5, ROOT.getY() - 1, ROOT.getZ() - 1.5);
        stage = Stage.FORM;
    }

    private static void panel(ServerLevel world, BlockPos pos, boolean throttle) {
        var block = NativeScreenRegistrations.BLOCKS.get(ScreenFamily.SCREEN).get();
        world.setBlockAndUpdate(pos, block.state(new ScreenFamily.State(Direction.NORTH.get3DDataValue(), false, false, false)));
        var screen = (NativeScreenEntity)world.getBlockEntity(pos);
        var tag = screen.saveWithFullMetadata(world.registryAccess());
        if (throttle) {
            var row = new CompoundTag();
            row.putString("Label", "Maxwell");
            row.putBoolean("Slider", true);
            row.putInt("Min", LOW);
            row.putInt("Max", 15);
            row.putIntArray("RedstoneChannels", new int[]{CHANNEL});
            var entries = new ListTag();
            entries.add(row);
            var rows = new CompoundTag();
            rows.putBoolean("Enabled", true);
            rows.putString("Title", "Drive throttle");
            rows.put("Rows", entries);
            tag.put("RedstonePrimary", rows);
        } else tag.putInt("PrimarySurfaceTexture", NativeVehicleStatus.SURFACE);
        screen.loadWithComponents(tag, world.registryAccess());
        screen.setChanged();
    }

    private static void placeDrive(ServerLevel world, BlockPos core, int width) {
        var block = NativeBuilderBindings.CORES.get(MaxwellEngine.DESIGN);
        world.setBlockAndUpdate(core, block.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING, Direction.NORTH));
        var shape = NativeBuilderFormation.layout(block.design, width, Direction.NORTH.get3DDataValue(), 0, 0);
        for (int x = 0; x < shape.width; x++) for (int y = 0; y < shape.height; y++) for (int z = 0; z < shape.depth; z++) {
            var pos = core.offset(shape.x(x, z), y, shape.z(x, z));
            if (!pos.equals(core) && shape.frame(x, y, z))
                world.setBlock(pos, NativeBuilderFormation.matter().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (rider == null || stage == Stage.FINISHED) return;
        var world = rider.serverLevel();
        if (++ticks >= 1600) throw new IllegalStateException("Native dual Maxwell timeout at " + stage
                + " craft=" + craft + " rider=" + rider.getVehicle() + " held=" + rider.getMainHandItem()
                + " slot=" + rider.getInventory().selected + " pos=" + rider.position()
                + (craft == null ? "" : " gauges=" + craft.flightReadout() + " altitude=" + craft.flightAltitude()));
        switch (stage) {
            case FORM -> {
                var engine = (NativeBuilderEntity)world.getBlockEntity(CORES[formed]);
                if (engine == null || !engine.available() || !engine.synthesisReady()) return;
                if (!engine.formed()) {
                    require(NativeBuilderTransactions.request(engine, rider, true), "installed Maxwell formation");
                    return;
                }
                require(engine.layout().width == WIDTHS[formed], "exact selected frame size");
                var inlet = world.getCapability(Capabilities.FluidHandler.BLOCK, engine.maxwellPort(engine.facing()), engine.facing());
                require(inlet != null && inlet.fill(new FluidStack(FUELS[formed], INITIAL_FUEL[formed]), FluidAction.EXECUTE)
                        == INITIAL_FUEL[formed], "real formed Maxwell fuel inlet");
                if (formed == 1) require(engine.configureDrive(NativeDriveSettings.maxwell(true, false, true),
                        ChannelList.of(CHANNEL)), "configure the larger engine for separate analog throttle");
                if (++formed != CORES.length) return;
                NativeVehicleTransfer.assemble(rider, ROOT);
                stage = Stage.ASSEMBLE;
            }
            case ASSEMBLE -> {
                var found = world.getEntitiesOfClass(NativeGroundVehicle.class, new AABB(ROOT).inflate(12));
                if (found.size() != 1 || !found.getFirst().admitted() || NativeVehicleTransfer.escrowed(found.getFirst())) return;
                craft = found.getFirst();
                var telemetry = verifyEnvelope(SMALL_THRUST);
                require(telemetry.fuel() == INITIAL_FUEL[0] + INITIAL_FUEL[1] && telemetry.canHover()
                        && telemetry.ceiling() == 0, "unpowered analog drive is excluded from takeoff, while both tanks report fuel");
                command("power");
                stage = Stage.TAXI_BURN;
            }
            case TAXI_BURN -> {
                if (rider.getVehicle() != craft || craft.flightReadout().fuel() > INITIAL_FUEL[0] + INITIAL_FUEL[1] - 1) return;
                var tanks = tanks();
                require(craft.mode() == NativeGroundVehicle.TAXI && craft.onGround() && tanks[0].fuel == INITIAL_FUEL[0]
                        && tanks[0].throttle == 0 && tanks[0].fraction == 0
                        && tanks[1].fuel == INITIAL_FUEL[1] - 1 && tanks[1].throttle == 15, "only the signaled drive burns in Taxi");
                var telemetry = verifyEnvelope(FULL_THRUST);
                require(telemetry.ceiling() > FlightEnvelope.WORLD_CEILING, "combined thrust admits Flight");
                command("hover");
                stage = Stage.HOVER_BURN;
            }
            case HOVER_BURN -> {
                if (craft.mode() != NativeGroundVehicle.HOVER || craft.flightReadout().fuel() > INITIAL_FUEL[0] + INITIAL_FUEL[1] - 2) return;
                var tanks = tanks();
                require(tanks[0].fuel == INITIAL_FUEL[0] && tanks[0].throttle == 0 && tanks[0].fraction == 0
                        && tanks[1].fuel == INITIAL_FUEL[1] - 2 && tanks[1].throttle == 15, "the same independent signal burns only the large tank in Hover");
                command("flight");
                stage = Stage.FLIGHT;
            }
            case FLIGHT -> {
                if (craft.mode() != NativeGroundVehicle.FLIGHT) return;
                var tanks = tanks();
                if (tanks[0].throttle != 15) return;
                require(tanks[0].fraction > 0 && tanks[1].throttle == 15
                        && verifyEnvelope(FULL_THRUST).fuel() == tanks[0].fuel + tanks[1].fuel,
                        "Flight requests the automatic engine independently and sums both fuel quantities");
                placeAt(254);
                command("raise");
                stage = Stage.CLIMB;
            }
            case CLIMB -> {
                if (!craft.highFlight()) return;
                command("level");
                stage = Stage.LEVEL;
            }
            case LEVEL -> {
                if (Math.abs(craft.hullPitch()) > 2) return;
                var full = verifyEnvelope(FULL_THRUST);
                double reduced = FlightEnvelope.maximumAltitude(full.effectiveMass(), SMALL_THRUST + LARGE_THRUST * LOW / 15);
                require(reduced > FlightEnvelope.WORLD_CEILING && reduced + 40 < full.ceiling(), "lower combined ceiling stays in geographic cruise");
                combinedAltitude = reduced + 15;
                placeAt(NativeVehicleCruise.SKY_OFFSET + combinedAltitude);
                command("reduce");
                stage = Stage.LIMIT;
            }
            case LIMIT -> {
                var telemetry = craft.flightReadout();
                if (telemetry.thrust() > SMALL_THRUST + LARGE_THRUST * LOW / 15 + 1e-7) return;
                verifyEnvelope(SMALL_THRUST + LARGE_THRUST * LOW / 15);
                require(craft.highFlight() && craft.mode() == NativeGroundVehicle.FLIGHT, "partial throttle keeps both engines in Flight");
                if (craft.flightAltitude() < combinedAltitude - 2) descended = true;
                if (!descended || craft.flightAltitude() > telemetry.ceiling() + .06) return;
                var tanks = tanks();
                require(tanks[0].throttle == 15 && tanks[1].throttle == LOW, "independent carried gauges retain full and low output");
                command("stop");
                combinedAltitude = craft.flightAltitude();
                stage = Stage.RETURN;
            }
            case RETURN -> {
                if (!returning) {
                    if (craft.flightReadout().thrust() > SMALL_THRUST + 1e-7 || craft.flightAltitude() > combinedAltitude - 3) return;
                    require(verifyEnvelope(SMALL_THRUST).ceiling() == 0 && craft.highFlight(), "removing the large engine signal makes the remaining thrust insufficient");
                    placeAt(NativeVehicleCruise.SKY_OFFSET + 258);
                    returning = true;
                    return;
                }
                if (craft.highFlight() || craft.mode() != NativeGroundVehicle.HOVER) return;
                var tanks = tanks();
                if (tanks[0].throttle != 0) return;
                require(Math.abs(craft.getY() - FlightEnvelope.REENTRY_ALTITUDE) <= .02,
                        "high-cruise loss levels out at the Y=200 recovery altitude, actual " + craft.getY());
                require(rider.getVehicle() == craft && tanks[0].fuel > 0 && tanks[1].fuel > 0 && tanks[1].throttle == 0,
                        "loss of strength returns the still-fueled mounted craft to Hover");
                System.out.println("[vandorlabs][dual-maxwell-player] server PASS unequal formed drives, combined envelope, independent Taxi/Hover fuel, limited cruise and Hover recovery");
                command("passed");
                stage = Stage.FINISHED;
            }
            case FINISHED -> {}
        }
    }

    private static NativeVehiclePropulsion.Readout verifyEnvelope(double thrust) {
        var actual = craft.flightReadout();
        require(actual.drives() == 2 && actual.capacity() == 2 * MaxwellEngine.CAPACITY
                && Math.abs(actual.thrust() - thrust) < 1e-7
                && actual.mass() >= MaxwellEngine.dryMass(WIDTHS[0]) + MaxwellEngine.dryMass(WIDTHS[1])
                && Math.abs(actual.effectiveMass() - FlightEnvelope.effectiveMass(actual.mass(), 1, 1)) < 1e-7
                && actual.effectiveMass() < actual.mass()
                && Math.abs(actual.ratedTakeoffMass() - FlightEnvelope.takeoffMass(MaxwellEngine.thrustUnits(WIDTHS[0]) + MaxwellEngine.thrustUnits(WIDTHS[1]))) < 1e-7
                && Math.abs(actual.takeoffMass() - FlightEnvelope.takeoffMass(thrust)) < 1e-7
                && Math.abs(actual.ceiling() - FlightEnvelope.maximumAltitude(actual.effectiveMass(), thrust)) < 1e-7,
                "combined fuel capacity, reduced weight, takeoff limit and ceiling: " + actual);
        return actual;
    }

    private static Tank[] tanks() {
        var cells = craft.snapshot().cells();
        var result = new Tank[CORES.length];
        for (int i = 0; i < CORES.length; i++) {
            var local = CORES[i].subtract(ROOT);
            var saved = cells.stream().filter(cell -> cell.pos.equals(local)).findFirst().orElseThrow().tile();
            var tank = NativeMaxwellTank.read(saved, craft.level().registryAccess());
            require(tank.amount() == 0 || tank.contents().getFluid() == FUELS[i], "each carried Core retains its own fuel type");
            result[i] = new Tank(tank.amount(), tank.activeLevel(), saved.getInt(NativeMaxwellTank.BURN_TAG));
        }
        return result;
    }

    private static void placeAt(double y) {
        craft.setPos(craft.getX(), y, craft.getZ());
        craft.setOldPosAndRot();
    }

    private static void command(String action) {
        rider.sendSystemMessage(net.minecraft.network.chat.Component.literal(PREFIX + action));
    }

    public static void require(boolean value, String detail) {
        if (!value) throw new IllegalStateException("Native dual Maxwell: " + detail);
    }

    private NativeDualMaxwellFixture() {}
}
