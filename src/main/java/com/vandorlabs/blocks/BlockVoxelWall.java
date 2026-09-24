package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Six-pixel voxel wall with collision matching the supplied stepped models. */
public final class BlockVoxelWall extends Block {
    public enum Shape { REGULAR, PORTHOLE, BOTTOM, TOP }
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private final Shape shape;
    public BlockVoxelWall(String name, Shape shape) {
        super(Material.IRON);
        this.shape = shape;
        setRegistryName(VandorLabs.MODID, name);
        setUnlocalizedName(VandorLabs.MODID + "." + name);
        setHardness(3F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return withRotation(state, mirror.toRotation(state.getValue(FACING)));
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getBlockLayer() {
        // Glass alpha survives; opaque pixels still have alpha=1 in this pass.
        return shape == Shape.PORTHOLE ? BlockRenderLayer.TRANSLUCENT : BlockRenderLayer.SOLID;
    }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing side) {
        return BlockFaceShape.UNDEFINED;
    }
    @Override public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return false; }
    private AxisAlignedBB turn(AxisAlignedBB b, EnumFacing facing) {
        int turns = facing == EnumFacing.EAST ? 1 : facing == EnumFacing.SOUTH ? 2 : facing == EnumFacing.WEST ? 3 : 0;
        for (int i=0; i<turns; ++i) b = new AxisAlignedBB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
        return b;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        boolean diagonal = shape == Shape.BOTTOM || shape == Shape.TOP;
        return turn(new AxisAlignedBB(0,0,diagonal ? 0 : .5,1,1,.875),state.getValue(FACING));
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB query, List<AxisAlignedBB> out, Entity entity, boolean actualState) {
        EnumFacing facing = state.getValue(FACING);
        if (shape == Shape.REGULAR || shape == Shape.PORTHOLE) {
            // Glass is solid to entities; only its rendering is transparent.
            addCollisionBoxToList(pos,query,out,turn(new AxisAlignedBB(0,0,.5,1,1,.875),facing));
        } else {
            // Nine integer-grid bands use the same bounds as the JSON model.
            for (int i=0;i<=8;++i) {
                double low=Math.max(0,2*i-1)/16.0;
                double high=Math.min(16,2*i+1)/16.0;
                double y0=shape==Shape.BOTTOM ? low : 1-high;
                double y1=shape==Shape.BOTTOM ? high : 1-low;
                double z0=i/16.0,z1=(i+6)/16.0;
                addCollisionBoxToList(pos,query,out,turn(new AxisAlignedBB(0,y0,z0,1,y1,z1),facing));
            }
        }
    }
}
