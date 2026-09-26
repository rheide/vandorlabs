package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.properties.IProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Full solid cube whose six faces share one selectable finish. */
public class BlockProgrammableBlock extends BlockAnimatedScreenSelector {
    public BlockProgrammableBlock() {
        this("programmable_block");
    }

    protected BlockProgrammableBlock(String name) {
        super(name);
        setLightLevel(0);
    }

    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING},
                new IUnlistedProperty<?>[]{ProgrammableHousingState.FINISH,
                        ProgrammableHousingState.TILE_SIDES, ProgrammableHousingState.VISIBLE,
                        ProgrammableHousingState.LIGHT});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ProgrammableHousingState.extend(state, world, pos, false);
    }

    @Override public int getPackedLightmapCoords(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ProgrammableHousingState.light(state, world, pos);
    }

    @Override public int getLightValue(IBlockState state, IBlockAccess world,
            BlockPos pos) { return 0; }
}
