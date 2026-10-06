package com.vandorlabs.redstone;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Vanilla weak/strong power rules without loading adjacent chunks to read them. */
public final class LoadedRedstonePower {
    private static final EnumFacing[] SIDES = EnumFacing.values();
    private LoadedRedstonePower() { }

    public static boolean isPowered(World world, BlockPos pos) {
        return level(world,pos)>0;
    }

    public static int level(World world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return 0;
        int result=0;
        for (EnumFacing side : SIDES) {
            BlockPos neighbor = pos.offset(side);
            if (!world.isBlockLoaded(neighbor)) continue;
            IBlockState state = world.getBlockState(neighbor);
            if (state.getBlock().shouldCheckWeakPower(state, world, neighbor, side)) {
                for (EnumFacing strongSide : SIDES) {
                    BlockPos source = neighbor.offset(strongSide);
                    if (world.isBlockLoaded(source))
                        result=Math.max(result,world.getBlockState(source).getStrongPower(world, source, strongSide));
                }
            } else result=Math.max(result,state.getWeakPower(world, neighbor, side));
            if(result>=15)return 15;
        }
        return Math.min(15,result);
    }
}
