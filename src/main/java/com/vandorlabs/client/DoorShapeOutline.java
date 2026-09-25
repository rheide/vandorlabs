package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One outline across a contiguous row of matching two-block doors. */
final class DoorShapeOutline {
    final int columns;
    final int column;
    final PortholeHex outline;

    private DoorShapeOutline(int columns, int column, int shape) {
        this.columns = columns;
        this.column = column;
        this.outline = new PortholeHex(vertices(columns * 16D, shape));
    }

    PortholeHex.Slice slice(int row) { return outline.slice(column, row); }

    static DoorShapeOutline group(TileEntitySpaceDoor tile, IBlockState state) {
        EnumFacing facing = state.getValue(BlockVandorDoor.FACING);
        // SOUTH is the model's native orientation, where +X points east.
        EnumFacing positive = facing.rotateYCCW();
        int before = 0, after = 0;
        for (int distance = 1; distance < 64; distance++) {
            if (!matches(tile, state, tile.getPos().offset(positive.getOpposite(), distance))) break;
            before++;
        }
        for (int distance = 1; distance < 64 - before; distance++) {
            if (!matches(tile, state, tile.getPos().offset(positive, distance))) break;
            after++;
        }
        return new DoorShapeOutline(before + 1 + after, before, tile.getShape());
    }

    private static boolean matches(TileEntitySpaceDoor source, IBlockState state,
            BlockPos pos) {
        World world = source.getWorld();
        if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(pos.up())) return false;
        IBlockState other = world.getBlockState(pos);
        if (other.getBlock() != state.getBlock()
                || other.getValue(BlockVandorDoor.HALF) != BlockDoor.EnumDoorHalf.LOWER
                || other.getValue(BlockVandorDoor.FACING)
                != state.getValue(BlockVandorDoor.FACING)
                || world.getBlockState(pos.up()).getBlock() != other.getBlock()) return false;
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileEntitySpaceDoor)) return false;
        TileEntitySpaceDoor tile = (TileEntitySpaceDoor) raw;
        return tile.getShape() == source.getShape()
                && tile.getPlacementDepth() == source.getPlacementDepth()
                && tile.isSliding() == source.isSliding()
                && tile.isFramed() == source.isFramed();
    }

    private static double[][] vertices(double width, int shape) {
        if (shape == 0) {
            double bevel = Math.min(4, width / 4);
            return new double[][] {{bevel, 0}, {width - bevel, 0}, {width, 16},
                    {width - bevel, 32}, {bevel, 32}, {0, 16}};
        }
        if (shape == 1) {
            double bevel = Math.min(4, width / 4);
            return new double[][] {{bevel, 0}, {width - bevel, 0},
                    {width, 5}, {width, 27}, {width - bevel, 32},
                    {bevel, 32}, {0, 27}, {0, 5}};
        }
        if (shape == 3) {
            double dx = Math.max(1, Math.floor(width / 8));
            return new double[][] {{3*dx,0},{width-3*dx,0},
                    {width-3*dx,2},{width-2*dx,2},
                    {width-2*dx,4},{width-dx,4},
                    {width-dx,7},{width,7},{width,25},
                    {width-dx,25},{width-dx,28},{width-2*dx,28},
                    {width-2*dx,30},{width-3*dx,30},
                    {width-3*dx,32},{3*dx,32},
                    {3*dx,30},{2*dx,30},{2*dx,28},{dx,28},
                    {dx,25},{0,25},{0,7},{dx,7},
                    {dx,4},{2*dx,4},{2*dx,2},{3*dx,2}};
        }
        return new double[][] {{0,0},{width,0},{width,32},{0,32}};
    }
}
