package com.vandorlabs.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockDisplaySequenced extends BlockVandorDirectional {

    public static final PropertyInteger MODE = PropertyInteger.create("mode", 1, 3);

    public static final int MODE_STATIC = 1;
    public static final int MODE_ANIMATED = 2;
    public static final int MODE_OFF = 3;

    public BlockDisplaySequenced(String name) {
        super(name);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(MODE, MODE_STATIC));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, MODE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(MODE, Math.min(((meta >> 2) & 3) + 1, MODE_OFF));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | ((state.getValue(MODE) - 1) << 2);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            int mode = state.getValue(MODE);
            int next = mode % 3 + 1;
            if (next == MODE_OFF && world.isBlockPowered(pos)) {
                next = MODE_ANIMATED;
            }
            world.setBlockState(pos, state.withProperty(MODE, next), 3);
        }
        return true;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        // Wake-only: a powered OFF screen starts animating. Deliberately no
        // auto-sleep -- screens have no power memory, so any neighbor update
        // would otherwise kill a hand-set animated screen (same bug class as
        // the old door behavior of slamming shut on nearby block updates).
        if (!world.isRemote) {
            boolean powered = world.isBlockPowered(pos);
            int mode = state.getValue(MODE);
            if (powered && mode == MODE_OFF) {
                world.setBlockState(pos, state.withProperty(MODE, MODE_ANIMATED), 3);
            }
        }
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(MODE) == MODE_OFF ? 0 : 7;
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
