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
    public ContainerStorageStatus(TileEntity tile){this.tile=tile;sample();}
    private void sample(){
        if(tile instanceof TileEntityBattery){net.minecraftforge.energy.IEnergyStorage e=tile.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,null);values[0]=e.getEnergyStored();values[1]=e.getMaxEnergyStored();values[2]=-1;}
        else if(tile instanceof TileEntityHexTank){values[0]=values[1]=0;values[2]=-1;for(IFluidTankProperties p:((TileEntityHexTank)tile).getTankProperties()){values[1]+=p.getCapacity();FluidStack f=p.getContents();if(f!=null){values[0]+=f.amount;values[2]=f.getFluid()==FluidRegistry.WATER?0xff3f76e4:f.getFluid()==FluidRegistry.LAVA?0xffff831a:f.getFluid().getColor(f);}}}
    }
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();sample();for(IContainerListener listener:listeners)for(int i=0;i<3;i++){listener.sendWindowProperty(this,i*2,values[i]&65535);listener.sendWindowProperty(this,i*2+1,values[i]>>>16);}}
    @Override public void updateProgressBar(int id,int data){if(id>=0&&id<6){int i=id/2;values[i]=(id%2==0)?(values[i]&0xffff0000)|(data&65535):(values[i]&65535)|((data&65535)<<16);}}
    @Override public boolean canInteractWith(EntityPlayer player){return !tile.isInvalid()&&tile.getWorld()==player.world&&tile.getWorld().getTileEntity(tile.getPos())==tile&&player.getDistanceSq(tile.getPos())<=64;}
    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player,int slot){return net.minecraft.item.ItemStack.EMPTY;}
}
