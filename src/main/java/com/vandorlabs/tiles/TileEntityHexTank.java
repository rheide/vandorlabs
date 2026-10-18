package com.vandorlabs.tiles;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;

/** Per-cell storage avoids loss when a vertical stack is split or a chunk unloads. */
public final class TileEntityHexTank extends TileEntityAnimatedScreenSelector implements IFluidHandler {
    public static final int CAPACITY=16000;
    public final FluidTank tank=new FluidTank(CAPACITY){@Override protected void onContentsChanged(){syncContents();}};
    @Override protected void finishLoading(){} // No redstone subscriptions.
    private void syncContents(){markDirty();if(world!=null && !world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    private List<TileEntityHexTank> members(){
        if(world==null)return Collections.singletonList(this);
        EnumFacing axis=world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
        EnumFacing step=EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE,axis.getAxis());
        List<TileEntityHexTank> result=new ArrayList<>();BlockPos bottom=pos;
        for(int i=0;i<255&&same(bottom.offset(step.getOpposite()),axis);i++)bottom=bottom.offset(step.getOpposite());
        for(BlockPos p=bottom;result.size()<256&&same(p,axis);p=p.offset(step))result.add((TileEntityHexTank)world.getTileEntity(p));
        return result;
    }
    private boolean same(BlockPos at,EnumFacing facing){return world.isBlockLoaded(at)&&world.getTileEntity(at) instanceof TileEntityHexTank&&world.getBlockState(at).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING).getAxis()==facing.getAxis();}
    private EnumFacing.Axis portAxis(){return world==null?EnumFacing.Axis.Y:world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING).getAxis();}
    @Override public IFluidTankProperties[] getTankProperties(){
        List<TileEntityHexTank> group=members();FluidStack fluid=null;boolean mixed=false;int amount=0;
        for(TileEntityHexTank cell:group){FluidStack f=cell.tank.getFluid();if(f!=null){if(fluid==null)fluid=f.copy();else if(!fluid.isFluidEqual(f))mixed=true;amount+=f.amount;}}
        if(mixed){List<IFluidTankProperties> properties=new ArrayList<>();for(TileEntityHexTank cell:group)properties.add(new FluidTankProperties(cell.tank.getFluid()==null?null:cell.tank.getFluid().copy(),CAPACITY));return properties.toArray(new IFluidTankProperties[0]);}
        if(fluid!=null)fluid.amount=amount;return new IFluidTankProperties[]{new FluidTankProperties(fluid,group.size()*CAPACITY)};
    }
    @Override public int fill(FluidStack resource,boolean commit){
        if(resource==null || resource.amount<=0)return 0;
        List<TileEntityHexTank> group=members();for(TileEntityHexTank cell:group)if(cell.tank.getFluid()!=null && !cell.tank.getFluid().isFluidEqual(resource))return 0;
        int remaining=resource.amount;for(TileEntityHexTank cell:group){FluidStack part=resource.copy();part.amount=remaining;remaining-=cell.tank.fill(part,commit);if(remaining==0)break;}return resource.amount-remaining;
    }
    @Override public FluidStack drain(FluidStack request,boolean commit){
        if(request==null || request.amount<=0)return null;FluidStack result=null;int remaining=request.amount;
        for(TileEntityHexTank cell:members()){
            FluidStack f=cell.tank.getFluid();if(f==null || !f.isFluidEqual(request))continue;
            FluidStack part=cell.tank.drain(remaining,commit);if(part==null)continue;
            if(result==null)result=part.copy();else result.amount+=part.amount;remaining-=part.amount;if(remaining==0)break;
        }return result;
    }
    @Override public FluidStack drain(int max,boolean commit){if(max<=0)return null;for(TileEntityHexTank cell:members())if(cell.tank.getFluid()!=null){FluidStack request=cell.tank.getFluid().copy();request.amount=max;return drain(request,commit);}return null;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setTag("Tank",tank.writeToNBT(new NBTTagCompound()));return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);tank.setFluid(null);if(tag.hasKey("Tank",10))tank.readFromNBT(tag.getCompoundTag("Tank"));}
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY?(side==null || side.getAxis()==portAxis()):super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)return hasCapability(cap,side)?CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this):null;return super.getCapability(cap,side);}
}
