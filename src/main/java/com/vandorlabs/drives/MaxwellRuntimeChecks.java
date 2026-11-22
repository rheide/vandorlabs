package com.vandorlabs.drives;

import com.vandorlabs.blocks.BlockConstructorCore;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleTileData;
import com.vandorlabs.vehicle.VehicleWeight;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/** The operational checks exercise real world tiles and carried Forge fluid capabilities. */
public final class MaxwellRuntimeChecks {
    private static final BlockPos ROOT = new BlockPos(50, 140, 47);
    private static int checks;

    private MaxwellRuntimeChecks() {}

    public static Fluid testFuel() {
        Fluid registered = FluidRegistry.getFluid("ethanol");
        if (registered != null) return registered;
        ResourceLocation still = new ResourceLocation("minecraft", "blocks/water_still");
        FluidRegistry.registerFluid(new Fluid("ethanol", still, still));
        return FluidRegistry.getFluid("ethanol");
    }

    public static TileEntityConstructor assemble(WorldServer world, BlockPos root, int width, EnumFacing facing, int cornerX, int cornerZ) {
        BlockConstructorCore block = (BlockConstructorCore) Block.getBlockFromName("vandorlabs:longburn_drive_core");
        world.setBlockState(root, block.getDefaultState().withProperty(BlockVandorDirectional.FACING, facing), 2);
        CoreLayout spec = new CoreLayout(width, width, MaxwellEngine.depth(width), facing.getIndex(), cornerX, cornerZ);
        for (int y = 0; y < spec.height; y++) {
            for (int x = 0; x < spec.width; x++) {
                for (int z = 0; z < spec.depth; z++) {
                    BlockPos at = root.add(spec.x(x, z), y, spec.z(x, z));
                    if (!at.equals(root) && spec.frame(x, y, z)) world.setBlockState(at, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 2);
                }
            }
        }
        TileEntityConstructor engine = (TileEntityConstructor) world.getTileEntity(root);
        require(engine.assemble(), "real frame assembly " + width + ", " + facing + ", " + cornerX + "/" + cornerZ + " " + engine.assemblyProblem());
        return engine;
    }

    public static IFluidHandler inlet(TileEntityConstructor engine, EnumFacing side) {
        return handler(engine.getWorld().getTileEntity(engine.maxwellPort(side)), side);
    }

    private static IFluidHandler handler(TileEntity tile, EnumFacing side) {
        return tile == null ? null : tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    public static void run(WorldServer world) {
        int[] widths = {3, 4, 5, 6, 9}, depths = {4, 5, 7, 8, 12};
        long previousTime = world.getTotalWorldTime();
        Fluid ethanol = testFuel();
        checks = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) world.getChunkFromChunkCoords((ROOT.getX() >> 4) + x, (ROOT.getZ() >> 4) + z);
        }
        try {
            for (int i = 0; i < widths.length; i++) {
                for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                    int turn = facing.getHorizontalIndex();
                    int width = widths[i], depth = depths[i];
                    int cornerX = (turn & 1) == 0 ? 0 : width - 1;
                    int cornerZ = (turn & 2) == 0 ? 0 : depth - 1;
                    TileEntityConstructor engine = assemble(world, ROOT, width, facing, cornerX, cornerZ);
                    require(engine.structureWidth() == width && engine.structureHeight() == width
                            && engine.structureDepth() == depth && engine.facing() == facing, "selected frame size and placed facing");
                    require(engine.mount() == null && engine.surfaceCount() == 0
                            && !engine.accepts(new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP)), "fuel instruments replace the console");
                    EnumFacing left = facing.rotateYCCW(), right = facing.rotateY();
                    int mid = (width - 1) / 2, sideCenter = (depth - 1) / 2;
                    require(engine.maxwellPort(facing).equals(engine.cell(mid, mid, 0))
                            && engine.maxwellPort(left).equals(engine.cell(0, mid, sideCenter))
                            && engine.maxwellPort(right).equals(engine.cell(width - 1, mid, sideCenter))
                            && engine.maxwellPort(facing.getOpposite()) == null, "inlet addresses align to three outward faces");
                    IFluidHandler front = inlet(engine, facing), west = inlet(engine, left), east = inlet(engine, right);
                    require(front != null && west != null && east != null && handler(engine, facing) == null
                            && handler(world.getTileEntity(engine.maxwellPort(facing)), facing.getOpposite()) == null, "fill capability only on correct outward inlet faces");
                    require(front.getTankProperties()[0].getCapacity() == 1000 && !front.getTankProperties()[0].canDrain(), "one bucket, no extraction");
                    require(front.fill(new FluidStack(FluidRegistry.WATER, 1000), true) == 0, "rejects water");
                    require(front.fill(new FluidStack(ethanol, 1500), false) == 1000 && engine.maxwellFuel() == 0, "simulation respects capacity");
                    require(west.fill(new FluidStack(ethanol, 725), true) == 725 && east.fill(new FluidStack(ethanol, 500), true) == 275
                            && engine.maxwellFuel() == 1000 && front.drain(1000, true) == null, "three inlets feed one bounded tank");
                    require(Math.abs(engine.maxwellDryMass() - 160 * width * width * depth / 36.0) < .001, "assembled dry weight scales by volume");
                    require(engine.maxwellThrustLevel(false) == 0 && engine.maxwellThrustLevel(true) == 15, "automatic mode burns only on flight demand");
                    if (i == 0 && facing == EnumFacing.NORTH) burnAndCarry(world, engine, front, ethanol);
                    engine.dismantle();
                    require(front.fill(new FluidStack(ethanol, 1), true) == 0, "retired inlet refuses cached writes");
                    clear(world, engine, width, depth);
                }
            }
        } finally {
            world.setTotalWorldTime(previousTime);
        }
        System.out.println("[vandorlabs][reprolab] maxwell-runtime PASS assertions=" + checks
                + " frame sizes/facings/corners fused tank/input sides/fuel rejection analog/digital/inverted timing and carried fuel");
    }

    private static void burnAndCarry(WorldServer world, TileEntityConstructor engine, IFluidHandler inlet, Fluid ethanol) {
        engine.configureDrive(new DriveSettings(true, false, 1, 8));
        engine.configureMachine(new NavigationScreenSettings(-1, false, 1, true, false), new int[0], false, 127, 0, ChannelList.EMPTY);
        engine.setChannelLevel(7);
        for (int i = 0; i < 428; i++) tick(world, engine);
        require(engine.maxwellFuel() == 1000 && engine.maxwellActiveLevel() == 7, "analog burn leaves sub-millibucket progress");
        NBTTagCompound save = engine.writeToNBT(new NBTTagCompound());
        engine.readFromNBT(save);
        tick(world, engine);
        require(engine.maxwellFuel() == 999, "fractional burn progress survives save and reload");
        engine.configureDrive(new DriveSettings(false, false, 1, 8));
        engine.setChannelLevel(1);
        for (int i = 0; i < 199; i++) tick(world, engine);
        require(engine.maxwellFuel() == 999 && engine.maxwellActiveLevel() == 15, "digital input gives full afterburner");
        tick(world, engine);
        require(engine.maxwellFuel() == 998, "full throttle consumes 1 mB every 200 ticks");
        engine.purgeMaxwell();
        require(engine.maxwellFuel() == 0 && engine.maxwellActiveLevel() == 0, "purge empties and extinguishes the tank");
        String[] supported = {"refined_fuel", "ethanol", "fuelium", "rocketfuel", "fuelgc", "fuel"};
        for (String name : supported) {
            Fluid fuel = FluidRegistry.getFluid(name);
            if (fuel == null || name.equals("fuel") && !net.minecraftforge.fml.common.Loader.isModLoaded("galacticraftcore")) continue;
            require(inlet.fill(new FluidStack(fuel, 1), true) == 1, "accepts installed " + fuel.getName());
            engine.purgeMaxwell();
        }
        inlet.fill(new FluidStack(ethanol, 650), true);
        engine.configureDrive(new DriveSettings(true, true, 1, 8));
        engine.setChannelLevel(11);
        require(engine.maxwellThrustLevel(false) == 4, "inversion subtracts the analog input from 15");

        EntityGroundVehicle craft = capture(world, engine);
        try {
            TileEntityConstructor carried = findEngine(craft);
            require(carried.maxwellFuel() == 650, "carried snapshot keeps exact tank contents");
            IFluidHandler carriedInput = inlet(carried, carried.facing());
            require(carriedInput.fill(new FluidStack(ethanol, 250), true) == 250 && carried.maxwellFuel() == 900,
                    "live carried capability feeds the owner tank");
            craft.signals.persist();
            NBTTagCompound persisted = craft.structure.at(carried.getPos()).tileData();
            require(persisted.getCompoundTag("MaxwellFuel").getInteger("Amount") == 900,
                    "vehicle saves the tank through the normal dirty-tile path");
            VehicleWeight estimate = new VehicleWeight(craft.structure, craft.view);
            while (!estimate.step(1024, Long.MAX_VALUE)) { }
            require(estimate.result().units >= 160 && estimate.result().units < 210,
                    "weight includes one engine and its fuel without counting every reservation");
        } finally {
            craft.setDead();
        }
    }

    private static TileEntityConstructor findEngine(EntityGroundVehicle craft) {
        for (TileEntity tile : craft.view.tiles.values()) {
            if (tile instanceof TileEntityConstructor && ((TileEntityConstructor) tile).maxwell()) return (TileEntityConstructor) tile;
        }
        throw new IllegalStateException("Missing carried engine");
    }

    private static void tick(WorldServer world, TileEntityConstructor engine) {
        world.setTotalWorldTime(world.getTotalWorldTime() + 1);
        engine.update();
    }

    private static EntityGroundVehicle capture(WorldServer world, TileEntityConstructor engine) {
        List<VehicleStructure.Cell> cells = new ArrayList<>();
        BlockPos origin = engine.getPos().subtract(engine.pivot());
        for (BlockPos at : BlockPos.getAllInBox(origin, origin.add(engine.structureWidth() - 1,
                engine.structureHeight() - 1, engine.structureDepth() - 1))) {
            TileEntity tile = world.getTileEntity(at);
            NBTTagCompound saved = VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()), BlockPos.ORIGIN.subtract(origin));
            cells.add(new VehicleStructure.Cell(at.subtract(origin), world.getBlockState(at), saved));
        }
        EntityGroundVehicle craft = new EntityGroundVehicle(world);
        craft.install(DriveRuntimeChecks.withCrew(cells, engine.facing()));
        craft.setPosition(ROOT.getX(), ROOT.getY() + 12, ROOT.getZ());
        craft.signals.open();
        craft.signals.tick();
        return craft;
    }

    private static void clear(WorldServer world, TileEntityConstructor engine, int width, int depth) {
        BlockPos origin = engine.getPos().subtract(engine.pivot());
        for (BlockPos at : BlockPos.getAllInBox(origin, origin.add(engine.layout().extentX() - 1, width - 1,
                engine.layout().extentZ() - 1))) if (!at.equals(engine.getPos())) world.setBlockState(at, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(engine.getPos(), Blocks.AIR.getDefaultState(), 2);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException("Maxwell Drive: " + message);
    }
}
