package com.vandorlabs.neoforge.seating;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
/** Standalone-first boundary: two-cell seating never enters a one-cell moving handoff. */
public final class NativeSeatCapture {
    public static void requireStandalone(ServerLevel level,Iterable<BlockPos> positions){if(!level.getServer().isSameThread())throw new IllegalStateException("Seat capture thread");for(var pos:positions){if(!NativeSeatBlock.loaded(level,pos))throw new IllegalArgumentException("Load the complete seat before moving it");if(level.getBlockState(pos).getBlock() instanceof NativeSeatBlock)throw new IllegalArgumentException("Stationary seats must remain native world blocks");}}
    private NativeSeatCapture(){}
}
