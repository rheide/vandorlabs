package com.vandorlabs.blocks;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A half-height programmable light with vanilla slab placement. */
public final class BlockProgrammableLightSlab extends BlockProgrammableLight {
    public static final PropertyEnum<BlockSlab.EnumBlockHalf> HALF =
            PropertyEnum.create("half",BlockSlab.EnumBlockHalf.class);
    public BlockProgrammableLightSlab(){
        super("programmable_light_slab");
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH)
                .withProperty(HALF,BlockSlab.EnumBlockHalf.BOTTOM));
        useNeighborBrightness=true;
    }
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,HALF);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,
            float hitX,float hitY,float hitZ,int meta,EntityLivingBase placer){
        BlockSlab.EnumBlockHalf half=side==EnumFacing.DOWN || side!=EnumFacing.UP&&hitY>.5F
                ?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM;
        return getDefaultState().withProperty(FACING,placementFacing(world,pos,side,placer))
                .withProperty(HALF,half);
    }
    @Override public IBlockState getStateFromMeta(int meta){
        int facing=meta&7;
        return getDefaultState().withProperty(FACING,facing<EnumFacing.values().length
                        ?EnumFacing.getFront(facing):EnumFacing.NORTH)
                .withProperty(HALF,(meta&8)!=0?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
    }
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getIndex()
            |(state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?8:0);}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        return state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP
                ?new AxisAlignedBB(0,.5,0,1,1,1):new AxisAlignedBB(0,0,0,1,.5,1);
    }
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean isTopSolid(IBlockState state){return state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP;}
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing face){
        return face==EnumFacing.DOWN&&state.getValue(HALF)==BlockSlab.EnumBlockHalf.BOTTOM
                ||face==EnumFacing.UP&&state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP
                ?BlockFaceShape.SOLID:BlockFaceShape.UNDEFINED;
    }
    @Override public boolean isSideSolid(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing face){
        return getBlockFaceShape(world,state,pos,face)==BlockFaceShape.SOLID;
    }
}
