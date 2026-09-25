package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Full solid cube whose six faces share one selectable finish. */
public class BlockProgrammableBlock extends BlockAnimatedScreenSelector {
    public BlockProgrammableBlock() {
        this("programmable_block");
    }

    protected BlockProgrammableBlock(String name) {
        super(name);
        setLightLevel(0);
    }

    @Override public int getLightValue(IBlockState state, IBlockAccess world,
            BlockPos pos) { return 0; }
}
