package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Installed mounted-control behavior needed by the shared latch, transmission and dialog host. */
public interface NativeMountedControl {
    boolean isMomentary();
    boolean powered(BlockState state);
    BlockState withPowered(BlockState state,boolean value);
    /** Direction from this control toward its physical support. */
    Direction supportDirection(BlockState state);
    void dropUnsupported(BlockState state,ServerLevel level,BlockPos pos);
}
