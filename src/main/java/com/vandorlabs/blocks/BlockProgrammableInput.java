package com.vandorlabs.blocks;

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

    public static final String NAME = "programmable_input";
    public static final PropertyDirection FACING = PropertyDirection.create(
            "facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool KEYBOARD = PropertyBool.create("keyboard");
    public static final PropertyBool UPPER = PropertyBool.create("upper");

    private static final AxisAlignedBB WALL_LOWER =
            new AxisAlignedBB(0, 0, 15.0 / 16.0, 1, 0.5, 1);
    private static final AxisAlignedBB WALL_MIDDLE =
            new AxisAlignedBB(0, 0.25, 15.0 / 16.0, 1, 0.75, 1);
    private static final AxisAlignedBB WALL_UPPER =
            new AxisAlignedBB(0, 0.5, 15.0 / 16.0, 1, 1, 1);
    private static final AxisAlignedBB KEYBOARD_LOWER =
            new AxisAlignedBB(0, 7.0 / 16.0, 0.5, 1, 0.5, 1);
    private static final AxisAlignedBB KEYBOARD_UPPER =
            new AxisAlignedBB(0, 15.0 / 16.0, 0.5, 1, 1, 1);
    public static final double SMALL_SCALE = 0.7D;

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
        EnumFacing facing = sideFace ? side : placer.getHorizontalFacing().getOpposite();
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
        AxisAlignedBB local;
        if (state.getValue(KEYBOARD)) {
            if (small) {
                double inset = (1.0D - SMALL_SCALE) / 2.0D;
                double minZ = 1.0D - 0.5D * SMALL_SCALE;
                double y = state.getValue(UPPER) ? 15.0D / 16.0D : 7.0D / 16.0D;
                local = new AxisAlignedBB(inset, y, minZ,
                        1.0D - inset, y + 1.0D / 16.0D, 1.0D);
            } else {
                local = state.getValue(UPPER) ? KEYBOARD_UPPER : KEYBOARD_LOWER;
            }
        } else {
            int wallPosition = state.getValue(UPPER) ? 2 : 0;
            if (selector != null) {
                wallPosition = selector.getWallPosition(wallPosition);
            }
            if (small) {
                double widthInset = (1.0D - SMALL_SCALE) / 2.0D;
                double height = 0.5D * SMALL_SCALE;
                double minY = wallPosition == 0 ? 0.0D
                        : wallPosition == 2 ? 1.0D - height : (1.0D - height) / 2.0D;
                local = new AxisAlignedBB(widthInset, minY, 15.0D / 16.0D,
                        1.0D - widthInset, minY + height, 1.0D);
            } else {
                local = wallPosition == 1 ? WALL_MIDDLE
                        : wallPosition == 2 ? WALL_UPPER : WALL_LOWER;
            }
        }
        return rotateFromNorth(local, state.getValue(FACING));
    }

    /** Maps a side-face click into equally sized bottom, middle and top slots. */
    public static int wallPositionForHit(float hitY) {
        return hitY < 1.0F / 3.0F ? 0 : hitY < 2.0F / 3.0F ? 1 : 2;
    }

    protected static AxisAlignedBB rotateFromNorth(AxisAlignedBB box, EnumFacing facing) {
        switch (facing) {
            case EAST:
                return new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                        1 - box.minZ, box.maxY, box.maxX);
            case SOUTH:
                return new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                        1 - box.minX, box.maxY, 1 - box.minZ);
            case WEST:
                return new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                        box.maxZ, box.maxY, 1 - box.minX);
            default:
                return box;
        }
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
