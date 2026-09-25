package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Full solid cube whose six faces share one selectable finish. */
public final class BlockProgrammableBlock extends BlockAnimatedScreenSelector {
    public BlockProgrammableBlock() {
        super("programmable_block");
        setLightLevel(0);
    }

    @Override public int getLightValue(IBlockState state, IBlockAccess world,
            BlockPos pos) { return 0; }
}
