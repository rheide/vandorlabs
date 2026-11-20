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
    public static boolean isOpenPassage(World world,BlockPos pos,IBlockState state){
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor)||!state.getValue(OPEN))return false;
        TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)state.getBlock()).root(world,pos);
        return root!=null&&world.getBlockState(root.getPos()).getValue(OPEN);
    }
    /** Retained for integrations which specifically query horizontal hatches. */
    public static boolean isOpenHorizontal(World world,BlockPos pos,IBlockState state){
        if(!isOpenPassage(world,pos,state))return false;
        return ((BlockLargeProgrammableDoor)state.getBlock()).root(world,pos).isHorizontal();
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
        } else if(tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED && !world.isRemote && mayToggleJoined(world,tile,player,face,hand))
            setPowered(world,tile.getPos(),state,world.getBlockState(tile.getPos()).getValue(POWERED),!world.getBlockState(tile.getPos()).getValue(OPEN));
        return true;
    }
    @Override public void updateRedstoneState(World world,BlockPos pos,IBlockState state){
        if(world.isRemote)return;TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null)return;
        IBlockState actual=world.getBlockState(tile.getPos());
        com.vandorlabs.render.LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=tile.joinedGroup();
        boolean powered=false;
        for(TileEntityLargeProgrammableDoor member:group.members)
            powered|=member.hasLocalRedstoneSignal()||member.isChannelSignalPowered();
        boolean open=tile.securityMode()>0?tile.securityOpen():tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED?world.getBlockState(group.anchor.getPos()).getValue(OPEN):SpaceDoorData.openForSignal(tile.getTrigger(),powered);
        setPowered(world,tile.getPos(),actual,powered,open);
    }
    @Override public void toggleDoor(World world,BlockPos pos,boolean open){
        if(world.isRemote)return;
        TileEntityLargeProgrammableDoor tile=root(world,pos);
        if(tile!=null)super.toggleDoor(world,tile.getPos(),open);
    }
    @Override protected void setPowered(World world,BlockPos pos,IBlockState state,boolean powered,boolean open){
        TileEntityLargeProgrammableDoor tile=root(world,pos);if(tile==null || !complete(world,tile))return;
        for(TileEntityLargeProgrammableDoor member:tile.joinedGroup().members)if(!complete(world,member))return;
        // An arm built through the doorway holds the whole joined group open.
        if(!open&&world.getBlockState(tile.getPos()).getValue(OPEN)&&armPassesThrough(world,tile))open=true;
        for(TileEntityLargeProgrammableDoor member:tile.joinedGroup().members)publishPower(world,member,powered,open);
    }
    /** True when an arm column runs through any cell of {@code tile}'s joined group. */
    public boolean armPassesThrough(World world,TileEntityLargeProgrammableDoor tile){
        for(TileEntityLargeProgrammableDoor member:tile.joinedGroup().members){
            BlockPos anchor=member.getPos();EnumFacing front=world.getBlockState(anchor).getValue(FACING);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(com.vandorlabs.gear.ArmPassages.occupied(world,member.cell(anchor,front,x,y)))return true;
        }
        return false;
    }
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,net.minecraft.entity.player.EntityPlayer player,boolean willHarvest){
        TileEntityLargeProgrammableDoor tile=world.isRemote?null:root(world,pos);
        if(tile!=null&&armPassesThrough(world,tile)){
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("An arm runs through this door; remove the arm first"),true);
            world.notifyBlockUpdate(pos,state,state,3);return false;
        }
        return super.removedByPlayer(state,world,pos,player,willHarvest);
    }
    private void publishPower(World world,TileEntityLargeProgrammableDoor tile,boolean powered,boolean open){
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
    public boolean mayToggleJoined(World world,TileEntityLargeProgrammableDoor tile,EntityPlayer player,EnumFacing hit,EnumHand hand){
        com.vandorlabs.render.LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=tile.joinedGroup();
        if(!group.joined())return true;
        for(TileEntityLargeProgrammableDoor member:group.members)
            if(!mayEditModule(world,member,player,hit,player.getHeldItem(hand)))return false;
        return true;
    }
    private static boolean mayEditModule(World world,TileEntityLargeProgrammableDoor member,EntityPlayer player,EnumFacing side,ItemStack item){
        BlockPos anchor=member.getPos();
        EnumFacing facing=world.getBlockState(anchor).getValue(FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos at=member.cell(anchor,facing,x,y);
            if(!world.isBlockLoaded(at,false)||!world.isBlockModifiable(player,at)||!player.canPlayerEdit(at,side,item))return false;
        }
        return true;
    }
    public boolean mayEditJoinedNeighbors(World world,BlockPos pos,EnumFacing facing,boolean horizontal,EntityPlayer player,ItemStack held){
        EnumFacing row=horizontal?facing:EnumFacing.UP;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){
            if(x==0&&y==0)continue;
            BlockPos at=pos.offset(facing.rotateYCCW(),3*x).offset(row,3*y);
            if(!world.isBlockLoaded(at,false))continue;
            TileEntityLargeProgrammableDoor other=root(world,at);
            if(other!=null&&other.getPos().equals(at)&&other.joins()&&other.isHorizontal()==horizontal
                    &&world.getBlockState(at).getValue(FACING)==facing
                    &&!mayEditModule(world,other,player,row,held))return false;
        }
        return true;
    }
    public void joinChanged(World world,TileEntityLargeProgrammableDoor tile){
        EnumFacing facing=world.getBlockState(tile.getPos()).getValue(FACING);
        java.util.List<TileEntityLargeProgrammableDoor> nearby=new java.util.ArrayList<>(9);
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){
            BlockPos at=tile.cell(tile.getPos(),facing,3*x,3*y);
            if(!world.isBlockLoaded(at,false))continue;
            TileEntityLargeProgrammableDoor other=root(world,at);
            if(other!=null&&other.getPos().equals(at)){other.invalidateJoin();nearby.add(other);}
        }
        for(TileEntityLargeProgrammableDoor other:nearby)
            updateRedstoneState(world,other.getPos(),world.getBlockState(other.getPos()));
    }
    private static boolean solidSupport(World world,BlockPos anchor,EnumFacing facing,EnumFacing row){
        for(int x=0;x<3;x++){
            BlockPos at=anchor.offset(facing.rotateYCCW(),x).offset(row.getOpposite());
            if(!world.isBlockLoaded(at,false)||!world.getBlockState(at).isSideSolid(world,at,row))return false;
        }
        return true;
    }
    /** A second row can use a complete first door, which must have its own real support. */
    public boolean supports(World world,BlockPos anchor,EnumFacing facing,boolean horizontal,boolean joiningPlacement){
        EnumFacing row=horizontal?facing:EnumFacing.UP;
        if(solidSupport(world,anchor,facing,row))return true;
        BlockPos preceding=anchor.offset(row.getOpposite(),3);
        if(!world.isBlockLoaded(preceding,false))return false;
        TileEntityLargeProgrammableDoor base=root(world,preceding);
        return base!=null&&base.getPos().equals(preceding)&&base.isHorizontal()==horizontal
                &&(!joiningPlacement||base.joins())&&world.getBlockState(preceding).getValue(FACING)==facing
                &&complete(world,base)&&solidSupport(world,preceding,facing,row);
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){
        if(world.isRemote || REMOVING.get())return;
        TileEntityLargeProgrammableDoor tile=root(world,pos);
        if(tile==null){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityLargeProgrammableDoor && world.isBlockLoaded(((TileEntityLargeProgrammableDoor)raw).anchorPos()))world.setBlockToAir(pos);return;}
        EnumFacing front=world.getBlockState(tile.getPos()).getValue(FACING);
        for(int x=0;x<3;x++)for(int y=-1;y<3;y++)if(!world.isBlockLoaded(tile.cell(tile.getPos(),front,x,y)))return;
        if(!complete(world,tile)){world.destroyBlock(tile.getPos(),true);return;}
        if(!supports(world,tile.getPos(),front,tile.isHorizontal(),false)){world.destroyBlock(tile.getPos(),true);return;}
        tile.invalidateJoin();
        tile.localInputChanged();updateRedstoneState(world,tile.getPos(),state);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity raw=world.getTileEntity(pos);
        TileEntityLargeProgrammableDoor part=raw instanceof TileEntityLargeProgrammableDoor?(TileEntityLargeProgrammableDoor)raw:null;
        boolean assemblyRemoval=!REMOVING.get();
        EnumFacing face=state.getValue(FACING);
        BlockPos released=part==null?pos:part.anchorPos(face);
        boolean checkDependent=part!=null&&assemblyRemoval&&!world.isRemote
                &&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world);
        if(assemblyRemoval)REMOVING.set(true);
        try {
            if(!world.isRemote&&part!=null&&part.isHorizontal())DoorWaterGush.stop(world,pos);
            if(assemblyRemoval){
                TileEntityLargeProgrammableDoor reader=root(world,pos);
                if(!world.isRemote&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)&&reader!=null){
                    for(int side=0;side<2;side++){
                        ItemStack token=reader.keyReader(side).takeForDrop();
                        if(!token.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,token);
                    }
                }
                EnumFacing front=state.getValue(FACING);
                BlockPos anchor=part==null?pos:part.anchorPos(front);
                for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                    BlockPos cell=part==null?anchor.offset(front.rotateYCCW(),x).up(y):part.cell(anchor,front,x,y);
                    if(cell.equals(pos)||!world.isBlockLoaded(cell))continue;
                    TileEntity other=world.getTileEntity(cell);
                    if(world.getBlockState(cell).getBlock()==this&&other instanceof TileEntityLargeProgrammableDoor
                            &&((TileEntityLargeProgrammableDoor)other).anchorPos().equals(anchor))world.setBlockToAir(cell);
                }
            }
            world.removeTileEntity(pos);
        } finally {
            if(assemblyRemoval)REMOVING.remove();
        }
        if(checkDependent){
            BlockPos successor=released.offset(part.rowDirection(face),3);
            if(world.isBlockLoaded(successor,false)){
                TileEntityLargeProgrammableDoor next=root(world,successor);
                if(next!=null&&next.getPos().equals(successor))
                    neighborChanged(world.getBlockState(successor),world,successor,this,released);
            }
        }
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
