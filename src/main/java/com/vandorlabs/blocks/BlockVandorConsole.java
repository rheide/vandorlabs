package com.vandorlabs.blocks;

import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockVandorConsole extends BlockVandor {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Vertical> VERTICAL = PropertyEnum.create("vertical", Vertical.class);

    public BlockVandorConsole(String name) {
        super(name);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(VERTICAL, Vertical.LEVEL));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, VERTICAL);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, placer, EnumHand.MAIN_HAND);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        if (facing == EnumFacing.UP) {
            return getDefaultState().withProperty(VERTICAL, Vertical.UP).withProperty(FACING, placer.getHorizontalFacing().getOpposite());
        } else if (facing == EnumFacing.DOWN) {
            return getDefaultState().withProperty(VERTICAL, Vertical.DOWN).withProperty(FACING, placer.getHorizontalFacing().getOpposite());
        }
        return getDefaultState().withProperty(VERTICAL, Vertical.LEVEL).withProperty(FACING, facing);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(VERTICAL, Vertical.values()[Math.min((meta >> 2) & 3, 2)]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(VERTICAL).ordinal() << 2);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
        return state.withRotation(mirrorIn.toRotation(state.getValue(FACING)));
    }
}
