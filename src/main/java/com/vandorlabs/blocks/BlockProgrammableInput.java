package com.vandorlabs.blocks;

import com.vandorlabs.render.InputSurfaceLayout;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Selectable half-depth input surface. A normal side click mounts a thin
 * vertical panel in the clicked half; sneaking folds that same panel out as
 * a horizontal keyboard shelf. This deliberately mirrors trapdoor placement
 * without adding an interactive open/closed state.
 */
public class BlockProgrammableInput extends BlockAnimatedScreenSelector {

    public static final String NAME = "programmable_half_input";
    public static final PropertyDirection FACING = PropertyDirection.create(
            "facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool KEYBOARD = PropertyBool.create("keyboard");
    public static final PropertyBool UPPER = PropertyBool.create("upper");

    public static final double SMALL_SCALE = InputSurfaceLayout.SMALL_SCALE;

    public BlockProgrammableInput() {
        this(NAME);
    }

    protected BlockProgrammableInput(String name) {
        super(name);
        setDefaultState(this.blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(KEYBOARD, false)
                .withProperty(UPPER, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, KEYBOARD, UPPER);
    }

    @Override
    protected IProperty<EnumFacing> facingProperty() {
        return FACING;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        boolean sideFace = side.getAxis().isHorizontal();
        boolean keyboard = !sideFace || placer.isSneaking();
        // Wall panels still use the supporting face because it defines their
        // mount. Horizontal keyboard placement uses the same player-facing
        // convention as the rest of the programmable family.
        EnumFacing facing = sideFace ? side
                : placementFacing(world, pos, side, placer);
        // UPPER remains the legacy/fallback wall position. The exact
        // bottom/middle/top wall slot is recorded by ItemProgrammableInput
        // in the tile entity because all four metadata bits are already in
        // use. An underside
        // click selects the upper plane; top-face and sneak/lower clicks use
        // the mid-height shelf at the top of the lower half.
        boolean upper = side == EnumFacing.DOWN || (sideFace && hitY > 0.5F);
        return getDefaultState().withProperty(FACING, facing)
                .withProperty(KEYBOARD, keyboard)
                .withProperty(UPPER, upper);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(KEYBOARD, (meta & 4) != 0)
                .withProperty(UPPER, (meta & 8) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(KEYBOARD) ? 4 : 0)
                | (state.getValue(UPPER) ? 8 : 0);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector selector = null;
        TileEntity tile = source.getTileEntity(pos);
        if (tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector) {
            selector = (com.vandorlabs.tiles.TileEntityAnimatedScreenSelector) tile;
        }
        boolean small = selector != null && selector.isSmallInput();
        int wallPosition=state.getValue(UPPER)?2:0;
        if (selector!=null) wallPosition=selector.getWallPosition(wallPosition);
        InputSurfaceLayout.Box box=InputSurfaceLayout.halfInput(state.getValue(KEYBOARD),
                state.getValue(UPPER),wallPosition,small).housing;
        AxisAlignedBB local=new AxisAlignedBB(box.x0/16,box.y0/16,box.z0/16,
                box.x1/16,box.y1/16,box.z1/16);
        return rotateFromNorth(local, state.getValue(FACING));
    }

    /** Maps a side-face click into equally sized bottom, middle and top slots. */
    public static int wallPositionForHit(float hitY) {
        return hitY < 1.0F / 3.0F ? 0 : hitY < 2.0F / 3.0F ? 1 : 2;
    }

    protected static AxisAlignedBB rotateFromNorth(AxisAlignedBB box, EnumFacing facing) {
        return PanelPlacement.rotateFromNorth(box, facing);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }
}
