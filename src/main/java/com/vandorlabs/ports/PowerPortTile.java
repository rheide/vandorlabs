package com.vandorlabs.ports;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Forge Energy interoperability without optional-mod interfaces. RF is the player-facing unit. */
public final class PowerPortTile extends ResourcePortTile {
    public static final int CAPACITY=1000000;
    private int energy;
    private final IEnergyStorage[] faces=new IEnergyStorage[6];
    public PowerPortTile(){for(int i=0;i<6;i++)faces[i]=new Face(i);}
    public int stored(){return energy;}
    @Override protected int storedUnits(){return energy;}
    @Override protected int simulateOutputCapacity(int side,int maximum){Object raw=neighbor(side);return compatible(raw,true)?Math.min(maximum,Math.max(0,((IEnergyStorage)raw).receiveEnergy(maximum,true))):0;}
    @Override protected Object findHandler(TileEntity tile,EnumFacing side){return tile.getCapability(CapabilityEnergy.ENERGY,side);}
    @Override protected boolean compatible(Object handler,boolean receiving){return handler instanceof IEnergyStorage&&(receiving?((IEnergyStorage)handler).canReceive():((IEnergyStorage)handler).canExtract());}
    @Override protected void transferTick(){
        if(settings().pullInputs&&energy<CAPACITY)for(int i=0;i<6&&energy<CAPACITY;i++)if(input(i)){
            Object raw=neighbor(i);if(!compatible(raw,false))continue;IEnergyStorage source=(IEnergyStorage)raw;
            int offer=Math.min(CAPACITY-energy,Math.max(0,source.extractEnergy(CAPACITY-energy,true)));if(offer<=0)continue;
            int received=Math.max(0,Math.min(offer,source.extractEnergy(offer,false)));if(received>0){energy+=received;transferred(i,received,false);}
        }
        if(energy==0||availableOutput()==0)return;
        int offer=boundedOutput(energy);
        for(int i=0;i<6;i++){capacities[i]=0;if(output(i)){Object raw=neighbor(i);if(compatible(raw,true))capacities[i]=Math.min(offer,Math.max(0,((IEnergyStorage)raw).receiveEnergy(offer,true)));}}
        allocate(energy);
        for(int i=0;i<6;i++)if(grants[i]>0&&energy>0){Object raw=neighbor(i);if(!compatible(raw,true))continue;int amount=(int)Math.min(grants[i],boundedOutput(energy));if(amount<=0)continue;
            int accepted=Math.min(amount,Math.max(0,((IEnergyStorage)raw).receiveEnergy(amount,false)));if(accepted>0){energy-=accepted;transferred(i,accepted,true);}
        }
    }
    @Override protected void writeContents(NBTTagCompound tag){tag.setInteger("PortEnergy",energy);}
    @Override protected void readContents(NBTTagCompound tag){energy=Math.max(0,Math.min(CAPACITY,tag.getInteger("PortEnergy")));}
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY?side!=null&&settings().face(side.getIndex())!=ResourcePortSettings.Face.DISABLED:super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityEnergy.ENERGY)return hasCapability(cap,side)?CapabilityEnergy.ENERGY.cast(faces[side.getIndex()]):null;return super.getCapability(cap,side);}
    private final class Face implements IEnergyStorage {
        private final int side;Face(int side){this.side=side;}
        @Override public int receiveEnergy(int max,boolean simulate){if(!input(side)||!transferAllowed())return 0;int amount=Math.min(Math.max(0,max),CAPACITY-energy);if(!simulate&&amount>0){energy+=amount;transferred(side,amount,false);}return amount;}
        @Override public int extractEnergy(int max,boolean simulate){if(max<=0||!output(side)||!transferAllowed())return 0;int amount=Math.min(energy,boundedOutput(side,max));if(!simulate&&amount>0){energy-=amount;transferred(side,amount,true);}return amount;}
        @Override public int getEnergyStored(){return energy;}
        @Override public int getMaxEnergyStored(){return CAPACITY;}
        @Override public boolean canExtract(){return output(side);}
        @Override public boolean canReceive(){return input(side);}
    }
}
