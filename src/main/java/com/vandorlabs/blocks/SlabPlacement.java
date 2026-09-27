package com.vandorlabs.blocks;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;

/** Recognizes both vanilla/modded slabs and our configurable half block. */
public final class SlabPlacement {
    private SlabPlacement() { }
    public static Boolean upperHalf(IBlockState state) {
        if (state.getBlock() instanceof BlockProgrammableSlab)
            return state.getValue(BlockProgrammableSlab.HALF) == BlockSlab.EnumBlockHalf.TOP;
        if (state.getBlock() instanceof BlockSlab && !((BlockSlab)state.getBlock()).isDouble())
            return state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP;
        return null;
    }
}
