package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import com.vandorlabs.persistence.SpaceDoorData;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** An indivisible 3x3 opening; each moving leaf is 1.5 x 3 blocks. */
public class BlockLargeProgrammableDoor extends BlockConfigurableSpaceDoor {
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
    public BlockLargeProgrammableDoor(String name,BlockDetailedDoor paired){super(name,false,paired);}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityLargeProgrammableDoor();}
    public TileEntityLargeProgrammableDoor root(IBlockAccess world,BlockPos pos){
        if(world instanceof World && !((World)world).isBlockLoaded(pos))return null;
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityLargeProgrammableDoor))return null;
        BlockPos anchor=((TileEntityLargeProgrammableDoor)raw).anchorPos();
        if(world instanceof World && !((World)world).isBlockLoaded(anchor))return null;
        raw=world.getTileEntity(anchor);
        return raw instanceof TileEntityLargeProgrammableDoor && ((TileEntityLargeProgrammableDoor)raw).isAnchor() && world.getBlockState(anchor).getBlock()==this?(TileEntityLargeProgrammableDoor)raw:null;
    }
    @Override protected boolean canPairWith(BlockVandorDoor other){return false;}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(OPEN,(meta&4)!=0).withProperty(POWERED,(meta&8)!=0);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex()|(state.getValue(OPEN)?4:0)|(state.getValue(POWERED)?8:0);}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntityLargeProgrammableDoor tile=root(world,pos);IBlockState actual=tile==null?state:world.getBlockState(tile.getPos());
        return actual.withProperty(PAIRED,true).withProperty(HINGE,BlockDoor.EnumHingePosition.RIGHT).withProperty(HALF,tile!=null && pos.equals(tile.getPos())?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER);
    }
    @Override public boolean canPlaceBlockAt(World world,BlockPos pos){return pos.getY()>0 && pos.getY()+2<world.getHeight();}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase entity,ItemStack stack){}
    public boolean complete(World world,TileEntityLargeProgrammableDoor tile){
        EnumFacing width=world.getBlockState(tile.getPos()).getValue(FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=tile.getPos().offset(width,x).up(y);if(!world.isBlockLoaded(cell))return false;
            TileEntity raw=world.getTileEntity(cell);
            if(world.getBlockState(cell).getBlock()!=this || !(raw instanceof TileEntityLargeProgrammableDoor) || !((TileEntityLargeProgrammableDoor)raw).anchorPos().equals(tile.getPos()))return false;
        }
        return true;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND)return false;
        TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null)return true;
        boolean panelHit=false;
        if(tile.hasPanel()){
            EnumFacing front=world.getBlockState(tile.getPos()).getValue(FACING);
            EnumFacing panelFace=tile.isSliding()?front.rotateY():front.rotateYCCW();
            double normal=front==EnumFacing.SOUTH?pos.getZ()+z-tile.getPos().getZ():front==EnumFacing.NORTH?1-(pos.getZ()+z-tile.getPos().getZ()):front==EnumFacing.EAST?pos.getX()+x-tile.getPos().getX():1-(pos.getX()+x-tile.getPos().getX());
            panelHit=face==panelFace && com.vandorlabs.render.SpaceDoorControlPanel.contains((normal-tile.positionOffset())*16,(pos.getY()+y-tile.getPos().getY())*16/1.5,tile.isSliding(),tile.getPlacementDepth()==2);
        }
        if(player.isSneaking() || player.capabilities.isCreativeMode && panelHit){
            if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,player.capabilities.isCreativeMode?com.vandorlabs.GuiHandler.GUI_SPACE_DOOR:com.vandorlabs.GuiHandler.GUI_REDSTONE_CHANNEL,world,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ());
        } else if(tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED && !world.isRemote)
            setPowered(world,tile.getPos(),state,world.getBlockState(tile.getPos()).getValue(POWERED),!world.getBlockState(tile.getPos()).getValue(OPEN));
        return true;
    }
    @Override public void updateRedstoneState(World world,BlockPos pos,IBlockState state){
        if(world.isRemote)return;TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null)return;
        IBlockState actual=world.getBlockState(tile.getPos());boolean powered=tile.hasLocalRedstoneSignal()||tile.isChannelSignalPowered();
        boolean open=tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED?actual.getValue(OPEN):SpaceDoorData.openForSignal(tile.getTrigger(),powered);
        setPowered(world,tile.getPos(),actual,powered,open);
    }
    @Override protected void setPowered(World world,BlockPos pos,IBlockState state,boolean powered,boolean open){
        TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null || !complete(world,tile))return;
        BlockPos anchor=tile.getPos();IBlockState old=world.getBlockState(anchor);EnumFacing width=old.getValue(FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=anchor.offset(width,x).up(y);IBlockState before=world.getBlockState(cell);
            if(before.getValue(OPEN)!=open || before.getValue(POWERED)!=powered)world.setBlockState(cell,before.withProperty(OPEN,open).withProperty(POWERED,powered),2);
        }
        if(old.getValue(OPEN)!=open)world.playSound(null,anchor,open?net.minecraft.init.SoundEvents.BLOCK_IRON_DOOR_OPEN:net.minecraft.init.SoundEvents.BLOCK_IRON_DOOR_CLOSE,SoundCategory.BLOCKS,1,1);
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){
        if(world.isRemote || REMOVING.get())return;
        TileEntityLargeProgrammableDoor tile=root(world,pos);
        if(tile==null){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityLargeProgrammableDoor && world.isBlockLoaded(((TileEntityLargeProgrammableDoor)raw).anchorPos()))world.setBlockToAir(pos);return;}
        EnumFacing width=state.getValue(FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=-1;y<3;y++)if(!world.isBlockLoaded(tile.getPos().offset(width,x).up(y)))return;
        if(!complete(world,tile)){world.destroyBlock(tile.getPos(),true);return;}
        for(int x=0;x<3;x++)if(!world.getBlockState(tile.getPos().offset(width,x).down()).isSideSolid(world,tile.getPos().offset(width,x).down(),EnumFacing.UP)){world.destroyBlock(tile.getPos(),true);return;}
        tile.localInputChanged();updateRedstoneState(world,tile.getPos(),state);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        if(!REMOVING.get()){
            TileEntity raw=world.getTileEntity(pos);BlockPos anchor=raw instanceof TileEntityLargeProgrammableDoor?((TileEntityLargeProgrammableDoor)raw).anchorPos():pos;
            EnumFacing width=state.getValue(FACING).rotateYCCW();REMOVING.set(true);
            try{for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                BlockPos cell=anchor.offset(width,x).up(y);if(cell.equals(pos) || !world.isBlockLoaded(cell))continue;
                TileEntity other=world.getTileEntity(cell);
                if(world.getBlockState(cell).getBlock()==this && other instanceof TileEntityLargeProgrammableDoor && ((TileEntityLargeProgrammableDoor)other).anchorPos().equals(anchor))world.setBlockToAir(cell);
            }}finally{REMOVING.remove();}
        }
        world.removeTileEntity(pos);
    }
    @Override public Item getItemDropped(IBlockState state,Random rand,int fortune){return Item.getItemFromBlock(this);}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult ray,World world,BlockPos pos,EntityPlayer player){ItemStack stack=new ItemStack(this);TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile!=null)stack.setTagInfo("SpaceDoorSettings",tile.itemSettings());return stack;}
    // Geometry is shared by collision and precise selection. Slid leaves remain physically
    // present outside the opening; only their portions intersecting this cell collide.
    public List<AxisAlignedBB> geometry(IBlockAccess world,BlockPos pos){
        TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null)return Collections.emptyList();
        IBlockState state=world.getBlockState(tile.getPos());EnumFacing face=state.getValue(FACING);
        List<AxisAlignedBB> result=new ArrayList<>();
        for(com.vandorlabs.render.LargeDoorGeometry.Box b:com.vandorlabs.render.LargeDoorGeometry.boxes(tile.isFramed(),tile.isSliding(),tile.getSlideDirection(),state.getValue(OPEN),tile.positionOffset(),face.getHorizontalIndex(),tile.hasPanel(),tile.getPlacementDepth()==2))
            result.add(new AxisAlignedBB(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1).offset(tile.getPos()));
        return result;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){List<AxisAlignedBB> boxes=geometry(world,pos);AxisAlignedBB cell=new AxisAlignedBB(pos),union=null;for(AxisAlignedBB box:boxes){if(!box.intersects(cell))continue;AxisAlignedBB clipped=box.intersect(cell);union=union==null?clipped:union.union(clipped);}return union==null?FULL_BLOCK_AABB:union.offset(-pos.getX(),-pos.getY(),-pos.getZ());}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return NULL_AABB;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB entityBox,List<AxisAlignedBB> boxes,Entity entity,boolean actual){TileEntityLargeProgrammableDoor root=root(world,pos);if(root==null)return;
        EnumFacing width=world.getBlockState(root.getPos()).getValue(FACING).rotateYCCW();
        int column=(pos.getX()-root.getPos().getX())*width.getFrontOffsetX()+(pos.getZ()-root.getPos().getZ())*width.getFrontOffsetZ();
        int row=pos.getY()-root.getPos().getY();
        double lo=column==0?Double.NEGATIVE_INFINITY:0,hi=column==2?Double.POSITIVE_INFINITY:1;
        double minX=Double.NEGATIVE_INFINITY,maxX=Double.POSITIVE_INFINITY,minZ=Double.NEGATIVE_INFINITY,maxZ=Double.POSITIVE_INFINITY;
        if(width.getAxis()==EnumFacing.Axis.X){minX=pos.getX()+(width.getFrontOffsetX()>0?lo:1-hi);maxX=pos.getX()+(width.getFrontOffsetX()>0?hi:1-lo);}
        else{minZ=pos.getZ()+(width.getFrontOffsetZ()>0?lo:1-hi);maxZ=pos.getZ()+(width.getFrontOffsetZ()>0?hi:1-lo);}
        AxisAlignedBB cell=new AxisAlignedBB(minX,row==0?Double.NEGATIVE_INFINITY:pos.getY(),minZ,maxX,row==2?Double.POSITIVE_INFINITY:pos.getY()+1,maxZ);
        // AxisAlignedBB normalizes reversed endpoints. Intersecting disjoint boxes
        // therefore creates a phantom solid instead of an empty intersection.
        for(AxisAlignedBB b:geometry(world,pos)){if(!b.intersects(cell))continue;AxisAlignedBB clipped=b.intersect(cell);if(clipped.intersects(entityBox))boxes.add(clipped);}}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){RayTraceResult nearest=null;double distance=Double.MAX_VALUE;for(AxisAlignedBB box:geometry(world,pos)){RayTraceResult hit=box.calculateIntercept(start,end);if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance){distance=start.squareDistanceTo(hit.hitVec);nearest=new RayTraceResult(hit.hitVec,hit.sideHit,pos);}}return nearest;}
}
