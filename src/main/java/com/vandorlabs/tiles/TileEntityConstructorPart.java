package com.vandorlabs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.state.IBlockState;
import com.vandorlabs.blocks.BlockProgrammableBlock;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Relative owner identity survives moving a complete machine with world-edit tools. */
public final class TileEntityConstructorPart extends TileEntity implements net.minecraft.util.ITickable {
    private BlockPos offset=BlockPos.ORIGIN;private boolean matter;private int ticks;
    public static final String FRAME_FACING="ConstructorFrameFacing", FRAME_DATA="ConstructorFrameData";
    private EnumFacing frameFacing=EnumFacing.NORTH;
    private NBTTagCompound frameData;
    public static final class Frame {
        private final EnumFacing facing;
        private final NBTTagCompound data;
        public Frame(EnumFacing facing,NBTTagCompound data){this.facing=facing;this.data=data;}
    }
    public void owner(BlockPos root,boolean matter){owner(root,matter,EnumFacing.NORTH,null);}
    public void owner(BlockPos root,Frame frame){
        if(frame==null)owner(root,false);
        else owner(root,true,frame.facing,frame.data);
    }
    public void owner(BlockPos root,boolean matter,EnumFacing facing,NBTTagCompound data){
        offset=root.subtract(pos);this.matter=matter;frameFacing=facing;frameData=data;
        markDirty();
        if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
    }
    public void restoreFrame(int flags){
        if(!matter){world.setBlockState(pos,net.minecraft.init.Blocks.AIR.getDefaultState(),flags);return;}
        IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockProgrammableBlock.FACING,frameFacing);
        if(!world.setBlockState(pos,state,flags)||frameData==null)return;
        TileEntity raw=world.getTileEntity(pos);
        if(!(raw instanceof TileEntityAnimatedScreenSelector))return;
        NBTTagCompound moved=frameData.copy();
        moved.setInteger("x",pos.getX());moved.setInteger("y",pos.getY());moved.setInteger("z",pos.getZ());
        raw.readFromNBT(moved);raw.markDirty();
        world.notifyBlockUpdate(pos,state,state,flags);
    }
    @Override public void update(){
        if(world==null || world.isRemote || ++ticks%20!=0 || !world.isBlockLoaded(owner()))return;
        if(com.vandorlabs.gear.ArmTransactions.locked(world,pos))return;
        TileEntity root=world.getTileEntity(owner());
        if(root instanceof TileEntityConstructor && ((TileEntityConstructor)root).formed())return;
        restoreFrame(3);
    }
    TileEntityConstructor machineOwner(){if(world==null||isInvalid()||!world.isBlockLoaded(pos,false)||!world.isBlockLoaded(owner(),false)||world.getTileEntity(pos)!=this)return null;TileEntity raw=world.getTileEntity(owner());return raw instanceof TileEntityConstructor?(TileEntityConstructor)raw:null;}
    private TileEntityConstructor observer;
    private net.minecraftforge.energy.IEnergyStorage observerInput;
    private TileEntityConstructor fuelOwner;
    private EnumFacing fuelFace;
    private IFluidHandler fuelInput;
    private <T>T machineCapability(net.minecraftforge.common.capabilities.Capability<T> cap,net.minecraft.util.EnumFacing side){
        TileEntityConstructor root=machineOwner();
        if(root==null)return null;
        if(cap==net.minecraftforge.energy.CapabilityEnergy.ENERGY&&side==net.minecraft.util.EnumFacing.DOWN&&root.acceptsObserverPower(this)){
            if(root!=observer){observer=root;observerInput=root.createObserverInput(this);}
            return net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(observerInput);
        }
        if(cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY&&root.acceptsMaxwellFuel(this,side)){
            if(root!=fuelOwner||side!=fuelFace){
                fuelOwner=root;
                fuelFace=side;
                fuelInput=root.maxwellInlet(this,side);
            }
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fuelInput);
        }
        return root.synthesisCapability(cap,pos,side);
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap,net.minecraft.util.EnumFacing side){return machineCapability(cap,side)!=null||super.hasCapability(cap,side);}
    @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,net.minecraft.util.EnumFacing side){T result=machineCapability(cap,side);return result!=null?result:super.getCapability(cap,side);}
    public BlockPos owner(){return pos.add(offset);}
    public boolean frameMatter(){return matter;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setLong("ConstructorOwner",offset.toLong());tag.setBoolean("ConstructorMatter",matter);if(frameData!=null){tag.setInteger(FRAME_FACING,frameFacing.getIndex());tag.setTag(FRAME_DATA,frameData.copy());}return tag;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound update=new NBTTagCompound();super.writeToNBT(update);update.setLong("ConstructorOwner",offset.toLong());update.setBoolean("ConstructorMatter",matter);return update;}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);offset=BlockPos.fromLong(tag.getLong("ConstructorOwner"));matter=tag.getBoolean("ConstructorMatter");frameData=matter&&tag.hasKey(FRAME_DATA,10)?tag.getCompoundTag(FRAME_DATA).copy():null;frameFacing=frameData==null?EnumFacing.NORTH:EnumFacing.getFront(tag.getInteger(FRAME_FACING));}
}
