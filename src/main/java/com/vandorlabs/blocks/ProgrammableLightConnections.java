package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Adjacent light members shared by rendering and manual switching. */
public final class ProgrammableLightConnections {
    private ProgrammableLightConnections() { }

    public static Set<BlockPos> members(TileEntityProgrammableLight first,
            IBlockState state) {
        World world = first.getWorld();
        Set<BlockPos> members = new LinkedHashSet<>();
        members.add(first.getPos());
        if (!first.isJoin()) return members;
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        EnumFacing right = facing.getAxis().isHorizontal()
                ? facing.rotateY() : EnumFacing.EAST;
        EnumFacing up = facing == EnumFacing.UP ? EnumFacing.SOUTH
                : facing == EnumFacing.DOWN ? EnumFacing.NORTH : EnumFacing.UP;
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(first.getPos());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (EnumFacing side : new EnumFacing[]{right, right.getOpposite(),
                    up, up.getOpposite()}) {
                BlockPos next = current.offset(side);
                if (members.contains(next) || !world.isBlockLoaded(next)
                        || !eligible(world, next, facing, first)) continue;
                members.add(next);
                queue.addLast(next);
                if (members.size() >= 4096) {
                    members.clear();
                    members.add(first.getPos());
                    return members;
                }
            }
        }
        return members;
    }

    private static boolean eligible(World world, BlockPos pos, EnumFacing facing,
            TileEntityProgrammableLight first) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != ModBlocks.PROGRAMMABLE_LIGHT
                || state.getValue(BlockAnimatedScreenSelector.FACING) != facing) return false;
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileEntityProgrammableLight)) return false;
        TileEntityProgrammableLight other = (TileEntityProgrammableLight) raw;
        return other.isJoin() && other.getTexture() == first.getTexture()
                && other.isOn() == first.isOn();
    }
}
