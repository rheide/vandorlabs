package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockDisplayArm;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import com.vandorlabs.vehicle.MovingBlockSupport;
import com.vandorlabs.vehicle.VehicleRotation;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Actual moving snapshots preserve the independently mounted base through cardinal yaw. */
public final class DisplayArmRotationChecks {
    private static final BlockPos AT = new BlockPos(2, 1, 3);
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.proxy = new com.vandorlabs.CommonProxy();
        BlockDisplayArm block = (BlockDisplayArm)ModBlocks.PROGRAMMABLE_DISPLAY_ARM;
        // A headless bootstrap has no active mod container to supply this namespace.
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(
                net.minecraftforge.registries.IForgeRegistryEntry.Impl.class, block, null, "registryName");
        block.setRegistryName("vandorlabs", BlockDisplayArm.NAME);
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityDisplayArm.class,
                new ResourceLocation("minecraft:display_arm_rotation_checks"));
        NonRenderingChecks.MemoryWorld parent = new NonRenderingChecks.MemoryWorld(false);
        Method foldedState = Class.forName("com.vandorlabs.gear.ArmGridTiles")
                .getDeclaredMethod("state", VehicleStructure.Cell.class, int[].class);
        foldedState.setAccessible(true);
        int[] fold = {1, 0, 0, 0, 0, -1, 0, 1, 0};

        for (EnumFacing mount : EnumFacing.values()) for (EnumFacing facing : EnumFacing.HORIZONTALS)
            for (int variant = 0; variant < 4; variant++) {
                TileEntityDisplayArm tile = (TileEntityDisplayArm)block.createNewTileEntity(parent, 0);
                tile.setPos(AT);
                tile.setMountFace(mount);
                DisplayArmSettings settings = new DisplayArmSettings((variant & 1) != 0, (variant & 2) != 0,
                        -19, 11, 27, 37, -48, 23);
                tile.setArmSettings(settings);
                tile.setInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[1]);
                NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
                NBTTagCompound opaque = new NBTTagCompound();
                opaque.setByteArray("Bytes", new byte[]{17, -1, 42, 0, 3});
                NBTTagCompound similarlyNamed = new NBTTagCompound();
                similarlyNamed.setByte("Mount", (byte)EnumFacing.NORTH.getIndex());
                opaque.setTag(TileEntityDisplayArm.SETTINGS, similarlyNamed);
                saved.setTag("ForgeCaps", opaque);
                IBlockState state = block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING, facing);
                VehicleStructure source = VehicleStructure.display(Arrays.asList(
                        new VehicleStructure.Cell(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), null),
                        new VehicleStructure.Cell(new BlockPos(4, 2, 6), Blocks.STONE.getDefaultState(), null),
                        new VehicleStructure.Cell(AT.offset(mount.getOpposite()), Blocks.STONE.getDefaultState(), null),
                        new VehicleStructure.Cell(AT, state, saved)));
                NBTTagCompound original = source.write();
                VehicleWorld view = new VehicleWorld(parent, source);
                MovingBlockSupport.validate(source, view);
                require(foldedState.invoke(null, source.at(AT), fold) == null,
                        "untyped arbitrary folded payload was admitted");

                DisplayArmGeometry before = ((TileEntityDisplayArm)view.getTileEntity(AT)).geometry();
                for (int turns = 0; turns < 4; turns++) {
                    Rotation rotation = Rotation.values()[turns];
                    VehicleStructure rotated = VehicleRotation.rotate(source, view, turns);
                    BlockPos pos = VehicleRotation.cell(AT, 5, 7, turns);
                    VehicleStructure.Cell cell = rotated.at(pos);
                    NBTTagCompound expected = source.at(AT).tileData();
                    expected.setInteger("x", pos.getX());
                    expected.setInteger("y", pos.getY());
                    expected.setInteger("z", pos.getZ());
                    expected.getCompoundTag(TileEntityDisplayArm.SETTINGS)
                            .setByte("Mount", (byte)rotation.rotate(mount).getIndex());
                    require(expected.equals(cell.tileData()), "rotation rewrote pose, artwork or opaque capability NBT");
                    require(cell.state.getValue(BlockAnimatedScreenSelector.FACING) == rotation.rotate(facing),
                            "screen direction did not rotate with the structure");
                    VehicleWorld turnedView = new VehicleWorld(parent, rotated);
                    MovingBlockSupport.validate(rotated, turnedView);
                    TileEntityDisplayArm turned = (TileEntityDisplayArm)turnedView.getTileEntity(pos);
                    require(turned.getMountFace() == rotation.rotate(mount) && turned.getArmSettings().equals(settings),
                            "local tile lost mounted pose");
                    BlockPos support = pos.offset(turned.getMountFace().getOpposite());
                    require(rotated.at(support) != null && rotated.at(support).state.getBlock() == Blocks.STONE,
                            "rotated arm no longer touches its captured support");
                    DisplayArmGeometry after = turned.geometry();
                    close(placed(after.display.center, pos), point(placed(before.display.center, AT), turns),
                            "screen position changed relative to the rotating structure");
                    close(after.display.xAxis, vector(before.display.xAxis, turns), "screen right axis");
                    close(after.display.yAxis, vector(before.display.yAxis, turns), "screen height axis");
                    close(after.display.zAxis, vector(before.display.zAxis, turns), "screen depth axis");
                    close(placed(after.baseCenter, pos), point(placed(before.baseCenter, AT), turns), "mount contact");
                    close(placed(after.rearSocket, pos), point(placed(before.rearSocket, AT), turns), "screen attachment");
                    require(original.equals(source.write()), "rotation mutated the authoritative source snapshot");
                }
                VehicleStructure cycle = source;
                for (int turns = 0; turns < 4; turns++)
                    cycle = VehicleRotation.rotate(cycle, new VehicleWorld(parent, cycle), 1);
                require(original.equals(cycle.write()), "four turns did not restore complete state, mount, pose and NBT");
            }
        System.out.println("Display arm rotation PASS " + checks
                + " all mounts/facings/variants/cardinal turns, exact NBT and opaque capabilities, folded-payload refusal");
    }

    private static DisplayArmGeometry.Vec placed(DisplayArmGeometry.Vec pixels, BlockPos pos) {
        return new DisplayArmGeometry.Vec(pixels.x / 16 + pos.getX(), pixels.y / 16 + pos.getY(), pixels.z / 16 + pos.getZ());
    }

    private static DisplayArmGeometry.Vec point(DisplayArmGeometry.Vec p, int turns) {
        switch (turns) {
            case 1: return new DisplayArmGeometry.Vec(7 - p.z, p.y, p.x);
            case 2: return new DisplayArmGeometry.Vec(5 - p.x, p.y, 7 - p.z);
            case 3: return new DisplayArmGeometry.Vec(p.z, p.y, 5 - p.x);
            default: return p;
        }
    }

    private static DisplayArmGeometry.Vec vector(DisplayArmGeometry.Vec p, int turns) {
        switch (turns) {
            case 1: return new DisplayArmGeometry.Vec(-p.z, p.y, p.x);
            case 2: return new DisplayArmGeometry.Vec(-p.x, p.y, -p.z);
            case 3: return new DisplayArmGeometry.Vec(p.z, p.y, -p.x);
            default: return p;
        }
    }

    private static void close(DisplayArmGeometry.Vec actual, DisplayArmGeometry.Vec expected, String message) {
        require(Math.abs(actual.x - expected.x) < 1e-8 && Math.abs(actual.y - expected.y) < 1e-8
                && Math.abs(actual.z - expected.z) < 1e-8, message);
    }
}
