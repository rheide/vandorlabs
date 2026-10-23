package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** The same platform controller in a horizontal half-height housing. */
public final class BlockRampControllerSlab extends BlockRampController {
    public static final PropertyEnum<BlockSlab.EnumBlockHalf> HALF=CachedProperties.enumeration("half",BlockSlab.EnumBlockHalf.class);
    private static final AxisAlignedBB BOTTOM=new AxisAlignedBB(0,0,0,1,.5,1),TOP=new AxisAlignedBB(0,.5,0,1,1,1);
    public BlockRampControllerSlab(){super("programmable_ramp_slab");setDefaultState(getDefaultState().withProperty(HALF,BlockSlab.EnumBlockHalf.BOTTOM));useNeighborBrightness=true;}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE_FACE,ACTIVE,MODE,HALF);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){
        return super.getStateForPlacement(world,pos,side,x,y,z,meta,placer).withProperty(HALF,side==EnumFacing.DOWN||side!=EnumFacing.UP&&y>.5F?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
    }
    // Six scan faces and two halves fit twelve metadata values. Activity is a tile-derived actual state.
    @Override public int getMetaFromState(IBlockState state){return scanFacing(state).getIndex()|(state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?8:0);}
    @Override public IBlockState getStateFromMeta(int meta){EnumFacing scan=(meta&7)<6?EnumFacing.getFront(meta&7):EnumFacing.NORTH;return getDefaultState().withProperty(ACTIVE_FACE,scan).withProperty(FACING,scan.getAxis()==EnumFacing.Axis.Y?EnumFacing.NORTH:scan).withProperty(HALF,(meta&8)!=0?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){IBlockState actual=super.getActualState(state,world,pos);TileEntity tile=world.getTileEntity(pos);return actual.withProperty(ACTIVE,tile instanceof TileEntityRampController&&((TileEntityRampController)tile).activeForModel());}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP?TOP:BOTTOM;}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return getBoundingBox(state,world,pos);}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean isTopSolid(IBlockState state){return state.getValue(HALF)==BlockSlab.EnumBlockHalf.TOP;}
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing face){return face==EnumFacing.UP&&isTopSolid(state)||face==EnumFacing.DOWN&&!isTopSolid(state)?BlockFaceShape.SOLID:BlockFaceShape.UNDEFINED;}
    @Override public boolean isSideSolid(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing face){return getBlockFaceShape(world,state,pos,face)==BlockFaceShape.SOLID;}
}
