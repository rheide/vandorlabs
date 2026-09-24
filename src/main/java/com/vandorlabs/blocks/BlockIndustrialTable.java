package com.vandorlabs.blocks;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Minecraft 1.12.2 / Forge; MCP names. One block per table module. */
public final class BlockIndustrialTable extends BlockVandor {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    // TRUE means connected: that side's strut is hidden.
    public static final PropertyBool LEFT = PropertyBool.create("left");
    public static final PropertyBool RIGHT = PropertyBool.create("right");
    public static final PropertyBool UPSIDE_DOWN = PropertyBool.create("upside_down");

    public BlockIndustrialTable() {
        super("industrial_table");
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(LEFT, false).withProperty(RIGHT, false)
                .withProperty(UPSIDE_DOWN, false));
    }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, LEFT, RIGHT, UPSIDE_DOWN);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(UPSIDE_DOWN) ? 4 : 0);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(UPSIDE_DOWN, (meta & 4) != 0);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite())
                .withProperty(UPSIDE_DOWN, side == EnumFacing.DOWN);
    }
    private boolean connects(IBlockAccess world, BlockPos pos, EnumFacing front, boolean upsideDown) {
        if (world instanceof World && !((World) world).isBlockLoaded(pos)) return false;
        IBlockState neighbor = world.getBlockState(pos);
        return neighbor.getBlock() == this && neighbor.getValue(FACING) == front
                && neighbor.getValue(UPSIDE_DOWN) == upsideDown;
    }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing front = state.getValue(FACING);
        boolean upsideDown = state.getValue(UPSIDE_DOWN);
        return state.withProperty(LEFT, connects(world, pos.offset(front.rotateYCCW()), front, upsideDown))
                .withProperty(RIGHT, connects(world, pos.offset(front.rotateY()), front, upsideDown));
    }
    private void refresh(World world, BlockPos pos) {
        // Include the adjacent modules, including across a chunk boundary.
        world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        refresh(world, pos);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            Block changedBlock, BlockPos fromPos) {
        refresh(world, pos);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        refresh(world, pos);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)))
                .withProperty(LEFT, false).withProperty(RIGHT, false);
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return withRotation(state, mirror.toRotation(state.getValue(FACING)));
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state,
            BlockPos pos, EnumFacing face) {
        return face == (state.getValue(UPSIDE_DOWN) ? EnumFacing.DOWN : EnumFacing.UP)
                ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
    @Override public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == (state.getValue(UPSIDE_DOWN) ? EnumFacing.DOWN : EnumFacing.UP);
    }

    private AxisAlignedBB turn(AxisAlignedBB box, EnumFacing facing) {
        int turns = facing == EnumFacing.EAST ? 1 : facing == EnumFacing.SOUTH ? 2 : facing == EnumFacing.WEST ? 3 : 0;
        for (int i = 0; i < turns; ++i) {
            box = new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX);
        }
        return box;
    }
    private void addBox(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> out, EnumFacing facing,
            boolean upsideDown, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (upsideDown) { double oldY0=y0; y0=16-y1; y1=16-oldY0; }
        addCollisionBoxToList(pos, query, out, turn(new AxisAlignedBB(x0/16, y0/16, z0/16, x1/16, y1/16, z1/16), facing));
    }
    private void addStrut(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> out, EnumFacing facing,
            boolean upsideDown, double x0) {
        // Eight small AABBs approximate a diagonal prism for collision only.
        // Visual geometry is one straight rotated cuboid per side.
        double start = 9 - 8 / Math.sqrt(2), end = 9 + 8 / Math.sqrt(2);
        for (int i = 0; i < 8; ++i) {
            double y0 = start + (end-start)*i/8, y1 = start + (end-start)*(i+1)/8;
            addBox(pos, query, out, facing, upsideDown, x0, y0 - 0.707107, 17-y1-0.707107,
                    x0+2, y1+0.707107, 17-y0+0.707107);
        }
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB query, List<AxisAlignedBB> boxes, Entity entity, boolean actualState) {
        IBlockState connected = getActualState(state, world, pos);
        EnumFacing facing = connected.getValue(FACING);
        boolean upsideDown = connected.getValue(UPSIDE_DOWN);
        addBox(pos, query, boxes, facing, upsideDown, 0,14,0,16,16,16);
        addBox(pos, query, boxes, facing, upsideDown, 0,0,14,16,14,16);
        if (!connected.getValue(LEFT)) addStrut(pos, query, boxes, facing, upsideDown, 0.5);
        if (!connected.getValue(RIGHT)) addStrut(pos, query, boxes, facing, upsideDown, 13.5);
    }
}
