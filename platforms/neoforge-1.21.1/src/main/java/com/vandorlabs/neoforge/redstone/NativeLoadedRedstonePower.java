package com.vandorlabs.neoforge.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Released weak/strong sampling without requesting unloaded adjacent chunks. */
public final class NativeLoadedRedstonePower {
    private static final Direction[] SIDES=Direction.values();
    private NativeLoadedRedstonePower(){}
    private static boolean loaded(Level world,BlockPos pos){return !world.isOutsideBuildHeight(pos)
            &&world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    public static int level(Level world,BlockPos pos){
        if(world==null||pos==null||world instanceof net.minecraft.server.level.ServerLevel server&&!NativeRedstoneChannels.open(server)||!loaded(world,pos))return 0;
        int result=0;
        for(Direction side:SIDES){
            var neighbor=pos.relative(side);if(!loaded(world,neighbor))continue;var state=world.getBlockState(neighbor);
            if(state.shouldCheckWeakPower(world,neighbor,side)){
                for(Direction strong:SIDES){var source=neighbor.relative(strong);if(loaded(world,source))
                    result=Math.max(result,world.getBlockState(source).getDirectSignal(world,source,strong));}
            }else result=Math.max(result,state.getSignal(world,neighbor,side));
            if(result>=15)return 15;
        }
        return Math.min(15,result);
    }
}
