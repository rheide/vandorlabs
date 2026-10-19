package com.vandorlabs.gear;

import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** One item reserves four horizontal cells. PART encodes its position in the footprint. */
public final class LargeGearBlock extends GearBlock {
    public static final PropertyInteger PART=PropertyInteger.create("part",0,3);
    private static final ThreadLocal<Boolean> REMOVING=new ThreadLocal<>();
    public LargeGearBlock(String name,Kind kind){super(name,kind);setDefaultState(getDefaultState().withProperty(PART,0));}
    @Override public int width(){return 2;}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ASSEMBLED,PART);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(PART,(meta>>2)&3);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex()|(state.getValue(PART)<<2);}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);return state.withProperty(ASSEMBLED,raw instanceof GearTileEntity&&((GearTileEntity)raw).hasLoadedController());}
    @Override public BlockPos componentRoot(BlockPos pos,IBlockState state){int part=state.getValue(PART);return pos.subtract(offset(state.getValue(FACING),part&1,part>>1));}
    @Override public List<BlockPos> componentCells(World world,BlockPos root){return cells(root,world.getBlockState(root).getValue(FACING));}
    private List<BlockPos> cells(BlockPos root,EnumFacing facing){return Arrays.asList(root,root.add(offset(facing,1,0)),root.add(offset(facing,0,1)),root.add(offset(facing,1,1)));}
    @Override public boolean componentIntact(World world,BlockPos root){if(!GearAssembly.loaded(world,root)||world.getBlockState(root).getBlock()!=this)return false;List<BlockPos> cells=componentCells(world,root);for(int n=0;n<4;n++){BlockPos p=cells.get(n);if(!GearAssembly.loaded(world,p))return false;IBlockState s=world.getBlockState(p);if(s.getBlock()!=this||s.getValue(PART)!=n||s.getValue(FACING)!=world.getBlockState(root).getValue(FACING))return false;}return true;}
    @Override public boolean canPlaceBlockAt(World world,BlockPos pos){return GearAssembly.loaded(world,pos)&&world.getBlockState(pos).getBlock().isReplaceable(world,pos);}
    @Override public boolean canPlaceBlockOnSide(World world,BlockPos pos,EnumFacing side){return canPlaceBlockAt(world,pos);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){if(!world.isRemote){for(BlockPos p:componentCells(world,pos))if(!p.equals(pos)&&(!GearAssembly.loaded(world,p)||!world.getBlockState(p).getBlock().isReplaceable(world,p))){world.destroyBlock(pos,true);return;}List<BlockPos> cells=componentCells(world,pos);for(int n=1;n<4;n++)world.setBlockState(cells.get(n),state.withProperty(PART,n),2);}super.onBlockPlacedBy(world,pos,state,placer,stack);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){super.breakBlock(world,pos,state);if(world.isRemote||Boolean.TRUE.equals(REMOVING.get()))return;REMOVING.set(true);try{BlockPos root=componentRoot(pos,state);for(BlockPos p:cells(root,state.getValue(FACING)))if(!p.equals(pos)&&world.getBlockState(p).getBlock()==this&&componentRoot(p,world.getBlockState(p)).equals(root))world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);}finally{REMOVING.remove();}}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){if(kind()!=Kind.WHEEL)return FULL_BLOCK_AABB;return new AxisAlignedBB(0,-1,0,1,14/16D,1);}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return kind()==Kind.WHEEL?null:super.getCollisionBoundingBox(state,world,pos);}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes,Entity entity,boolean actual){
        // The global geometry provider includes the loose wheel below its source cells.
        if(kind()!=Kind.WHEEL)super.addCollisionBoxToList(state,world,pos,query,boxes,entity,actual);
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        if(kind()!=Kind.WHEEL)return super.collisionRayTrace(state,world,pos,start,end);
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(componentRoot(pos,state));
        if(!(tile instanceof GearTileEntity))return null;
        GearTileEntity gear=(GearTileEntity)tile;
        if(gear.hasLoadedController())return super.collisionRayTrace(state,world,pos,start,end);
        RayTraceResult nearest=null;
        for(AxisAlignedBB box:GearGeometry.looseWheelBoxes(gear)){
            RayTraceResult hit=box.calculateIntercept(start,end);
            if(hit!=null && (nearest==null || start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(nearest.hitVec)))nearest=new RayTraceResult(hit.hitVec,hit.sideHit,gear.getPos());
        }
        return nearest;
    }
    public static BlockPos offset(EnumFacing facing,int x,int z){switch(facing){case EAST:return new BlockPos(-z,0,x);case SOUTH:return new BlockPos(-x,0,-z);case WEST:return new BlockPos(z,0,-x);default:return new BlockPos(x,0,z);}}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return super.withRotation(state,rotation);}
}
