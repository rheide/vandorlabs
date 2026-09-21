package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Solid triangular-prism programmable display with stair-style horizontal
 * facing and upper/lower placement. Its arbitrary triangular housing and
 * diagonal live face are rendered by the programmable-screen TESR.
 */
public class BlockProgrammableDiagonalScreen extends BlockAnimatedScreenSelector {

    public static final String NAME = "programmable_diagonal_screen";
    public static final PropertyDirection FACING = PropertyDirection.create(
            "facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool INVERTED = PropertyBool.create("inverted");

    public BlockProgrammableDiagonalScreen() {
        super(NAME);
        setDefaultState(this.blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(INVERTED, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, INVERTED);
    }

    @Override
    protected IProperty<EnumFacing> facingProperty() {
        return FACING;
    }

    /**
     * Stair-style placement: horizontal direction follows the player, while
     * clicking a side's upper half (or a block underside) flips the wedge.
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        boolean inverted = side == EnumFacing.DOWN
                || (side.getAxis().isHorizontal() && hitY > 0.5F);
        return getDefaultState()
                .withProperty(FACING, placementFacing(world, pos, side, placer))
                .withProperty(INVERTED, inverted);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(INVERTED, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(INVERTED) ? 4 : 0);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
