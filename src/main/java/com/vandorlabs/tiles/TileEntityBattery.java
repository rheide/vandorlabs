package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockVandorDirectional;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.*;

/** Forge Energy buffer with an input front and an output rear. */
public final class TileEntityBattery extends TileEntity implements ITickable {
    public static final int CAPACITY=100000, RATE=1000;
    private int energy;
    private final IEnergyStorage input=new Port(true,false),output=new Port(false,true),internal=new Port(true,true);
    public EnumFacing inputSide(){return world==null?EnumFacing.NORTH:world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);}
    private final class Port implements IEnergyStorage {
        private final boolean receive,extract;
        Port(boolean receive,boolean extract){this.receive=receive;this.extract=extract;}
        public int receiveEnergy(int max,boolean simulate){int amount=receive?Math.min(RATE,Math.min(Math.max(0,max),CAPACITY-energy)):0;if(!simulate && amount>0){energy+=amount;markDirty();}return amount;}
        public int extractEnergy(int max,boolean simulate){int amount=extract?Math.min(RATE,Math.min(Math.max(0,max),energy)):0;if(!simulate && amount>0){energy-=amount;markDirty();}return amount;}
        public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return CAPACITY;}
        public boolean canExtract(){return extract;}public boolean canReceive(){return receive;}
    }
    @Override public void update(){
        if(world==null || world.isRemote || energy==0)return;
        EnumFacing side=inputSide().getOpposite();if(!world.isBlockLoaded(pos.offset(side)))return;
        TileEntity neighbor=world.getTileEntity(pos.offset(side));if(neighbor==null)return;
        IEnergyStorage target=neighbor.getCapability(CapabilityEnergy.ENERGY,side.getOpposite());
        if(target!=null && target.canReceive())output.extractEnergy(target.receiveEnergy(Math.min(RATE,energy),false),false);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setInteger("Energy",energy);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);energy=Math.max(0,Math.min(CAPACITY,tag.getInteger("Energy")));}
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY?(side==null || side==inputSide() || side==inputSide().getOpposite()):super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityEnergy.ENERGY)return hasCapability(cap,side)?CapabilityEnergy.ENERGY.cast(side==null?internal:side==inputSide()?input:output):null;return super.getCapability(cap,side);}
}
