package com.vandorlabs.coupler;

import com.vandorlabs.blocks.BlockVandor;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Six-axis authored coupling head with collision at its selected endpoint. */
public class BlockCoupler extends BlockVandor {
    public static final PropertyDirection FACING=PropertyDirection.create("facing");
    public static final PropertyBool LOCKED=PropertyBool.create("locked");
    public BlockCoupler(){this("square_coupler");}
    protected BlockCoupler(String id){super(id);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(LOCKED,false));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,LOCKED);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getFront(meta&7)).withProperty(LOCKED,(meta&8)!=0);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getIndex()|(state.getValue(LOCKED)?8:0);}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,net.minecraft.entity.EntityLivingBase actor){return getDefaultState().withProperty(FACING,side);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack){super.onBlockPlacedBy(world,pos,state,placer,stack);net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileCoupler&&placer instanceof net.minecraft.entity.player.EntityPlayer)((TileCoupler)raw).authorize((net.minecraft.entity.player.EntityPlayer)placer);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new TileCoupler();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing side){return BlockFaceShape.UNDEFINED;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return CouplerShape.getBounds(state.getValue(FACING),state.getValue(LOCKED));}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,java.util.List<AxisAlignedBB> out,net.minecraft.entity.Entity entity,boolean actual){for(AxisAlignedBB box:collisionBoxes(state))addCollisionBoxToList(pos,query,out,box);}
    protected AxisAlignedBB[] collisionBoxes(IBlockState state){return CouplerShape.getBoxes(state.getValue(FACING),state.getValue(LOCKED));}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){RayTraceResult closest=null;double distance=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:collisionBoxes(state)){RayTraceResult hit=box.offset(pos).calculateIntercept(start,end);if(hit!=null&&start.squareDistanceTo(hit.hitVec)<distance){distance=start.squareDistanceTo(hit.hitVec);closest=new RayTraceResult(hit.hitVec,hit.sideHit,pos);}}return closest;}
    public static double couplingHeight(IBlockState state){return state.getBlock() instanceof BlockCouplerSlab?(state.getValue(BlockCouplerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP?.75:.25):.5;}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileCoupler)((TileCoupler)tile).sample();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,net.minecraft.entity.player.EntityPlayer actor,EnumHand hand,EnumFacing side,float x,float y,float z){if(hand!=EnumHand.MAIN_HAND)return false;ContainerCoupler.open(actor,world,pos);return true;}
    /** The pair is a boundary if either independent half is released. */
    public static boolean separates(IBlockState a,IBlockState b){return a.getBlock() instanceof BlockCoupler&&b.getBlock() instanceof BlockCoupler&&(!a.getValue(LOCKED)||!b.getValue(LOCKED));}
}
