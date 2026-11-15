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
    private static final AxisAlignedBB[][][][][] BOXES=new AxisAlignedBB[4][2][2][2][];
    private static final AxisAlignedBB[][][][] BOUNDS=new AxisAlignedBB[4][2][2][2];
    static{for(EnumFacing facing:EnumFacing.HORIZONTALS)for(EnumFacing up:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP})for(int locked=0;locked<2;locked++)for(int half=0;half<2;half++){
        AxisAlignedBB[] original=CouplerShape.getBoxes(facing,up,locked!=0),boxes=new AxisAlignedBB[original.length];AxisAlignedBB bounds=null;
        for(int i=0;i<original.length;i++){AxisAlignedBB b=original[i],box=new AxisAlignedBB(b.minX,b.minY*.5+half*.5,b.minZ,b.maxX,b.maxY*.5+half*.5,b.maxZ);boxes[i]=box;bounds=bounds==null?box:bounds.union(box);}
        BOXES[facing.getHorizontalIndex()][up.getIndex()][locked][half]=boxes;BOUNDS[facing.getHorizontalIndex()][up.getIndex()][locked][half]=bounds;
    }}
    public BlockCouplerSlab(){super("vehicle_coupler_slab");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(LOCKED,false).withProperty(HALF,BlockSlab.EnumBlockHalf.BOTTOM));}
    @Override protected BlockStateContainer createBlockState(){return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty[]{FACING,LOCKED,HALF},new net.minecraftforge.common.property.IUnlistedProperty[]{FRAME_UP});}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(HALF,(meta&4)!=0?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM).withProperty(LOCKED,(meta&8)!=0);}
    @Override public int getMetaFromState(IBlockState state){return (state.getValue(FACING).getAxis().isHorizontal()?state.getValue(FACING).getHorizontalIndex():EnumFacing.NORTH.getHorizontalIndex())|(state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?4:0)|(state.getValue(LOCKED)?8:0);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase actor){EnumFacing facing=side.getAxis().isHorizontal()?side:actor.getHorizontalFacing().getOpposite();return getDefaultState().withProperty(FACING,facing).withProperty(HALF,side==EnumFacing.DOWN||side!=EnumFacing.UP&&y>.5?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);}
    private static EnumFacing horizontal(IBlockState state){EnumFacing facing=state.getValue(FACING);return facing.getAxis().isHorizontal()?facing:EnumFacing.NORTH;}
    @Override protected AxisAlignedBB[] collisionBoxes(IBlockState state,EnumFacing up){return BOXES[horizontal(state).getHorizontalIndex()][up.getAxis()==EnumFacing.Axis.Y?up.getIndex():EnumFacing.UP.getIndex()][state.getValue(LOCKED)?1:0][state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?1:0];}
    @Override protected AxisAlignedBB bounds(IBlockState state,EnumFacing up){return BOUNDS[horizontal(state).getHorizontalIndex()][up.getAxis()==EnumFacing.Axis.Y?up.getIndex():EnumFacing.UP.getIndex()][state.getValue(LOCKED)?1:0][state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?1:0];}
    @Override public boolean isTopSolid(IBlockState state){return false;}
}
