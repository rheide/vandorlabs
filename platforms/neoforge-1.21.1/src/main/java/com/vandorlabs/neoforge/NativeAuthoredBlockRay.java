package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Opt-in authored ray intersection, independent of the block's physical/outline voxel grid. */
public interface NativeAuthoredBlockRay {
    /** Null continues ordinary DDA traversal; caller-specific empty shapes are filtered beforehand. */
    BlockHitResult traceAuthored(BlockState state,BlockGetter world,BlockPos pos,Vec3 start,Vec3 end);
}
