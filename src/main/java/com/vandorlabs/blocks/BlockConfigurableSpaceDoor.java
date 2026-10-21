package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.SpaceDoorControlPanel;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BlockConfigurableSpaceDoor extends BlockSpaceDoor {
    private static final ThreadLocal<Integer> PLACEMENT_DEPTH = new ThreadLocal<>();

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer, EnumHand hand) {
        EnumFacing front = placer.getHorizontalFacing().getOpposite();
        PLACEMENT_DEPTH.set(PanelDepth.fromHit(front, hitX, hitZ));
        return super.getStateForPlacement(world, pos, side, hitX, hitY, hitZ,
                meta, placer, hand);
    }
    @Override protected boolean canToggleByHand(World world,BlockPos lowerPos) {
        TileEntity raw=world.getTileEntity(lowerPos);
        return !(raw instanceof TileEntitySpaceDoor)
                || ((TileEntitySpaceDoor)raw).securityMode()==0&&((TileEntitySpaceDoor)raw).getTrigger()==SpaceDoorData.TRIGGER_DISABLED;
    }
    @Override public void updateRedstoneState(World world,BlockPos pos,IBlockState state) {
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        TileEntity raw=world.getTileEntity(lower);
        if (!(raw instanceof TileEntitySpaceDoor)) { super.updateRedstoneState(world,pos,state); return; }
        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
        boolean powered=com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,lower)
                || com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,lower.up())
                || tile.isChannelSignalPowered();
        boolean open=tile.securityMode()>0?tile.securityOpen():tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED
                ?world.getBlockState(lower).getValue(OPEN)
                :SpaceDoorData.openForSignal(tile.getTrigger(),powered);
        if (getActualState(state,world,pos).getValue(POWERED)!=powered
                || world.getBlockState(lower).getValue(OPEN)!=open)
            setPowered(world,pos,state,powered,open);
    }
    public BlockConfigurableSpaceDoor(String name,boolean sliding,BlockDetailedDoor paired) {
        super(name,sliding,true,paired);
    }
    @Override public TileEntity createTileEntity(World world,IBlockState state) {
        TileEntitySpaceDoor tile=new TileEntitySpaceDoor();
        if (isSlidingModel()) tile.configure(tile.getDesign(),tile.getDetail(),tile.isFramed(),0,true,true);
        return tile;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing face,float x,float y,float z) {
        BlockPos securityRoot=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        TileEntity securityRaw=world.getTileEntity(securityRoot);
        if(securityRaw instanceof TileEntitySpaceDoor&&((TileEntitySpaceDoor)securityRaw).securityMode()>0&&!player.isSneaking()&&player.getHeldItem(hand).getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER){
            if(hitControlPanel(world,pos,state,face,x,y,z)||((TileEntitySpaceDoor)securityRaw).holdingCredential(player,hand))return ((TileEntitySpaceDoor)securityRaw).useSecurity(player,hand,((TileEntitySpaceDoor)securityRaw).readerIndex(new Vec3d(pos.getX()+x,pos.getY()+y,pos.getZ()+z)));return true;
        }
        if (hand != EnumHand.MAIN_HAND || !player.capabilities.isCreativeMode
                || (!player.isSneaking() && !hitControlPanel(world,pos,state,face,x,y,z)))
            return super.onBlockActivated(world,pos,getActualState(state,world,pos),
                    player,hand,face,x,y,z);
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        if (!world.isRemote) player.openGui(VandorLabs.instance,GuiHandler.GUI_SPACE_DOOR,world,
                lower.getX(),lower.getY(),lower.getZ());
        return true;
    }
    protected boolean hitControlPanel(World world,BlockPos pos,IBlockState state,
            EnumFacing face,float x,float y,float z) {
        boolean upper=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER;
        BlockPos lower=upper?pos.down():pos;
        IBlockState lowerState=world.getBlockState(lower);
        if (lowerState.getBlock()!=this) return false;
        IBlockState actual=getActualState(lowerState,world,lower);
        SpaceDoorControlPanel.Side side=panelSide(world,lower,actual);
        if (side==SpaceDoorControlPanel.Side.NONE) return false;
        EnumFacing front=actual.getValue(FACING);
        if (face!=panelFacing(front,side)) return false;
        double localZ=front==EnumFacing.SOUTH?z:front==EnumFacing.NORTH?1-z
                :front==EnumFacing.EAST?x:1-x;
        TileEntitySpaceDoor tile=settings(state,world,pos);
        return tile!=null && panelContains(tile,localZ,y+(upper?1:0),1);
    }
    public static boolean panelContains(TileEntitySpaceDoor tile,double localZ,double localY,double size){
        double y=(localY-SpaceDoorControlPanel.verticalOffset(size))*16/size;
        double lo=tile.securityMode()>0?15.8:SpaceDoorControlPanel.Y0,hi=tile.securityMode()>0?20.2:SpaceDoorControlPanel.Y1;
        if(y<lo||y>hi)return false;
        double start=SpaceDoorControlPanel.z0(tile.isSliding(),tile.getPlacementDepth()==2),end=start+3;
        if(tile.securityMode()>0){double extra=1.5*(size-1);start-=extra;end+=extra;}
        double z=(localZ-tile.positionOffset())*16;
        if(z>=start&&z<=end)return true;
        z=(1-localZ-tile.positionOffset())*16;return tile.panelMode()==2&&z>=start&&z<=end;
    }
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntitySpaceDoor)((TileEntitySpaceDoor)raw).tickReaders();}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        if(!world.isRemote&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntitySpaceDoor){for(int side=0;side<2;side++){ItemStack token=((TileEntitySpaceDoor)raw).keyReader(side).takeForDrop();if(!token.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,token);}}}
        super.breakBlock(world,pos,state);
    }
    public static SpaceDoorControlPanel.Side panelSide(World world,BlockPos lower,IBlockState actual) {
        TileEntity raw=world.getTileEntity(lower);
        if (raw instanceof TileEntitySpaceDoor && !((TileEntitySpaceDoor)raw).hasPanel())
            return SpaceDoorControlPanel.Side.NONE;
        if(raw instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor)
            return SpaceDoorControlPanel.side(actual.getValue(HINGE)==BlockDoor.EnumHingePosition.RIGHT,
                    ((TileEntitySpaceDoor)raw).isSliding(),true,false,false);
        EnumFacing front=actual.getValue(FACING);
        boolean sliding=raw instanceof TileEntitySpaceDoor && ((TileEntitySpaceDoor)raw).isSliding();
        return SpaceDoorControlPanel.side(actual.getValue(HINGE)==BlockDoor.EnumHingePosition.RIGHT,
                sliding,actual.getValue(PAIRED),hasDoorBeside(world,lower,front.rotateY(),front),
                hasDoorBeside(world,lower,front.rotateYCCW(),front));
    }
    private static boolean hasDoorBeside(World world,BlockPos lower,EnumFacing side,EnumFacing front) {
        BlockPos neighbor=lower.offset(side);
        if (!world.isBlockLoaded(neighbor) || !world.isBlockLoaded(neighbor.up())) return false;
        IBlockState state=world.getBlockState(neighbor);
        return state.getBlock() instanceof BlockConfigurableSpaceDoor
                && state.getValue(HALF)==BlockDoor.EnumDoorHalf.LOWER
                && state.getValue(FACING)==front
                && world.getBlockState(neighbor.up()).getBlock()==state.getBlock();
    }
    private static EnumFacing panelFacing(EnumFacing front,SpaceDoorControlPanel.Side side) {
        return side==SpaceDoorControlPanel.Side.RIGHT?front.rotateY():front.rotateYCCW();
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,
            Vec3d start,Vec3d end) {
        RayTraceResult door=super.collisionRayTrace(state,world,pos,start,end);
        boolean upper=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER;
        BlockPos lower=upper?pos.down():pos;
        IBlockState lowerState=world.getBlockState(lower);
        // A ray may retain the door state after its lower half is removed.
        if (lowerState.getBlock()!=this) return door;
        IBlockState actual=getActualState(lowerState,world,lower);
        SpaceDoorControlPanel.Side side=panelSide(world,lower,actual);
        if (side==SpaceDoorControlPanel.Side.NONE) return door;
        TileEntitySpaceDoor tile=settings(state,world,pos);
        if (tile==null) return door;
        AxisAlignedBB panel=controlPanelBounds(actual.getValue(FACING),side,tile.positionOffset(),tile.isSliding(),tile.getPlacementDepth()==2);
        if(tile.securityMode()>0)panel=panel.grow(0,.7/16,0);
        Vec3d localStart=start.subtract(pos.getX(),pos.getY(),pos.getZ()),localEnd=end.subtract(pos.getX(),pos.getY(),pos.getZ());
        for(int index=0;index<(tile.panelMode()==2?2:1);index++){
            AxisAlignedBB box=index==0?panel:mirrorPanel(panel,actual.getValue(FACING));box=box.offset(0,upper?-1:0,0);
            RayTraceResult local=box.calculateIntercept(localStart,localEnd);if(local==null)continue;
            Vec3d hit=local.hitVec.addVector(pos.getX(),pos.getY(),pos.getZ());
            if(local.sideHit==panelFacing(actual.getValue(FACING),side)||door==null||start.squareDistanceTo(hit)<start.squareDistanceTo(door.hitVec))door=new RayTraceResult(hit,local.sideHit,pos);
        }
        return door;
    }
    public static AxisAlignedBB mirrorPanel(AxisAlignedBB box,EnumFacing face){return face.getAxis()==EnumFacing.Axis.X?new AxisAlignedBB(1-box.maxX,box.minY,box.minZ,1-box.minX,box.maxY,box.maxZ):new AxisAlignedBB(box.minX,box.minY,1-box.maxZ,box.maxX,box.maxY,1-box.minZ);}

    private static AxisAlignedBB controlPanelBounds(EnumFacing facing,
            SpaceDoorControlPanel.Side side,double offset,boolean sliding,boolean farEdge) {
        double x0=SpaceDoorControlPanel.x0(side)/16,x1=SpaceDoorControlPanel.x1(side)/16;
        double z0=SpaceDoorControlPanel.z0(sliding,farEdge)/16,
                z1=SpaceDoorControlPanel.z1(sliding,farEdge)/16;
        double y0=SpaceDoorControlPanel.Y0/16,y1=SpaceDoorControlPanel.Y1/16;
        switch (facing) {
            case NORTH: return new AxisAlignedBB(1-x1,y0,1-z1-offset,1-x0,y1,1-z0-offset);
            case EAST: return new AxisAlignedBB(z0+offset,y0,1-x1,z1+offset,y1,1-x0);
            case WEST: return new AxisAlignedBB(1-z1-offset,y0,x0,1-z0-offset,y1,x1);
            default: return new AxisAlignedBB(x0,y0,z0+offset,x1,y1,z1+offset);
        }
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack) {
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        Integer clickedDepth = PLACEMENT_DEPTH.get();
        PLACEMENT_DEPTH.remove();
        TileEntity raw=world.getTileEntity(pos);
        if (!world.isRemote && raw instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("SpaceDoorSettings",10)) {
                // An explicitly picked configuration takes precedence over
                // both placement defaults and neighbor appearance inheritance.
                tile.applyItemSettings(stack.getTagCompound().getCompoundTag("SpaceDoorSettings"));
                tile.setPlacementDepth(clickedDepth == null ? 1 : clickedDepth);
                return;
            }
            BlockPos mate=tile.mate();
            if (mate!=null && world.isBlockLoaded(mate) && world.getTileEntity(mate) instanceof TileEntitySpaceDoor) {
                TileEntitySpaceDoor other=(TileEntitySpaceDoor)world.getTileEntity(mate);
                tile.configure(other.getDesign(),other.getDetail(),other.isFramed(),other.getSlideDirection(),
                        other.isMiddle(),other.isSliding(),other.hasHinges(),other.getTrigger(),other.hasPanel());
            } else if (isSlidingModel()) {
                // Both the unified block and legacy sliding item default to
                // sideways sliding on the centre track.
                tile.configure(tile.getDesign(),tile.getDetail(),tile.isFramed(),
                        tile.getSlideDirection(),true,true);
            }
            tile.setPlacementDepth(clickedDepth == null ? 1 : clickedDepth);
        }
    }
    @Override public ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult target,
            World world,BlockPos pos,EntityPlayer player) {
        net.minecraft.block.Block unified=net.minecraft.block.Block.REGISTRY.getObject(
                new net.minecraft.util.ResourceLocation("vandorlabs","programmable_door"));
        ItemStack stack=new ItemStack(unified);
        TileEntitySpaceDoor tile=settings(state,world,pos);
        if (tile!=null) stack.setTagInfo("SpaceDoorSettings",tile.itemSettings());
        return stack;
    }
    private TileEntitySpaceDoor settings(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos) {
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        TileEntity raw=world.getTileEntity(lower);
        return raw instanceof TileEntitySpaceDoor?(TileEntitySpaceDoor)raw:null;
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(IBlockState state,
            net.minecraft.world.IBlockAccess world,BlockPos pos) {
        TileEntitySpaceDoor tile=settings(state,world,pos);
        IBlockState actual=getActualState(state,world,pos);
        net.minecraft.util.math.AxisAlignedBB box=tile==null?super.getBoundingBox(state,world,pos)
                :tile.model(tile.isSliding()).spaceBounds(actual,world,pos);
        return tile==null?box:positionedBounds(box,actual.getValue(FACING),tile.positionOffset(),!tile.isSliding() && actual.getValue(OPEN));
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getCollisionBoundingBox(IBlockState state,
            net.minecraft.world.IBlockAccess world,BlockPos pos) {
        TileEntitySpaceDoor tile=settings(state,world,pos);
        if (tile==null) return super.getCollisionBoundingBox(state,world,pos);
        IBlockState actual=getActualState(state,world,pos);
        if (tile.isSliding() && actual.getValue(OPEN)) return NULL_AABB;
        net.minecraft.util.math.AxisAlignedBB box=tile.model(tile.isSliding()).spaceBounds(actual,world,pos);
        EnumFacing facing=actual.getValue(FACING);
        return positionedBounds(box,facing,tile.positionOffset(),!tile.isSliding() && actual.getValue(OPEN));
    }
    private static AxisAlignedBB positionedBounds(AxisAlignedBB box,EnumFacing facing,double offset,boolean rotatedOpen) {
        AxisAlignedBB moved=box.offset(facing.getFrontOffsetX()*offset,0,
                facing.getFrontOffsetZ()*offset);
        if(rotatedOpen)return moved;
        // The older selection boxes are wider than the door art. Keep the
        // shifted box inside its block when the visible outer face is flush.
        if (facing.getAxis()==EnumFacing.Axis.X)
            return new AxisAlignedBB(Math.max(0,moved.minX),moved.minY,moved.minZ,
                    Math.min(1,moved.maxX),moved.maxY,moved.maxZ);
        return new AxisAlignedBB(moved.minX,moved.minY,Math.max(0,moved.minZ),
                moved.maxX,moved.maxY,Math.min(1,moved.maxZ));
    }
}
