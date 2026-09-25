package com.vandorlabs.blocks;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Programmable panels with the four-pixel depth of Programmable Glass. */
public class BlockProgrammableWall extends BlockAnimatedScreenSelector {
    public enum Shape { PLAIN, PORTHOLE, DIAGONAL, DIAGONAL_CORNER }

    public static final PropertyDirection FACING = PropertyDirection.create(
            "facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool INVERTED = PropertyBool.create("inverted");
    private static final AxisAlignedBB PANEL = new AxisAlignedBB(0, 0, 6 / 16D,
            1, 1, 10 / 16D);
    private final Shape shape;

    public BlockProgrammableWall(String name, Shape shape) {
        super(name);
        this.shape = shape;
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(INVERTED, false));
        setLightLevel(0);
        useNeighborBrightness = true;
    }

    public Shape getShape() { return shape; }

    private boolean isDiagonalShape() {
        return shape == Shape.DIAGONAL || shape == Shape.DIAGONAL_CORNER;
    }

    @Override protected IProperty<EnumFacing> facingProperty() { return FACING; }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, INVERTED);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer) {
        boolean inverted = isDiagonalShape() && (side == EnumFacing.DOWN
                || (side.getAxis().isHorizontal() && hitY > .5F));
        return getDefaultState()
                .withProperty(FACING, placementFacing(world, pos, side, placer))
                .withProperty(INVERTED, inverted);
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(INVERTED, (meta & 4) != 0 && isDiagonalShape());
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(INVERTED) ? 4 : 0);
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        return isDiagonalShape() ? FULL_BLOCK_AABB
                : rotate(PANEL, state.getValue(FACING));
    }

    @Override public void addCollisionBoxToList(IBlockState state, World world,
            BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> boxes,
            @Nullable Entity entity, boolean isActualState) {
        if (!isDiagonalShape()) {
            addCollisionBoxToList(pos, entityBox, boxes,
                    rotate(PANEL, state.getValue(FACING)));
            return;
        }
        // Collision approximates the straight rendered slab at one-pixel
        // vertical intervals. The visible mesh itself is a single plane.
        for (int slice = 0; slice < 16; slice++) {
            double center = (slice + .5D) / 16D;
            double near = state.getValue(INVERTED)
                    ? 6D * (1D - center) : 6D * center;
            if (shape == Shape.DIAGONAL_CORNER) {
                double edge = 16D - near;
                AxisAlignedBB first = new AxisAlignedBB(0, slice / 16D,
                        near / 16D, edge / 16D,
                        (slice + 1) / 16D, (near + 4D) / 16D);
                AxisAlignedBB second = new AxisAlignedBB((edge - 4D) / 16D,
                        slice / 16D, near / 16D, edge / 16D,
                        (slice + 1) / 16D, 1);
                addCollisionBoxToList(pos, entityBox, boxes,
                        rotate(first, state.getValue(FACING)));
                addCollisionBoxToList(pos, entityBox, boxes,
                        rotate(second, state.getValue(FACING)));
                continue;
            }
            AxisAlignedBB box = new AxisAlignedBB(0, slice / 16D, near / 16D,
                    1, (slice + 1) / 16D, (near + 4D) / 16D);
            addCollisionBoxToList(pos, entityBox, boxes,
                    rotate(box, state.getValue(FACING)));
        }
    }

    private static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing facing) {
        switch (facing) {
            case SOUTH: return new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                    1 - box.minX, box.maxY, 1 - box.minZ);
            case EAST: return new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX);
            case WEST: return new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                    box.maxZ, box.maxY, 1 - box.minX);
            default: return box;
        }
    }
}
