package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.neoforge.builders.NativeBuilderBindings;
import com.vandorlabs.neoforge.builders.NativeBuilderCoreBlock;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.builders.NativeBuilderFormation;
import com.vandorlabs.neoforge.builders.NativeBuilderTransactions;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.screen.NativeScreenRegistrations;
import com.vandorlabs.neoforge.seating.NativeSeatBindings;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeVehicleFlightFixture {
    private static BlockPos root = new BlockPos(272, 70, 264);
    private static ServerPlayer actor;
    private static Fluid fuel;
    private static NativeGroundVehicle craft;
    private static Stage stage;
    private static ChunkPos departure;
    private static Vec3 departed;
    private static int ticks, unexpectedLoads, airborneTicks;
    private static double descendingFrom;
    private static int wheelWallZ, wheelWait;
    private static double lastWheelZ;
    private static boolean landed, sawRunway;

    private enum Stage { FORM, ASSEMBLE, BOARD, LOW, TRANSIT, BURN, REENTRY, TOUCHDOWN, COLD, WHEEL_CONTACT, IMPACT }

    public static boolean enabled() { return Boolean.getBoolean("vandorlabs.vehicleFlightChecks"); }
    public static String restartPhase() { return System.getProperty("vandorlabs.vehicleFlightRestart", ""); }
    public static String impact() { return System.getProperty("vandorlabs.vehicleFlightImpact", ""); }
    public static boolean normalTerrain() { return Boolean.getBoolean("vandorlabs.vehicleFlightNormalTerrain"); }

    @EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
    public static final class Fuel {
        @SubscribeEvent public static void register(RegisterEvent event) {
            if (!enabled()) return;
            event.register(Registries.FLUID, helper -> {
                fuel = new WaterFluid.Source();
                helper.register(ResourceLocation.parse("vandorlabs:ethanol"), fuel);
            });
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        actor = player;
        if (restartPhase().equals("read")) {
            stage = Stage.COLD;
            return;
        }
        var world = player.serverLevel();
        if (normalTerrain()) {
            int height = root.getY();
            for (int x = -5; x <= 12; x++) for (int z = -14; z <= 48; z++) {
                height = Math.max(height, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        root.getX() + x, root.getZ() + z) + 2);
            }
            root = root.atY(height);
        }
        var pilot = root.east();
        var panelPos = pilot.south(3).above();
        var core = root.east(3);
        var top = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = -5; x <= 12; x++) for (int z = -14; z <= 48; z++) {
            world.setBlockAndUpdate(root.offset(x, -2, z), Blocks.STONE.defaultBlockState());
            for (int y = -1; y <= 6; y++) {
                var at = root.offset(x, y, z);
                if (!world.isEmptyBlock(at)) world.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            }
        }
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 3; z++) {
            world.setBlockAndUpdate(root.offset(x, -1, z), top);
        }
        if (impact().equals("runway")) {
            var wheel = com.vandorlabs.neoforge.landing.NativeLandingBindings.WHEELS.get("wheel_small");
            world.setBlockAndUpdate(root.offset(6, -1, 3), wheel.defaultBlockState()
                    .setValue(com.vandorlabs.neoforge.landing.NativeSideWheel.FACING, Direction.EAST));
        }
        if (impact().equals("hover")) {
            String[] lift = {"antigravity_plate", "repulsor_array", "vertical_hover_thruster", "ion_drive"};
            for (int index = 0; index < lift.length; index++) {
                var at = root.west().south(index);
                world.setBlockAndUpdate(at.below(), top);
                var device = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs", lift[index]));
                world.setBlockAndUpdate(at, device.defaultBlockState());
            }
        }
        world.setBlockAndUpdate(root, NativeVehicleBindings.CONTROLLER.defaultBlockState());
        ((NativeVehicleControllerEntity)world.getBlockEntity(root)).placedBy(player.getUUID());
        var seat = NativeSeatBindings.BLOCKS.get(NativeSeatKind.PILOT).defaultBlockState()
                .setValue(NativeSeatBlock.FACING, Direction.SOUTH);
        world.setBlockAndUpdate(pilot, seat);
        world.setBlockAndUpdate(pilot.above(), seat.setValue(NativeSeatBlock.UPPER, true));
        var panel = NativeScreenRegistrations.BLOCKS.get(ScreenFamily.SCREEN).get();
        world.setBlockAndUpdate(panelPos.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(panelPos, panel.state(new ScreenFamily.State(Direction.NORTH.get3DDataValue(), false, false, false)));
        var monitor = (NativeScreenEntity)world.getBlockEntity(panelPos);
        var config = monitor.saveWithFullMetadata(world.registryAccess());
        config.putInt("PrimarySurfaceTexture", NativeVehicleStatus.SURFACE);
        monitor.loadWithComponents(config, world.registryAccess());
        monitor.setChanged();
        var maxwell = NativeBuilderBindings.CORES.get(MaxwellEngine.DESIGN);
        world.setBlockAndUpdate(core, maxwell.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING, Direction.NORTH));
        var layout = NativeBuilderFormation.layout(maxwell.design, 3, Direction.NORTH.get3DDataValue(), 0, 0);
        for (int x = 0; x < layout.width; x++) for (int y = 0; y < layout.height; y++) for (int z = 0; z < layout.depth; z++) {
            var at = core.offset(layout.x(x, z), y, layout.z(x, z));
            if (!at.equals(core) && layout.frame(x, y, z)) {
                world.setBlock(at, NativeBuilderFormation.matter().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.teleportTo(root.getX() + 2.5, root.getY() - 1, root.getZ() - 1.5);
        stage = Stage.FORM;
    }

    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if (actor == null || craft == null || departure == null || craft.level() != event.getLevel()
                || stage != Stage.TRANSIT && stage != Stage.BURN) return;
        var pos = event.getChunk().getPos();
        if (Math.abs(pos.x - departure.x) > 12 || Math.abs(pos.z - departure.z) > 12) unexpectedLoads++;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (actor == null || landed) return;
        var world = actor.serverLevel();
        if (++ticks >= 1500) throw new IllegalStateException("Native Flight: server stage " + stage
                + " pos=" + actor.position() + " craft=" + craft
                + " carried=" + actor.getVehicle() + " hand=" + actor.getMainHandItem() + " slot=" + actor.getInventory().selected
                + (craft == null ? "" : " hit=" + craft.hit(actor) + " grounded=" + craft.onGround() + " mode=" + craft.mode()));
        switch (stage) {
            case FORM -> {
                var engine = (NativeBuilderEntity)world.getBlockEntity(root.east(3));
                if (!engine.formed()) {
                    if (engine.available() && engine.synthesisReady()) {
                        require(NativeBuilderTransactions.request(engine, actor, true), "real Maxwell formation submission");
                    }
                    return;
                }
                if (!engine.available() || !engine.synthesisReady()) return;
                var side = engine.facing();
                var inlet = world.getCapability(Capabilities.FluidHandler.BLOCK, engine.maxwellPort(side), side);
                require(inlet != null && inlet.fill(new FluidStack(fuel, 1), FluidAction.EXECUTE) == 1, "real Maxwell fuel inlet");
                actor.teleportTo(root.getX() + 1.5, root.getY() - 1, root.getZ() - 1.5);
                var hull = NativeVehicleScan.scan(world, root, actor.getUUID());
                var admissionProbe = new NativeGroundVehicle(NativeVehicleBindings.VEHICLE, world);
                admissionProbe.install(hull.local(), hull.pilot(), hull.facing());
                require(admissionProbe.statusPanels().size() == 1, "Flight monitor present in verified assembly preview");
                NativeVehicleTransfer.assemble(actor, root);
                stage = Stage.ASSEMBLE;
            }
            case ASSEMBLE -> {
                var found = world.getEntitiesOfClass(NativeGroundVehicle.class, new AABB(root).inflate(12));
                if (found.size() != 1 || !found.getFirst().admitted() || NativeVehicleTransfer.escrowed(found.getFirst())) return;
                craft = found.getFirst();
                var telemetry = craft.flightReadout();
                require(telemetry.drives() == 1 && telemetry.fuel() == 1 && telemetry.ceiling() > 1000
                        && craft.statusPanels().size() == 1, "formed Maxwell and Vehicle Status carried");
                if (NativeVehicleEngineMenuFixture.enabled()) NativeVehicleEngineMenuFixture.prepare(actor, craft);
                stage = Stage.BOARD;
            }
            case BOARD -> {
                if (NativeVehicleEngineMenuFixture.enabled()) {
                    landed = NativeVehicleEngineMenuFixture.tick(actor, craft);
                    return;
                }
                if (actor.getVehicle() != craft || craft.mode() != NativeGroundVehicle.FLIGHT) return;
                if (craft.onGround()) {
                    sawRunway = true;
                    return;
                }
                require(sawRunway, "a supported Flight roll precedes the first airborne tick");
                stage = Stage.LOW;
            }
            case LOW -> {
                if (!impact().isEmpty()) {
                    if (++airborneTicks < 9) return;
                    var box = craft.getBoundingBox();
                    int wall = Mth.floor(box.maxZ) + 2;
                    if (impact().equals("runway")) {
                        wheelWallZ = wall;
                        wheelPillar(world, Blocks.STONE.defaultBlockState());
                        lastWheelZ = craft.getZ();
                        stage = Stage.WHEEL_CONTACT;
                        return;
                    }
                    for (int x = Mth.floor(box.minX) - 1; x <= Mth.ceil(box.maxX) + 1; x++) {
                        for (int y = root.getY(); y <= Mth.ceil(box.maxY) + 4; y++) {
                            world.setBlockAndUpdate(new BlockPos(x, y, wall), Blocks.STONE.defaultBlockState());
                        }
                    }
                    stage = Stage.IMPACT;
                    return;
                }
                if (craft.highFlight()) {
                    require(craft.flightAltitude() > FlightEnvelope.WORLD_CEILING && craft.flightAltitude() < 260,
                            "powered tick crosses Y=256 into geographic transit");
                    departure = craft.chunkPosition();
                    departed = craft.position();
                    stage = Stage.TRANSIT;
                    return;
                }
                if (++airborneTicks == 10) {
                    craft.setPos(craft.getX(), 254.0, craft.getZ());
                    craft.setOldPosAndRot();
                }
            }
            case TRANSIT -> {
                require(unexpectedLoads == 0, "high transit generated no distant route chunks");
                if (Math.abs(craft.hullPitch()) > 2 || actor.getVehicle() != craft) return;
                double height = Math.min(6000, craft.flightReadout().ceiling() - 100);
                craft.setPos(craft.getX(), NativeVehicleCruise.SKY_OFFSET + height, craft.getZ());
                craft.setOldPosAndRot();
                stage = Stage.BURN;
            }
            case BURN -> {
                require(unexpectedLoads == 0 && craft.chunkPosition().equals(departure), "geographic route retains departure tracking chunk");
                if (restartPhase().equals("write")) {
                    if (Math.hypot(craft.routeX() - departed.x, craft.routeZ() - departed.z) <= 200) return;
                    require(craft.flightReadout().fuel() == 1 && actor.getVehicle() == craft, "checkpoint powered and still mounted");
                    event.getServer().saveEverything(true, true, true);
                    landed = true;
                    System.out.println("[vandorlabs][vehicle-flight-write-player] server PASS saved powered geographic craft and mounted pilot");
                    return;
                }
                if (craft.flightReadout().fuel() != 0) return;
                require(Math.hypot(craft.routeX() - departed.x, craft.routeZ() - departed.z) > 800,
                        "altitude-dependent cruise speed advances a distant geographic route");
                var tag = craft.saveWithoutId(new CompoundTag());
                var saved = NativeGroundVehicle.readSavedVehicle(tag, world.registryAccess());
                require(saved.route() != null && saved.pilotAssignment().equals(actor.getUUID()),
                        "high-flight route and pilot assignment persisted");
                descendingFrom = craft.flightAltitude();
                stage = Stage.REENTRY;
            }
            case REENTRY -> {
                if (craft.highFlight() && craft.flightAltitude() < descendingFrom - 2 && craft.flightAltitude() > 300) {
                    craft.setPos(craft.getX(), NativeVehicleCruise.SKY_OFFSET + 258.0, craft.getZ());
                    craft.setOldPosAndRot();
                }
                if (craft.highFlight()) return;
                require(!craft.chunkPosition().equals(departure) && actor.getVehicle() == craft
                        && craft.getY() >= world.getMaxBuildHeight(), "bounded destination reentry relocates the actual mounted pilot");
                double ground = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        craft.getBlockX(), craft.getBlockZ());
                craft.setPos(craft.getX(), ground + 4, craft.getZ());
                craft.setOldPosAndRot();
                stage = Stage.TOUCHDOWN;
            }
            case WHEEL_CONTACT -> {
                require(craft.mode() == NativeGroundVehicle.FLIGHT && actor.getVehicle() == craft,
                        "an outboard wheel touching the post preserves powered Flight");
                require(craft.getZ() < wheelWallZ, "the outboard wheel cannot pass through its post");
                wheelWait = Math.abs(lastWheelZ - craft.getZ()) < .0001 ? wheelWait + 1 : 0;
                lastWheelZ = craft.getZ();
                if (wheelWait < 4) return;
                wheelPillar(world, Blocks.AIR.defaultBlockState());
                for (int x = root.getX() - 2; x <= root.getX() + 8; x++) {
                    for (int y = root.getY() - 3; y < root.getY() + 22; y++) {
                        world.setBlockAndUpdate(new BlockPos(x, y, wheelWallZ + 3), Blocks.STONE.defaultBlockState());
                    }
                }
                System.out.println("[vandorlabs][vehicle-flight-impact-runway-player] wheel-only contact PASS");
                stage = Stage.IMPACT;
            }
            case IMPACT -> {
                int expected = impact().equals("hover") ? NativeGroundVehicle.HOVER : NativeGroundVehicle.TAXI;
                if (craft.mode() != expected || Math.abs(craft.driveSpeed()) > .005) return;
                require(!craft.highFlight() && actor.getVehicle() == craft && craft.flightReadout().fuel() == 1
                        && craft.flightReadout().canHover() == impact().equals("hover"), "powered hull impact applies its available recovery mode");
                landed = true;
                System.out.println("[vandorlabs][vehicle-flight-impact-" + impact() + "-player] server PASS powered terrain sweep and capability-specific recovery");
            }
            case COLD -> {
                if (!(actor.getVehicle() instanceof NativeGroundVehicle restored) || !restored.admitted()) return;
                require(restored.highFlight() && restored.flightAltitude() > 1000 && restored.flightReadout().fuel() == 1
                        && Math.hypot(restored.routeX() - restored.getX(), restored.routeZ() - restored.getZ()) > 200,
                        "cold JVM restores physical pilot attachment, virtual route and retained fuel");
                landed = true;
                System.out.println("[vandorlabs][vehicle-flight-read-player] server PASS cold carried ownership and seated high-flight geographic pose");
            }
            case TOUCHDOWN -> {
                if (craft.mode() != NativeGroundVehicle.TAXI || !craft.onGround()) return;
                require(actor.getVehicle() == craft && craft.flightReadout().fuel() == 0, "Maxwell-only fuel loss returns to Taxi at touchdown");
                landed = true;
                System.out.println("[vandorlabs][vehicle-flight-player] server PASS formed Maxwell runway high transit no route chunk loads reentry fuel-loss landing");
            }
        }
    }

    private static void wheelPillar(net.minecraft.server.level.ServerLevel world, net.minecraft.world.level.block.state.BlockState state) {
        for (int y = root.getY() - 3; y < root.getY() + 22; y++) {
            world.setBlockAndUpdate(new BlockPos(root.getX() + 6, y, wheelWallZ), state);
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native Flight: " + message);
    }

    private NativeVehicleFlightFixture() {}
}
