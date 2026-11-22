package com.vandorlabs.neoforge.builders;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.redstone.ChannelList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeMaxwellRuntimeChecks {
    private static final BlockPos ROOT = new BlockPos(31, 120, 31);
    private static final int[] WIDTHS = MaxwellEngine.widths();
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH};
    private static ServerLevel world;
    private static FakePlayer actor;
    private static IFluidHandler retired;
    private static Set<BlockPos> cells;
    private static int index, ticks, checks;
    private static long litAt;
    private static Fluid ethanol, rocketFuel;
    private static Stage stage;

    private enum Stage { FORM, DISMANTLE, REMOVE, REPAIR, ACTIVE, STOP, COLD }

    private static String phase() { return System.getProperty("vandorlabs.maxwellRuntimePhase", ""); }

    @EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
    public static final class Fuels {
        @SubscribeEvent public static void register(RegisterEvent event) {
            if (phase().isEmpty() && !Boolean.getBoolean("vandorlabs.driveClient")
                    && !System.getProperty("vandorlabs.builderRecoveryPhase", "").equals("drives")) return;
            event.register(Registries.FLUID, helper -> {
                ethanol = new WaterFluid.Source();
                rocketFuel = new WaterFluid.Source();
                helper.register(ResourceLocation.parse("vandorlabs:ethanol"), ethanol);
                helper.register(ResourceLocation.parse("vandorlabs:rocket_fuel"), rocketFuel);
            });
        }
    }

    static FluidStack fuel(int amount) { return new FluidStack(ethanol, amount); }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (phase().isEmpty()) return;
        world = event.getServer().overworld();
        for (int x = 1; x <= 2; x++) for (int z = 1; z <= 2; z++) {
            world.setChunkForced(x, z, true);
            world.getChunk(x, z);
        }
        actor = new FakePlayer(world, new GameProfile(new UUID(0, 31807), "NativeMaxwellRuntime"));
        actor.setGameMode(GameType.SURVIVAL);
        actor.setPos(31.5, 120, 29.5);
        if (phase().equals("read")) {
            index = WIDTHS.length - 1;
            stage = Stage.COLD;
        } else {
            fixtures();
            place();
        }
    }

    private static void place() {
        int width = WIDTHS[index], depth = MaxwellEngine.depth(width);
        var facing = FACINGS[index];
        var core = NativeBuilderBindings.CORES.get(MaxwellEngine.DESIGN);
        need(world.setBlock(ROOT, core.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING, facing), Block.UPDATE_ALL),
                "fresh Maxwell Core is placed after prior ownership retires");
        int cornerX = (index & 1) == 0 ? 0 : width - 1;
        int cornerZ = (index & 2) == 0 ? 0 : depth - 1;
        var shape = NativeBuilderFormation.layout(core.design, width, facing.get3DDataValue(), cornerX, cornerZ);
        cells = new HashSet<>();
        for (int x = 0; x < width; x++) for (int y = 0; y < width; y++) for (int z = 0; z < depth; z++) {
            var pos = ROOT.offset(shape.x(x, z), y, shape.z(x, z));
            cells.add(pos);
            if (!pos.equals(ROOT)) world.setBlock(pos, shape.frame(x, y, z)
                    ? NativeBuilderFormation.matter().defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        var owner = (NativeBuilderEntity) world.getBlockEntity(ROOT);
        need(NativeBuilderTransactions.request(owner, actor, true), "Maxwell formation queued");
        stage = Stage.FORM;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (world == null) return;
        try {
            need(++ticks < 2200, "bounded async formation, dismantle and real burn");
            if (stage == Stage.REMOVE) {
                if (!world.isEmptyBlock(ROOT) || com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(world, ROOT)) return;
                for (var pos : cells) world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                index++;
                place();
                return;
            }
            if (!(world.getBlockEntity(ROOT) instanceof NativeBuilderEntity owner) || !owner.available() || !owner.synthesisReady())
                return;
            switch (stage) {
                case FORM -> {
                    if (!owner.formed()) return;
                    var shape = owner.layout();
                    need(shape.width == WIDTHS[index] && shape.height == WIDTHS[index]
                            && shape.depth == MaxwellEngine.depth(shape.width) && owner.facing() == FACINGS[index],
                            "exact formed size and facing");
                    for (var pos : cells) need(NativeBuilderBindings.ownerAt(world, pos) == owner, "member resolves full frame " + pos);
                    retired = fill(owner);
                    need(NativeBuilderTransactions.request(owner, actor, false), "dismantle queued");
                    stage = Stage.DISMANTLE;
                }
                case DISMANTLE -> {
                    if (owner.formed()) return;
                    need(owner.maxwellFuel() == 1000 && owner.driveLevel() == 0, "dismantle retains fuel in idle Core");
                    need(retired.fill(fuel(1), FluidAction.EXECUTE) == 0 && retired.getFluidInTank(0).isEmpty(),
                            "cached inlet cannot access dismantled owner");
                    if (index < WIDTHS.length - 1) {
                        need(NativeBuilderTransactions.mine(owner, actor, ROOT), "filled unformed Core requires durable removal");
                        stage = Stage.REMOVE;
                        return;
                    }
                    var saved = owner.saveWithoutMetadata(world.registryAccess());
                    need(saved.getInt(MaxwellEngine.WIDTH_TAG) == WIDTHS[index] && NativeMaxwellTank.read(saved, world.registryAccess()).amount() == 1000,
                            "unformed owner saves full fuel and size");
                    need(NativeBuilderTransactions.request(owner, actor, true), "refilled Core reformation queued");
                    stage = Stage.REPAIR;
                }
                case REMOVE -> throw new IllegalStateException("Removal stage handled before resolving the Core");
                case REPAIR -> {
                    if (!owner.formed()) return;
                    need(owner.maxwellFuel() == 1000 && owner.maxwellThrustLevel(true) == 15 && owner.driveLevel() == 0,
                            "reformation retains fuel and automatic demand policy");
                    need(owner.configureDrive(NativeDriveSettings.maxwell(true, false, true), ChannelList.EMPTY),
                            "real Redstone configuration");
                    world.setBlock(owner.maxwellPort(owner.facing()).relative(owner.facing()), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    litAt = Long.MIN_VALUE;
                    stage = Stage.ACTIVE;
                }
                case ACTIVE -> {
                    if (owner.driveLevel() == 15 && litAt == Long.MIN_VALUE) litAt = world.getGameTime();
                    if (litAt == Long.MIN_VALUE) return;
                    long elapsed = world.getGameTime() - litAt;
                    if (elapsed < 199) {
                        need(owner.maxwellFuel() == 1000, "full power retains 1 mB until the 200th tick");
                        return;
                    }
                    need(owner.maxwellFuel() == 999 && owner.driveLevel() == 15, "stationary signaled drive consumes exactly 1 mB");
                    world.setBlock(owner.maxwellPort(owner.facing()).relative(owner.facing()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    stage = Stage.STOP;
                }
                case STOP -> {
                    if (owner.driveLevel() != 0) return;
                    need(owner.maxwellFuel() == 999, "removing physical signal stops burn");
                    event.getServer().saveEverything(true, true, true);
                    finish("save PASS fiveSizes/threeSidedFluidCaps/retiredCap/realAnalogSignalAndBurn/reform");
                }
                case COLD -> {
                    need(owner.formed() && owner.layout().width == 9 && owner.maxwellFuel() == 999 && owner.driveLevel() == 0,
                            "cold restart reloads geometry, fuel and unpowered state");
                    var front = inlet(owner, owner.facing());
                    need(front.getFluidInTank(0).getFluid() == ethanol && front.fill(fuel(4), FluidAction.SIMULATE) == 1,
                            "cold registered fluid identity and capacity");
                    var menu = new NativeDriveMenu(NativeDriveBindings.MENU, 44, owner, ROOT);
                    need(!menu.clickMenuButton(actor, NativeDriveMenu.PURGE_TANK), "unattached menu cannot purge");
                    actor.containerMenu = menu;
                    need(!menu.clickMenuButton(actor, 7) && menu.clickMenuButton(actor, NativeDriveMenu.PURGE_TANK)
                            && owner.maxwellFuel() == 0 && front.getFluidInTank(0).isEmpty(), "live owned menu purges the shared tank");
                    finish("read PASS coldGeometry/registeredFluid/authorizedPurge");
                }
            }
        } catch (Throwable bad) {
            bad.printStackTrace();
            System.out.println("[vandorlabs][maxwell-runtime] FAIL stage=" + stage + " size=" + WIDTHS[index] + " tick=" + ticks + " " + bad);
            world = null;
            event.getServer().halt(false);
        }
    }

    private static IFluidHandler fill(NativeBuilderEntity owner) {
        var front = inlet(owner, owner.facing());
        var left = inlet(owner, owner.facing().getCounterClockWise());
        var right = inlet(owner, owner.facing().getClockWise());
        var port = owner.maxwellPort(owner.facing());
        var layout = owner.layout();
        need(port.equals(owner.cell(MaxwellEngine.faceCenter(layout.width), MaxwellEngine.faceCenter(layout.height), 0))
                && owner.maxwellPort(owner.facing().getCounterClockWise()).equals(
                        owner.cell(0, MaxwellEngine.faceCenter(layout.height), MaxwellEngine.faceCenter(layout.depth))),
                "intake centers and even side cell align to authored front");
        need(world.getCapability(Capabilities.FluidHandler.BLOCK, port, owner.facing().getOpposite()) == null
                && world.getCapability(Capabilities.FluidHandler.BLOCK, port, null) == null
                && world.getCapability(Capabilities.FluidHandler.BLOCK, port.above(), owner.facing()) == null,
                "no wrong side or other cell capability");
        need(front.fill(new FluidStack(Fluids.WATER, 30), FluidAction.EXECUTE) == 0
                && front.fill(fuel(2000), FluidAction.SIMULATE) == 1000 && owner.maxwellFuel() == 0, "reject water and simulate exact capacity");
        need(left.fill(fuel(600), FluidAction.EXECUTE) == 600
                && right.fill(new FluidStack(rocketFuel, 100), FluidAction.EXECUTE) == 0
                && right.fill(fuel(500), FluidAction.EXECUTE) == 400 && owner.maxwellFuel() == 1000
                && front.getFluidInTank(0).getAmount() == 1000 && front.drain(3, FluidAction.EXECUTE).isEmpty(),
                "all inlets share one fill-only tank and reject mixing accepted fuels");
        var client = new NativeBuilderEntity(ROOT, owner.getBlockState());
        var data = owner.getUpdateTag(world.registryAccess());
        client.handleUpdateTag(data, world.registryAccess());
        need(!data.contains(NativeMaxwellTank.FUEL_TAG) && !client.inert() && client.maxwellFuel() == 1000
                && client.layout().width == owner.layout().width, "public render state carries gauge quantity and frame dimensions");
        var saved = owner.saveWithoutMetadata(world.registryAccess());
        var projection = com.vandorlabs.neoforge.transfer.NativeSnapshotAppearance.project(owner.getBlockState(), saved, world.registryAccess());
        var moving = new NativeBuilderEntity(ROOT, owner.getBlockState());
        moving.loadWithComponents(projection, world.registryAccess());
        need(!moving.inert() && moving.maxwellFuel() == 1000 && moving.layout().width == owner.layout().width
                && moving.pivot().equals(owner.pivot()) && !projection.contains(NativeMaxwellTank.FUEL_TAG)
                && !projection.contains("Drive") && !projection.contains("Inventory"),
                "carried renderer keeps the casing, pivot and gauge while the fluid stays private");
        var refused = new NativeBuilderEntity(ROOT, owner.getBlockState());
        refused.setLevel(world);
        refused.loadWithComponents(projection, world.registryAccess());
        need(refused.inert() && refused.maxwellFuel() == 0, "visual-only projection cannot own placed fuel");
        saved.getCompound(NativeMaxwellTank.FUEL_TAG).putInt("amount", 2000);
        var damaged = new NativeBuilderEntity(ROOT, owner.getBlockState());
        damaged.loadWithComponents(saved, world.registryAccess());
        need(damaged.inert() && damaged.saveWithoutMetadata(world.registryAccess()).equals(saved),
                "invalid overfilled tank retains original recovery payload");
        return front;
    }

    private static IFluidHandler inlet(NativeBuilderEntity owner, Direction side) {
        var handler = world.getCapability(Capabilities.FluidHandler.BLOCK, owner.maxwellPort(side), side);
        need(handler != null && handler.getTanks() == 1 && handler.getTankCapacity(0) == 1000, "formed fluid inlet " + side);
        return handler;
    }

    private static void fixtures() {
        var tank = new NativeMaxwellTank();
        var inlet = tank.inlet(() -> true, () -> {});
        need(inlet.fill(fuel(4), FluidAction.EXECUTE) == 4, "registered test fluid");
        var analog = NativeDriveSettings.maxwell(true, false, true);
        var inverted = NativeDriveSettings.maxwell(true, true, true);
        var digital = NativeDriveSettings.maxwell(true, false, false);
        need(tank.throttle(analog, 5, false) == 5 && tank.throttle(digital, 5, false) == 15
                && tank.throttle(inverted, 5, false) == 10, "analog, digital and inverted throttle");
        for (int i = 0; i < 599; i++) tank.tick(5, i);
        var saved = new CompoundTag();
        tank.write(saved, world.registryAccess());
        var reload = NativeMaxwellTank.read(saved, world.registryAccess());
        need(reload.amount() == 4 && saved.getInt(NativeMaxwellTank.BURN_TAG) == 2995, "save exact fractional burn");
        reload.tick(0, 599);
        reload.tick(5, 599);
        need(reload.amount() == 4 && reload.activeLevel() == 0, "paused and duplicate timestep burn nothing");
        reload.tick(5, 600);
        need(reload.amount() == 3, "resumed analog pays one exact mB");
    }

    private static void finish(String summary) {
        System.out.println("[vandorlabs][maxwell-runtime] " + summary + " checks=" + checks);
        var server = world.getServer();
        world = null;
        server.halt(false);
    }

    private static void need(boolean valid, String detail) {
        checks++;
        if (!valid) throw new AssertionError("Maxwell stage " + stage + " size " + WIDTHS[index] + " tick " + ticks + " " + detail);
    }

    private NativeMaxwellRuntimeChecks() {}
}
