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
public final class BlockIndustrialDisplayTable extends BlockVandor {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    // TRUE means connected: that side's strut is hidden.
    public static final PropertyBool LEFT = PropertyBool.create("left");
    public static final PropertyBool RIGHT = PropertyBool.create("right");

    public BlockIndustrialDisplayTable() {
        super("industrial_display_table");
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(LEFT, false).withProperty(RIGHT, false));
    }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, LEFT, RIGHT);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3));
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    private boolean connects(IBlockAccess world, BlockPos pos, EnumFacing front) {
        if (world instanceof World && !((World) world).isBlockLoaded(pos)) return false;
        IBlockState neighbor = world.getBlockState(pos);
        return neighbor.getBlock() == this && neighbor.getValue(FACING) == front;
    }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing front = state.getValue(FACING);
        return state.withProperty(LEFT, connects(world, pos.offset(front.rotateYCCW()), front))
                .withProperty(RIGHT, connects(world, pos.offset(front.rotateY()), front));
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
        return face == EnumFacing.UP ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
    @Override public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == EnumFacing.UP;
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
            double x0, double y0, double z0, double x1, double y1, double z1) {
        addCollisionBoxToList(pos, query, out, turn(new AxisAlignedBB(x0/16, y0/16, z0/16, x1/16, y1/16, z1/16), facing));
    }
    private void addStrut(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> out, EnumFacing facing, double x0) {
        // Eight small AABBs approximate a diagonal prism for collision only.
        // Visual geometry is one straight rotated cuboid per side.
        double start = 9 - 8 / Math.sqrt(2), end = 9 + 8 / Math.sqrt(2);
        for (int i = 0; i < 8; ++i) {
            double y0 = start + (end-start)*i/8, y1 = start + (end-start)*(i+1)/8;
            addBox(pos, query, out, facing, x0, y0 - 0.707107, 17-y1-0.707107,
                    x0+2, y1+0.707107, 17-y0+0.707107);
        }
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB query, List<AxisAlignedBB> boxes, Entity entity, boolean actualState) {
        IBlockState connected = getActualState(state, world, pos);
        EnumFacing facing = connected.getValue(FACING);
        addBox(pos, query, boxes, facing, 0,14,0,16,16,16);
        addBox(pos, query, boxes, facing, 0,0,14,16,14,16);
        if (!connected.getValue(LEFT)) addStrut(pos, query, boxes, facing, 0.5);
        if (!connected.getValue(RIGHT)) addStrut(pos, query, boxes, facing, 13.5);
    }
}
