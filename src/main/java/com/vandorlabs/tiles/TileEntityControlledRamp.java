package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockControlledRamp;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.ControllerRecovery;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Non-ticking occupied/next-step cell. Carries its own recovery journal and timeline. */
public class TileEntityControlledRamp extends TileEntity {
    public BlockPos controller=BlockPos.ORIGIN;
    public IBlockState source=Blocks.STONE.getDefaultState();
    public int sourceY,row,length=1,drop=3,segments=2,duration=60;
    public double low,high=1;
    public boolean top=true,elevator;
    private boolean open,moving;
    private double startPose;
    private long startTick;

    public boolean isOpen() { return open; }
    public boolean isMoving() { return moving; }
    public double pose(double partial) {
        return moving?ControllerPlatform.pose(startPose,open,world.getTotalWorldTime()-startTick+partial,duration):startPose;
    }
    public void move(boolean target,double from,long tick,int ticks,boolean animate) {
        open=target; startPose=from; startTick=tick; duration=ticks; moving=animate;
        markDirty();
        IBlockState s=world.getBlockState(pos);
        world.notifyBlockUpdate(pos,s,s,2);
    }
    public List<AxisAlignedBB> boxes(double partial) {
        List<AxisAlignedBB> boxes=new ArrayList<>();
        IBlockState state=world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockControlledRamp)) return boxes;
        EnumFacing face=state.getValue(BlockVandorDirectional.FACING);
        int count=elevator?1:segments;
        for (int i=0;i<count;i++) {
            double offset=ControllerPlatform.offset(row,i,length,segments,drop,pose(partial),top,elevator);
            double y0=Math.max(0,sourceY+low+offset-pos.getY());
            double y1=Math.min(1,sourceY+high+offset-pos.getY());
            if (y1-y0<1e-8) continue;
            double a=(double)i/count,b=(double)(i+1)/count;
            boxes.add(new AxisAlignedBB(face==EnumFacing.EAST?a:face==EnumFacing.WEST?1-b:0,y0,
                    face==EnumFacing.SOUTH?a:face==EnumFacing.NORTH?1-b:0,
                    face==EnumFacing.EAST?b:face==EnumFacing.WEST?1-a:1,y1,
                    face==EnumFacing.SOUTH?b:face==EnumFacing.NORTH?1-a:1));
        }
        return boxes;
    }
    public double sideTextureV(AxisAlignedBB box,double localY,double partial) {
        EnumFacing face=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        double along=face.getAxis()==EnumFacing.Axis.X?(box.minX+box.maxX)/2:(box.minZ+box.maxZ)/2;
        if (face==EnumFacing.NORTH || face==EnumFacing.WEST) along=1-along;
        int step=elevator?0:Math.max(0,Math.min(segments-1,(int)(along*segments)));
        double offset=ControllerPlatform.offset(row,step,length,segments,drop,pose(partial),top,elevator);
        return ControllerPlatform.sideTextureV(pos.getY(),localY,sourceY,offset);
    }
    public boolean belongsTo(BlockPos owner) { return controller.equals(owner); }
    public void restore() {
        if (world.getTileEntity(pos)!=this) return;
        BlockPos origin=new BlockPos(pos.getX(),sourceY,pos.getZ());
        TileEntity other=world.getTileEntity(origin);
        // Dynamic reservations may no longer include the source position. Restore it once.
        if (world.isAirBlock(origin) || (other instanceof TileEntityControlledRamp
                && ((TileEntityControlledRamp)other).belongsTo(controller))) world.setBlockState(origin,source,3);
        if (!pos.equals(origin) && world.getTileEntity(pos)==this) world.setBlockToAir(pos);
    }
    public void recoverOnEvent() {
        if (world.isRemote || !world.isBlockLoaded(controller)) return;
        TileEntity owner=world.getTileEntity(controller);
        if (!(owner instanceof TileEntityRampController) || !((TileEntityRampController)owner).owns(this)) restore();
        else ((TileEntityRampController)owner).resumeAfterLoad();
    }
    @Override public void onLoad() {
        if (!world.isRemote) {
            ControllerRecovery.register(this);
            world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),1);
        }
    }
    @Override public void invalidate() { ControllerRecovery.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { ControllerRecovery.unregister(this); super.onChunkUnload(); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("Controller",controller.toLong());
        tag.setTag("Source",NBTUtil.writeBlockState(new NBTTagCompound(),source));
        tag.setInteger("SourceY",sourceY); tag.setInteger("Row",row); tag.setInteger("Length",length);
        tag.setInteger("Drop",drop); tag.setInteger("Segments",segments); tag.setInteger("Duration",duration);
        tag.setDouble("Low",low); tag.setDouble("High",high);
        tag.setBoolean("Top",top); tag.setBoolean("Elevator",elevator);
        tag.setBoolean("Open",open); tag.setBoolean("Moving",moving);
        tag.setDouble("StartPose",startPose); tag.setLong("StartTick",startTick);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        controller=BlockPos.fromLong(tag.getLong("Controller")); source=NBTUtil.readBlockState(tag.getCompoundTag("Source"));
        sourceY=tag.getInteger("SourceY"); length=Math.max(1,Math.min(128,tag.getInteger("Length")));
        row=Math.max(0,Math.min(length-1,tag.getInteger("Row"))); drop=Math.max(1,Math.min(16,tag.getInteger("Drop")));
        segments=tag.getInteger("Segments")==8?8:2; duration=Math.max(1,tag.getInteger("Duration"));
        low=tag.getDouble("Low"); high=tag.getDouble("High");
        top=!tag.hasKey("Top") || tag.getBoolean("Top"); elevator=tag.getBoolean("Elevator");
        open=tag.getBoolean("Open"); moving=tag.getBoolean("Moving");
        double p=tag.getDouble("StartPose"); startPose=Double.isFinite(p)?Math.max(0,Math.min(1,p)):0;
        startTick=tag.getLong("StartTick");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos,0,getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
