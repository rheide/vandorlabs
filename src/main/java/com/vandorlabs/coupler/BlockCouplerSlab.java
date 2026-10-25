package com.vandorlabs.coupler;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Horizontal coupling head, fitted into the clicked lower or upper half of its cell. */
public final class BlockCouplerSlab extends BlockCoupler {
    public static final PropertyEnum<BlockSlab.EnumBlockHalf> HALF=PropertyEnum.create("half",BlockSlab.EnumBlockHalf.class);
    private static final AxisAlignedBB[][][][] BOXES=new AxisAlignedBB[4][2][2][];
    static{for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int locked=0;locked<2;locked++){
        AxisAlignedBB[] original=CouplerShape.getBoxes(facing,locked!=0);for(int half=0;half<2;half++){AxisAlignedBB[] boxes=new AxisAlignedBB[original.length];for(int i=0;i<original.length;i++){AxisAlignedBB b=original[i];boxes[i]=new AxisAlignedBB(b.minX,b.minY*.5+half*.5,b.minZ,b.maxX,b.maxY*.5+half*.5,b.maxZ);}BOXES[facing.getHorizontalIndex()][locked][half]=boxes;}

    }}
    public BlockCouplerSlab(){super("vehicle_coupler_slab");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(LOCKED,false).withProperty(HALF,BlockSlab.EnumBlockHalf.BOTTOM));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,LOCKED,HALF);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(HALF,(meta&4)!=0?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM).withProperty(LOCKED,(meta&8)!=0);}
    @Override public int getMetaFromState(IBlockState state){return (state.getValue(FACING).getAxis().isHorizontal()?state.getValue(FACING).getHorizontalIndex():EnumFacing.NORTH.getHorizontalIndex())|(state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?4:0)|(state.getValue(LOCKED)?8:0);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase actor){EnumFacing facing=side.getAxis().isHorizontal()?side:actor.getHorizontalFacing().getOpposite();return getDefaultState().withProperty(FACING,facing).withProperty(HALF,side==EnumFacing.DOWN||side!=EnumFacing.UP&&y>.5?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);}
    @Override protected AxisAlignedBB[] collisionBoxes(IBlockState state){EnumFacing facing=state.getValue(FACING);if(!facing.getAxis().isHorizontal())facing=EnumFacing.NORTH;return BOXES[facing.getHorizontalIndex()][state.getValue(LOCKED)?1:0][state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?1:0];}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){AxisAlignedBB[] boxes=collisionBoxes(state);AxisAlignedBB bounds=boxes[0];for(int i=1;i<boxes.length;i++)bounds=bounds.union(boxes[i]);return bounds;}
    @Override public boolean isTopSolid(IBlockState state){return false;}
}
