package com.vandorlabs.blocks;

import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Emissive strip tile whose artwork turns vertically when placed on a wall. */
public class BlockLightStrip extends BlockVandor {

    public static final PropertyBool VERTICAL = PropertyBool.create("vertical");

    public BlockLightStrip(String name) {
        super(name, 1.0F);
        setDefaultState(blockState.getBaseState().withProperty(VERTICAL, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, VERTICAL);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(VERTICAL, side.getAxis().isHorizontal());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(VERTICAL, (meta & 1) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(VERTICAL) ? 1 : 0;
    }
}
