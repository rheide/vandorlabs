package com.vandorlabs.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Side wheels. Large wheels reserve a two-by-two vertical footprint. */
public final class BlockWheel extends BlockLandingGear {
    public static final PropertyInteger PART=PropertyInteger.create("part",0,3);
    private final int size;
    public int wheelSize(){return size;}
    public BlockWheel(String name,int size){
        super(name,false);this.size=size;
        parts.addAll(loadParts("block/"+name));
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(PART,0));
    }
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,PART);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(PART,(meta>>2)&3);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex()|(state.getValue(PART)<<2);}
    public BlockPos root(BlockPos at,IBlockState state){int part=state.getValue(PART);return at.offset(state.getValue(FACING).rotateY(),-(part&1)).down(part>>1);}
    public java.util.List<BlockPos> members(BlockPos root,IBlockState state){
        java.util.List<BlockPos> result=new java.util.ArrayList<>();
        for(int n=0;n<(size==2?4:1);n++)result.add(root.offset(state.getValue(FACING).rotateY(),n&1).up(n>>1));
        return result;
    }
    private boolean slab(IBlockState state){return state.getBlock() instanceof net.minecraft.block.BlockSlab||state.getBlock() instanceof BlockProgrammableSlab||state.getBlock() instanceof BlockProgrammableLightSlab;}
    @Override public boolean canPlaceBlockOnSide(World world,BlockPos at,EnumFacing side){
        if(!side.getAxis().isHorizontal()||!slab(world.getBlockState(at.offset(side.getOpposite()))))return false;
        for(BlockPos p:members(at,getDefaultState().withProperty(FACING,side)))if(!world.isBlockLoaded(p)||!world.getBlockState(p).getBlock().isReplaceable(world,p))return false;
        return true;
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos at,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){return getDefaultState().withProperty(FACING,side.getAxis().isHorizontal()?side:placer.getHorizontalFacing().getOpposite());}
    @Override public void onBlockPlacedBy(World world,BlockPos at,IBlockState state,EntityLivingBase placer,ItemStack stack){
        if(world.isRemote||size!=2)return;
        java.util.List<BlockPos> cells=members(at,state);
        for(int n=1;n<cells.size();n++)world.setBlockState(cells.get(n),state.withProperty(PART,n),2);
    }
    @Override protected java.util.List<AxisAlignedBB> boxes(IBlockState state,IBlockAccess world,BlockPos at){
        java.util.List<AxisAlignedBB> result=new java.util.ArrayList<>();int part=state.getValue(PART);
        for(AxisAlignedBB box:parts){AxisAlignedBB shifted=box.offset(-(part&1),-(part>>1),0);
            if(shifted.maxX>0&&shifted.minX<1&&shifted.maxY>0&&shifted.minY<1)result.add(new AxisAlignedBB(Math.max(0,shifted.minX),Math.max(0,shifted.minY),shifted.minZ,Math.min(1,shifted.maxX),Math.min(1,shifted.maxY),shifted.maxZ));}
        return result;
    }
    @Override public void breakBlock(World world,BlockPos at,IBlockState state){
        if(!world.isRemote && size==2){BlockPos root=root(at,state);
            // Clear companions directly, so breaking a helper cannot recursively drop four wheels.
            for(BlockPos p:members(root,state))if(!p.equals(at)&&world.getBlockState(p).getBlock()==this){
                world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
        }
        super.breakBlock(world,at,state);
    }
    @Override public int damageDropped(IBlockState state){return 0;}
}
