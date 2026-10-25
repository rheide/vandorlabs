package com.vandorlabs.coupler;

import com.vandorlabs.blocks.BlockVandor;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Six-axis authored coupling head with collision at its selected endpoint. */
public final class BlockCoupler extends BlockVandor {
    public static final PropertyDirection FACING=PropertyDirection.create("facing");
    public static final PropertyBool LOCKED=PropertyBool.create("locked");
    public BlockCoupler(){super("square_coupler");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(LOCKED,false));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,LOCKED);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getFront(meta&7)).withProperty(LOCKED,(meta&8)!=0);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getIndex()|(state.getValue(LOCKED)?8:0);}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,net.minecraft.entity.EntityLivingBase actor){return getDefaultState().withProperty(FACING,side);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new TileCoupler();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing side){return BlockFaceShape.UNDEFINED;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return CouplerShape.getBounds(state.getValue(FACING),state.getValue(LOCKED));}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,java.util.List<AxisAlignedBB> out,net.minecraft.entity.Entity entity,boolean actual){for(AxisAlignedBB box:CouplerShape.getBoxes(state.getValue(FACING),state.getValue(LOCKED)))addCollisionBoxToList(pos,query,out,box);}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileCoupler)((TileCoupler)tile).sample();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,net.minecraft.entity.player.EntityPlayer actor,EnumHand hand,EnumFacing side,float x,float y,float z){if(hand!=EnumHand.MAIN_HAND)return false;if(!world.isRemote&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(actor,world,pos))actor.openGui(com.vandorlabs.VandorLabs.instance,25,world,pos.getX(),pos.getY(),pos.getZ());return true;}
    /** The pair is a boundary if either independent half is released. */
    public static boolean separates(IBlockState a,IBlockState b){return a.getBlock() instanceof BlockCoupler&&b.getBlock() instanceof BlockCoupler&&(!a.getValue(LOCKED)||!b.getValue(LOCKED));}
}
