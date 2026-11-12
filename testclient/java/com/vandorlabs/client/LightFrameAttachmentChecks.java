package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockProgrammableLightFrame;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.BlockWall;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Exercise the vanilla wall's connection query against actual Light Frame states. */
public final class LightFrameAttachmentChecks {
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        BlockProgrammableLightFrame frame = new BlockProgrammableLightFrame();
        BlockPos pos = new BlockPos(8, 80, 8);
        int checks = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            // Missing tile data must describe the normal-size fallback geometry.
            for (int variant = 0; variant < 3; variant++) {
                boolean small = variant == 1;
                NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
                IBlockState state = frame.getDefaultState()
                        .withProperty(BlockAnimatedScreenSelector.FACING, facing);
                world.states.put(pos, state);
                if (variant != 2) {
                    TileEntityProgrammableLight tile = new TileEntityProgrammableLight();
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setBoolean("smallInput", small);
                    tile.readFromNBT(tag);
                    tile.setWorld(world);
                    tile.setPos(pos);
                    world.tiles.put(pos, tile);
                }
                AxisAlignedBB bounds = frame.getBoundingBox(state, world, pos);
                for (EnumFacing side : EnumFacing.values()) {
                    boolean expected = coversFace(bounds, side);
                    boolean solid = state.getBlockFaceShape(world, pos, side) == BlockFaceShape.SOLID;
                    require(solid == expected, "face shape", facing, side, variant);
                    checks++;
                    if (!side.getAxis().isHorizontal()) continue;
                    BlockPos wallPos = pos.offset(side);
                    IBlockState wall = Blocks.COBBLESTONE_WALL.getDefaultState();
                    world.states.put(wallPos, wall);
                    IBlockState actual = wall.getActualState(world, wallPos);
                    require(actual.getValue(connection(side.getOpposite())) == expected,
                            "vanilla wall connection", facing, side, variant);
                    world.states.remove(wallPos);
                    checks++;
                }
            }
        }
        System.out.println("Light Frame attachments PASS checks=" + checks);
    }

    private static boolean coversFace(AxisAlignedBB box, EnumFacing side) {
        boolean fullX = box.minX == 0 && box.maxX == 1;
        boolean fullY = box.minY == 0 && box.maxY == 1;
        boolean fullZ = box.minZ == 0 && box.maxZ == 1;
        switch (side) {
            case DOWN: return fullX && fullZ && box.minY == 0;
            case UP: return fullX && fullZ && box.maxY == 1;
            case NORTH: return fullX && fullY && box.minZ == 0;
            case SOUTH: return fullX && fullY && box.maxZ == 1;
            case WEST: return fullY && fullZ && box.minX == 0;
            case EAST: return fullY && fullZ && box.maxX == 1;
            default: throw new AssertionError(side);
        }
    }

    private static PropertyBool connection(EnumFacing side) {
        switch (side) {
            case NORTH: return BlockWall.NORTH;
            case SOUTH: return BlockWall.SOUTH;
            case WEST: return BlockWall.WEST;
            case EAST: return BlockWall.EAST;
            default: throw new IllegalArgumentException("Wall connections must be horizontal");
        }
    }

    private static void require(boolean condition, String query, EnumFacing facing,
            EnumFacing side, int variant) {
        if (!condition) throw new AssertionError(query + ": facing=" + facing
                + ", neighbor=" + side + ", variant=" + variant);
    }
}
