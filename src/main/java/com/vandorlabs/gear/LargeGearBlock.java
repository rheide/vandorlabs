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

/** Large arms occupy one cell; the oversized wheel retains its four source cells. */
public final class LargeGearBlock extends GearBlock {
    public static final PropertyInteger PART=PropertyInteger.create("part",0,3);
    private static final ThreadLocal<Boolean> REMOVING=new ThreadLocal<>();
    /** A validated durable transfer owns every helper and removes them explicitly. */
    public static ComponentRemoval withoutComponentBreak(){return new ComponentRemoval();}
    public static final class ComponentRemoval implements AutoCloseable {
        private final Boolean previous=REMOVING.get();private boolean closed;
        private ComponentRemoval(){REMOVING.set(true);}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)REMOVING.remove();else REMOVING.set(previous);}
    }
    public LargeGearBlock(String name,Kind kind){super(name,kind);setDefaultState(getDefaultState().withProperty(PART,0));}
    @Override public int width(){return kind()==Kind.WHEEL?2:1;}
    @Override public int sizeClass(){return 2;}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ASSEMBLED,PART);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(PART,(meta>>2)&3);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex()|(state.getValue(PART)<<2);}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);return state.withProperty(ASSEMBLED,raw instanceof GearTileEntity&&(((GearTileEntity)raw).hasLoadedController()||kind()==Kind.WHEEL&&((GearTileEntity)raw).componentFrame()!=EnumFacing.DOWN));}
    @Override public BlockPos componentRoot(BlockPos pos,IBlockState state){int part=state.getValue(PART);return pos.subtract(offset(state.getValue(FACING),part&1,part>>1));}
    @Override public BlockPos componentRoot(IBlockAccess world,BlockPos pos,IBlockState state){int part=state.getValue(PART);return pos.subtract(ArmFootComponentFrame.offset(ArmFootComponentFrame.frame(world,pos),state.getValue(FACING),part&1,part>>1));}
    @Override public List<BlockPos> componentCells(World world,BlockPos root){return width()==1?Collections.singletonList(root):cells(root,world.getBlockState(root).getValue(FACING),ArmFootComponentFrame.frame(world,root));}
    private List<BlockPos> cells(BlockPos root,EnumFacing facing){return Arrays.asList(root,root.add(offset(facing,1,0)),root.add(offset(facing,0,1)),root.add(offset(facing,1,1)));}
    private List<BlockPos> cells(BlockPos root,EnumFacing facing,EnumFacing frame){return Arrays.asList(root,root.add(ArmFootComponentFrame.offset(frame,facing,1,0)),root.add(ArmFootComponentFrame.offset(frame,facing,0,1)),root.add(ArmFootComponentFrame.offset(frame,facing,1,1)));}
    @Override public boolean componentIntact(World world,BlockPos root){if(!GearAssembly.loaded(world,root)||world.getBlockState(root).getBlock()!=this)return false;List<BlockPos> cells=componentCells(world,root);for(int n=0;n<cells.size();n++){BlockPos p=cells.get(n);if(!GearAssembly.loaded(world,p))return false;IBlockState s=world.getBlockState(p);if(s.getBlock()!=this||s.getValue(PART)!=n||s.getValue(FACING)!=world.getBlockState(root).getValue(FACING)||width()==2&&ArmFootComponentFrame.frame(world,p)!=ArmFootComponentFrame.frame(world,root))return false;}return true;}
    @Override public boolean canPlaceBlockAt(World world,BlockPos pos){return GearAssembly.loaded(world,pos)&&world.getBlockState(pos).getBlock().isReplaceable(world,pos);}
    @Override public boolean canPlaceBlockOnSide(World world,BlockPos pos,EnumFacing side){return canPlaceBlockAt(world,pos);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){
        if(!world.isRemote){
            EnumFacing frame=kind()==Kind.WHEEL?ArmFootComponentFrame.placement():EnumFacing.DOWN;
            List<BlockPos> members=width()==1?Collections.singletonList(pos):cells(pos,state.getValue(FACING),frame);
            for(BlockPos p:members)if(!p.equals(pos)&&(!GearAssembly.loaded(world,p)||!world.getBlockState(p).getBlock().isReplaceable(world,p))){throw new IllegalArgumentException("The Large Wheel footprint changed during placement");}
            for(int n=1;n<members.size();n++)if(!world.setBlockState(members.get(n),state.withProperty(PART,n),2))throw new IllegalArgumentException("The Large Wheel footprint could not be placed");
            if(ArmFootComponentFrame.posedPlacement())for(BlockPos member:members){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(member);if(!(tile instanceof GearTileEntity))throw new IllegalArgumentException("The Large Wheel tile is unavailable");((GearTileEntity)tile).componentFrame(frame);((GearTileEntity)tile).setMountDirection(frame);}
        }
        super.onBlockPlacedBy(world,pos,state,placer,stack);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        EnumFacing frame=ArmFootComponentFrame.frame(world,pos);BlockPos root=pos.subtract(ArmFootComponentFrame.offset(frame,state.getValue(FACING),state.getValue(PART)&1,state.getValue(PART)>>1));
        List<BlockPos> members=cells(root,state.getValue(FACING),frame);
        super.breakBlock(world,pos,state);if(world.isRemote||width()==1||Boolean.TRUE.equals(REMOVING.get()))return;
        REMOVING.set(true);try{for(BlockPos p:members)if(!p.equals(pos)&&GearAssembly.loaded(world,p)&&world.getBlockState(p).getBlock()==this&&componentRoot(world,p,world.getBlockState(p)).equals(root))world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);}finally{REMOVING.remove();}
    }
    /** Roll back an interrupted ordinary item placement before its held item is spent. */
    static boolean restorePlacement(LargeGearBlock block,World world,java.util.List<net.minecraftforge.common.util.BlockSnapshot> snapshots){
        boolean restored=true;Boolean previous=REMOVING.get();REMOVING.set(true);
        try{for(int n=snapshots.size()-1;n>=0;n--){net.minecraftforge.common.util.BlockSnapshot snapshot=snapshots.get(n);IBlockState current=world.getBlockState(snapshot.getPos());if(current.getBlock()!=snapshot.getReplacedBlock().getBlock()&&current.getBlock()!=block){restored=false;continue;}restored&=snapshot.restore(true,false);}}
        finally{if(previous==null)REMOVING.remove();else REMOVING.set(previous);}
        return restored;
    }
    public boolean migrateCompact(World world,BlockPos pos,IBlockState state){
        if(width()!=1)return true;BlockPos root=componentRoot(pos,state);List<BlockPos> old=cells(root,state.getValue(FACING));
        for(BlockPos cell:old)if(!GearAssembly.loaded(world,cell))return false;
        for(BlockPos cell:old){IBlockState candidate=world.getBlockState(cell);if(!cell.equals(root)&&candidate.getBlock()==this&&candidate.getValue(PART)>0&&componentRoot(cell,candidate).equals(root)){
            // Retiring a source helper must not dismantle or deploy its surviving controller.
            world.removeTileEntity(cell);world.setBlockToAir(cell);
        }}
        return true;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){if(kind()!=Kind.WHEEL)return FULL_BLOCK_AABB;return ArmOrientation.box(ArmFootComponentFrame.frame(world,pos),new AxisAlignedBB(0,-1,0,1,14/16D,1));}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return kind()==Kind.WHEEL?null:super.getCollisionBoundingBox(state,world,pos);}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes,Entity entity,boolean actual){
        // The global geometry provider includes the loose wheel below its source cells.
        if(kind()!=Kind.WHEEL)super.addCollisionBoxToList(state,world,pos,query,boxes,entity,actual);
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        if(kind()!=Kind.WHEEL)return super.collisionRayTrace(state,world,pos,start,end);
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(componentRoot(world,pos,state));
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
