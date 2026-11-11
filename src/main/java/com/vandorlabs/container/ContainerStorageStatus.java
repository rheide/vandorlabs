package com.vandorlabs.container;

import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

/** Read-only live meters; no client-supplied charge or fluid mutations. */
public final class ContainerStorageStatus extends Container {
    public final TileEntity tile;
    public final int[] values=new int[3];
    private final WindowPropertyDeltas<IContainerListener> deltas=new WindowPropertyDeltas<>(
            (listener,id,value)->listener.sendWindowProperty(this,id,value));
    public ContainerStorageStatus(TileEntity tile){this.tile=tile;sample();}
    private void sample(){
        if(tile instanceof TileEntityBattery){net.minecraftforge.energy.IEnergyStorage e=tile.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,null);values[0]=e.getEnergyStored();values[1]=e.getMaxEnergyStored();values[2]=-1;}
        else if(tile instanceof TileEntityHexTank){values[0]=values[1]=0;values[2]=-1;for(IFluidTankProperties p:((TileEntityHexTank)tile).getTankProperties()){values[1]+=p.getCapacity();FluidStack f=p.getContents();if(f!=null){values[0]+=f.amount;values[2]=f.getFluid()==FluidRegistry.WATER?0xff3f76e4:f.getFluid()==FluidRegistry.LAVA?0xffff831a:f.getFluid().getColor(f);}}}
    }
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();sample();for(IContainerListener listener:listeners)deltas.sendChanges(listener,values);}
    @Override public void removeListener(IContainerListener listener){super.removeListener(listener);deltas.removeListener(listener);}
    @Override public void updateProgressBar(int id,int data){WindowPropertyDeltas.receive(values,id,data);}
    @Override public boolean canInteractWith(EntityPlayer player){return !tile.isInvalid()&&tile.getWorld()==player.world&&tile.getWorld().getTileEntity(tile.getPos())==tile&&player.getDistanceSq(tile.getPos())<=64;}
    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player,int slot){return net.minecraft.item.ItemStack.EMPTY;}
}
