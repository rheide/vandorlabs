package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockControlledRamp;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.ControllerRecovery;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RampCellData;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.LegacyBlockStates;
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
        for (RampGeometry.Box box:geometry(partial))
            boxes.add(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        return boxes;
    }
    public List<RampGeometry.Box> geometry(double partial) {
        List<RampGeometry.Box> boxes=new ArrayList<>();
        IBlockState state=world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockControlledRamp)) return boxes;
        EnumFacing face=state.getValue(BlockVandorDirectional.FACING);
        return RampGeometry.boxes(direction(face),pos.getY(),sourceY,low,high,
                row,length,drop,segments,pose(partial),top,elevator);
    }
    public double sideTextureV(AxisAlignedBB box,double localY,double partial) {
        return sideTextureV(new RampGeometry.Box(box.minX,box.minY,box.minZ,
                box.maxX,box.maxY,box.maxZ),localY,partial);
    }
    public double sideTextureV(RampGeometry.Box box,double localY,double partial) {
        EnumFacing face=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        RampGeometry.Box portable=new RampGeometry.Box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);
        int step=RampGeometry.segmentAt(direction(face),portable,segments,elevator);
        double offset=ControllerPlatform.offset(row,step,length,segments,drop,pose(partial),top,elevator);
        return ControllerPlatform.sideTextureV(pos.getY(),localY,sourceY,offset);
    }
    private static RampGeometry.Direction direction(EnumFacing face) {
        return RampGeometry.Direction.valueOf(face.getName().toUpperCase(java.util.Locale.ROOT));
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
        new RampCellData(sourceY,row,length,drop,segments,duration,low,high,top,
                elevator,open,moving,startPose,startTick).write(new NbtPrimitiveData(tag));
        tag.setLong(SaveSchema.Ramp.CONTROLLER,controller.toLong());
        tag.setInteger(SaveSchema.Ramp.CONTROLLER_X,controller.getX());
        tag.setInteger(SaveSchema.Ramp.CONTROLLER_Y,controller.getY());
        tag.setInteger(SaveSchema.Ramp.CONTROLLER_Z,controller.getZ());
        tag.setTag(SaveSchema.Ramp.SOURCE,NBTUtil.writeBlockState(new NBTTagCompound(),source));
        tag.setString(SaveSchema.Ramp.SOURCE_STATE,LegacyBlockStates.encode(source));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        controller=tag.hasKey(SaveSchema.Ramp.CONTROLLER_X)
                ?new BlockPos(tag.getInteger(SaveSchema.Ramp.CONTROLLER_X),
                        tag.getInteger(SaveSchema.Ramp.CONTROLLER_Y),
                        tag.getInteger(SaveSchema.Ramp.CONTROLLER_Z))
                :BlockPos.fromLong(tag.getLong(SaveSchema.Ramp.CONTROLLER));
        source=tag.hasKey(SaveSchema.Ramp.SOURCE_STATE)
                ?LegacyBlockStates.decode(tag.getString(SaveSchema.Ramp.SOURCE_STATE)):null;
        if (source==null) source=NBTUtil.readBlockState(tag.getCompoundTag(SaveSchema.Ramp.SOURCE));
        RampCellData data=RampCellData.read(new NbtPrimitiveData(tag));
        sourceY=data.sourceY; row=data.row; length=data.length; drop=data.drop;
        segments=data.segments; duration=data.duration; low=data.low; high=data.high;
        top=data.top; elevator=data.elevator; open=data.open; moving=data.moving;
        startPose=data.startPose; startTick=data.startTick;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos,0,getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
