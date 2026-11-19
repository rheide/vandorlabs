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
    public static boolean isOpenHorizontal(World world,BlockPos pos,IBlockState state){
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor)||!state.getValue(OPEN))return false;
        TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)state.getBlock()).root(world,pos);
        return root!=null&&root.isHorizontal()&&world.getBlockState(root.getPos()).getValue(OPEN);
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
        EnumFacing front=world.getBlockState(tile.getPos()).getValue(FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=tile.cell(tile.getPos(),front,x,y);if(!world.isBlockLoaded(cell))return false;
            TileEntity raw=world.getTileEntity(cell);
            if(world.getBlockState(cell).getBlock()!=this || !(raw instanceof TileEntityLargeProgrammableDoor) || !((TileEntityLargeProgrammableDoor)raw).anchorPos().equals(tile.getPos()))return false;
        }
        return true;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){
        TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null)return true;
        boolean panelHit=tile.readerHit(new Vec3d(pos.getX()+x,pos.getY()+y,pos.getZ()+z),face);
        if(tile.securityMode()>0&&!player.isSneaking()&&player.getHeldItem(hand).getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER){if(panelHit||tile.holdingCredential(player,hand))return tile.useSecurity(player,hand,tile.readerIndex(new Vec3d(pos.getX()+x,pos.getY()+y,pos.getZ()+z)));return true;}
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.isSneaking() || player.capabilities.isCreativeMode && panelHit){
            if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,player.capabilities.isCreativeMode?com.vandorlabs.GuiHandler.GUI_SPACE_DOOR:com.vandorlabs.GuiHandler.GUI_REDSTONE_CHANNEL,world,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ());
        } else if(tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED && !world.isRemote)
            setPowered(world,tile.getPos(),state,world.getBlockState(tile.getPos()).getValue(POWERED),!world.getBlockState(tile.getPos()).getValue(OPEN));
        return true;
    }
    @Override public void updateRedstoneState(World world,BlockPos pos,IBlockState state){
        if(world.isRemote)return;TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null)return;
        IBlockState actual=world.getBlockState(tile.getPos());boolean powered=tile.hasLocalRedstoneSignal()||tile.isChannelSignalPowered();
        boolean open=tile.securityMode()>0?tile.securityOpen():tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED?actual.getValue(OPEN):SpaceDoorData.openForSignal(tile.getTrigger(),powered);
        setPowered(world,tile.getPos(),actual,powered,open);
    }
    @Override public void toggleDoor(World world,BlockPos pos,boolean open){
        if(world.isRemote)return;
        TileEntityLargeProgrammableDoor tile=root(world,pos);
        if(tile!=null)super.toggleDoor(world,tile.getPos(),open);
    }
    @Override protected void setPowered(World world,BlockPos pos,IBlockState state,boolean powered,boolean open){
        TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null || !complete(world,tile))return;
        BlockPos anchor=tile.getPos();IBlockState old=world.getBlockState(anchor);EnumFacing front=old.getValue(FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=tile.cell(anchor,front,x,y);IBlockState before=world.getBlockState(cell);
            if(before.getValue(OPEN)!=open || before.getValue(POWERED)!=powered)world.setBlockState(cell,before.withProperty(OPEN,open).withProperty(POWERED,powered),2);
            if(tile.isHorizontal()&&before.getValue(OPEN)!=open && world.getTileEntity(cell) instanceof TileEntityLargeProgrammableDoor){
                TileEntityLargeProgrammableDoor part=(TileEntityLargeProgrammableDoor)world.getTileEntity(cell);
                if(part.fluids.any()||part.waterOutlets()!=0)DoorWaterGush.schedule(world,cell);
            }
        }
        if(old.getValue(OPEN)!=open)world.playSound(null,anchor,open?net.minecraft.init.SoundEvents.BLOCK_IRON_DOOR_OPEN:net.minecraft.init.SoundEvents.BLOCK_IRON_DOOR_CLOSE,SoundCategory.BLOCKS,1,1);
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){
        if(world.isRemote || REMOVING.get())return;
        TileEntityLargeProgrammableDoor tile=root(world,pos);
        if(tile==null){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityLargeProgrammableDoor && world.isBlockLoaded(((TileEntityLargeProgrammableDoor)raw).anchorPos()))world.setBlockToAir(pos);return;}
        EnumFacing front=world.getBlockState(tile.getPos()).getValue(FACING);
        for(int x=0;x<3;x++)for(int y=-1;y<3;y++)if(!world.isBlockLoaded(tile.cell(tile.getPos(),front,x,y)))return;
        if(!complete(world,tile)){world.destroyBlock(tile.getPos(),true);return;}
        for(int x=0;x<3;x++){
            BlockPos support=tile.cell(tile.getPos(),front,x,-1);
            if(!world.getBlockState(support).isSideSolid(world,support,tile.rowDirection(front))){world.destroyBlock(tile.getPos(),true);return;}
        }
        tile.localInputChanged();updateRedstoneState(world,tile.getPos(),state);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        if(!world.isRemote&&world.getTileEntity(pos) instanceof TileEntityLargeProgrammableDoor
                &&((TileEntityLargeProgrammableDoor)world.getTileEntity(pos)).isHorizontal())DoorWaterGush.stop(world,pos);
        if(!REMOVING.get()){
            TileEntityLargeProgrammableDoor reader=root(world,pos);if(!world.isRemote&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)&&reader!=null){for(int side=0;side<2;side++){ItemStack token=reader.keyReader(side).takeForDrop();if(!token.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,token);}}
            TileEntity raw=world.getTileEntity(pos);BlockPos anchor=raw instanceof TileEntityLargeProgrammableDoor?((TileEntityLargeProgrammableDoor)raw).anchorPos():pos;
            EnumFacing front=state.getValue(FACING);REMOVING.set(true);
            try{for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                BlockPos cell=raw instanceof TileEntityLargeProgrammableDoor?((TileEntityLargeProgrammableDoor)raw).cell(anchor,front,x,y):anchor.offset(front.rotateYCCW(),x).up(y);if(cell.equals(pos) || !world.isBlockLoaded(cell))continue;
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
        return tile.collisionGeometry(face,state.getValue(OPEN));
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){List<AxisAlignedBB> boxes=geometry(world,pos);AxisAlignedBB cell=new AxisAlignedBB(pos),union=null;for(AxisAlignedBB box:boxes){if(!box.intersects(cell))continue;AxisAlignedBB clipped=box.intersect(cell);union=union==null?clipped:union.union(clipped);}return union==null?FULL_BLOCK_AABB:union.offset(-pos.getX(),-pos.getY(),-pos.getZ());}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return NULL_AABB;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB entityBox,List<AxisAlignedBB> boxes,Entity entity,boolean actual){TileEntityLargeProgrammableDoor root=root(world,pos);if(root==null)return;
        // geometry is a public extension point. An unknown block subclass may
        // supply different or mutable shapes, so retain its original dispatch.
        if(getClass()!=BlockLargeProgrammableDoor.class){addUncachedCollisionBoxes(world,pos,root,entityBox,boxes);return;}
        IBlockState rootState=world.getBlockState(root.getPos());
        EnumFacing facing=rootState.getValue(FACING),width=facing.rotateYCCW();
        int column=TileEntityLargeProgrammableDoor.distance(root.getPos(),pos,width);
        int row=TileEntityLargeProgrammableDoor.distance(root.getPos(),pos,root.rowDirection(facing));
        for(AxisAlignedBB box:root.collisionCellGeometry(facing,rootState.getValue(OPEN),column,row))
            if(box.intersects(entityBox))boxes.add(box);}
    private void addUncachedCollisionBoxes(World world,BlockPos pos,TileEntityLargeProgrammableDoor root,
            AxisAlignedBB entityBox,List<AxisAlignedBB> boxes){
        EnumFacing front=world.getBlockState(root.getPos()).getValue(FACING);
        int column=TileEntityLargeProgrammableDoor.distance(root.getPos(),pos,front.rotateYCCW());
        int row=TileEntityLargeProgrammableDoor.distance(root.getPos(),pos,root.rowDirection(front));
        AxisAlignedBB cell=root.collisionPartition(front,column,row);
        for(AxisAlignedBB box:geometry(world,pos)){
            if(!box.intersects(cell))continue;
            AxisAlignedBB clipped=box.intersect(cell);if(clipped.intersects(entityBox))boxes.add(clipped);
        }
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        if(getClass()==BlockLargeProgrammableDoor.class){
            TileEntityLargeProgrammableDoor root=root(world,pos);
            if(root!=null&&root.getClass()==TileEntityLargeProgrammableDoor.class){
                IBlockState actual=world.getBlockState(root.getPos());
                RayTraceResult hit=root.collisionIndex(actual.getValue(FACING),actual.getValue(OPEN)).trace(start,end);
                return hit==null?null:new RayTraceResult(hit.hitVec,hit.sideHit,pos);
            }
        }
        RayTraceResult nearest=null;double distance=Double.MAX_VALUE;
        for(AxisAlignedBB box:geometry(world,pos)){
            RayTraceResult hit=box.calculateIntercept(start,end);
            if(hit!=null&&start.squareDistanceTo(hit.hitVec)<distance){distance=start.squareDistanceTo(hit.hitVec);nearest=new RayTraceResult(hit.hitVec,hit.sideHit,pos);}
        }
        return nearest;
    }
}
