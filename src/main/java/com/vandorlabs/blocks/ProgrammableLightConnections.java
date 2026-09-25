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
        return members(first, state, true);
    }

    private static Set<BlockPos> members(TileEntityProgrammableLight first,
            IBlockState state, boolean matchOn) {
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
                        || !eligible(world, next, facing, first, matchOn)) continue;
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

    /** Reconcile only loaded assemblies touched by a signal or topology change. */
    public static void refreshAround(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) return;
        Set<BlockPos> visited = new LinkedHashSet<>();
        Set<BlockPos> seeds = new LinkedHashSet<>();
        seeds.add(pos);
        for (EnumFacing side : EnumFacing.values()) seeds.add(pos.offset(side));
        for (BlockPos seed : seeds) {
            if (visited.contains(seed) || !world.isBlockLoaded(seed)) continue;
            TileEntity raw = world.getTileEntity(seed);
            if (!(raw instanceof TileEntityProgrammableLight) || !((TileEntityProgrammableLight) raw).isAvailableForJoining()
                    || world.getBlockState(seed).getBlock() != ModBlocks.PROGRAMMABLE_LIGHT) continue;
            Set<BlockPos> group = members((TileEntityProgrammableLight) raw,
                    world.getBlockState(seed), false);
            visited.addAll(group);
            boolean powered = false;
            for (BlockPos member : group)
                powered |= ((TileEntityProgrammableLight) world.getTileEntity(member)).hasDirectTriggerPower();
            for (BlockPos member : group)
                ((TileEntityProgrammableLight) world.getTileEntity(member)).setJoinedTriggerPower(powered);
        }
    }

    private static boolean eligible(World world, BlockPos pos, EnumFacing facing,
            TileEntityProgrammableLight first, boolean matchOn) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != ModBlocks.PROGRAMMABLE_LIGHT
                || state.getValue(BlockAnimatedScreenSelector.FACING) != facing) return false;
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileEntityProgrammableLight)) return false;
        TileEntityProgrammableLight other = (TileEntityProgrammableLight) raw;
        return other.isAvailableForJoining() && other.isJoin() && other.getTexture() == first.getTexture()
                && (!matchOn || other.isOn() == first.isOn());
    }
}
