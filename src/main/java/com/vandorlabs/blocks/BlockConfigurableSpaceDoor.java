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
    @Override protected boolean canToggleByHand(World world,BlockPos lowerPos) {
        TileEntity raw=world.getTileEntity(lowerPos);
        return !(raw instanceof TileEntitySpaceDoor)
                || ((TileEntitySpaceDoor)raw).getTrigger()==SpaceDoorData.TRIGGER_DISABLED;
    }
    @Override public void updateRedstoneState(World world,BlockPos pos,IBlockState state) {
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        TileEntity raw=world.getTileEntity(lower);
        if (!(raw instanceof TileEntitySpaceDoor)) { super.updateRedstoneState(world,pos,state); return; }
        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
        boolean powered=world.isBlockPowered(lower) || world.isBlockPowered(lower.up())
                || tile.isChannelSignalPowered();
        boolean open=tile.getTrigger()==SpaceDoorData.TRIGGER_DISABLED
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
        if (!player.isSneaking() && !hitControlPanel(world,pos,state,face,x,y,z))
            return super.onBlockActivated(world,pos,state,player,hand,face,x,y,z);
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        if (!world.isRemote) player.openGui(VandorLabs.instance,GuiHandler.GUI_SPACE_DOOR,world,
                lower.getX(),lower.getY(),lower.getZ());
        return true;
    }
    private boolean hitControlPanel(World world,BlockPos pos,IBlockState state,
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
        return tile!=null && SpaceDoorControlPanel.contains(
                localZ*16-tile.positionOffset()*16,y*16+(upper?16:0),tile.isSliding());
    }
    public static SpaceDoorControlPanel.Side panelSide(World world,BlockPos lower,IBlockState actual) {
        TileEntity raw=world.getTileEntity(lower);
        if (raw instanceof TileEntitySpaceDoor && !((TileEntitySpaceDoor)raw).hasPanel())
            return SpaceDoorControlPanel.Side.NONE;
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
        AxisAlignedBB panel=controlPanelBounds(actual.getValue(FACING),side,tile.positionOffset(),tile.isSliding())
                .offset(0,upper?-1:0,0);
        RayTraceResult local=panel.calculateIntercept(start.subtract(pos.getX(),pos.getY(),pos.getZ()),
                end.subtract(pos.getX(),pos.getY(),pos.getZ()));
        if (local==null) return door;
        Vec3d hit=local.hitVec.addVector(pos.getX(),pos.getY(),pos.getZ());
        RayTraceResult pad=new RayTraceResult(hit,local.sideHit,pos);
        // The leaf's broad selection box can cover the narrow jamb pad while
        // it swings. A ray through the pad's inward face targets the pad.
        return local.sideHit==panelFacing(actual.getValue(FACING),side)
                || door==null || start.squareDistanceTo(hit)<start.squareDistanceTo(door.hitVec)?pad:door;
    }
    private static AxisAlignedBB controlPanelBounds(EnumFacing facing,
            SpaceDoorControlPanel.Side side,double offset,boolean sliding) {
        double x0=SpaceDoorControlPanel.x0(side)/16,x1=SpaceDoorControlPanel.x1(side)/16;
        double z0=SpaceDoorControlPanel.z0(sliding)/16,z1=SpaceDoorControlPanel.z1(sliding)/16;
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
        TileEntity raw=world.getTileEntity(pos);
        if (!world.isRemote && raw instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("SpaceDoorSettings",10)) {
                // An explicitly picked configuration takes precedence over
                // both placement defaults and neighbor appearance inheritance.
                tile.applyItemSettings(stack.getTagCompound().getCompoundTag("SpaceDoorSettings"));
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
        }
    }
    @Override public ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult target,
            World world,BlockPos pos,EntityPlayer player) {
        net.minecraft.block.Block unified=net.minecraft.block.Block.REGISTRY.getObject(
                new net.minecraft.util.ResourceLocation("vandorlabs","space_door"));
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
        if (tile!=null) {
            EnumFacing facing=actual.getValue(FACING);
            box=box.offset(facing.getFrontOffsetX()*tile.positionOffset(),0,
                    facing.getFrontOffsetZ()*tile.positionOffset());
        }
        return box;
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getCollisionBoundingBox(IBlockState state,
            net.minecraft.world.IBlockAccess world,BlockPos pos) {
        TileEntitySpaceDoor tile=settings(state,world,pos);
        if (tile==null) return super.getCollisionBoundingBox(state,world,pos);
        IBlockState actual=getActualState(state,world,pos);
        if (tile.isSliding() && actual.getValue(OPEN)) return NULL_AABB;
        net.minecraft.util.math.AxisAlignedBB box=tile.model(tile.isSliding()).spaceBounds(actual,world,pos);
        EnumFacing facing=actual.getValue(FACING);
        return box.offset(facing.getFrontOffsetX()*tile.positionOffset(),0,
                facing.getFrontOffsetZ()*tile.positionOffset());
    }
}
