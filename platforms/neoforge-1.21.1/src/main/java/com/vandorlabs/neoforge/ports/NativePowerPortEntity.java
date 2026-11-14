package com.vandorlabs.neoforge.ports;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class NativePowerPortEntity extends NativeResourcePortEntity {
    public static final int CAPACITY=1000000;
    private int energy;
    private final IEnergyStorage[] faces=new IEnergyStorage[6];
    public NativePowerPortEntity(BlockPos pos,BlockState state){super(pos,state,NativePortKind.POWER);for(int i=0;i<6;i++)faces[i]=new Face(i);}
    public int stored(){return energy;}public IEnergyStorage energy(Direction side){return side!=null&&settings().face(side.get3DDataValue())!=com.vandorlabs.ports.ResourcePortSettings.Face.DISABLED?faces[side.get3DDataValue()]:null;}
    @Override protected int storedUnits(){return energy;}
    @Override protected Object resolve(BlockPos pos,Direction face){return level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,face);}
    @Override protected boolean compatible(Object raw,boolean receiving){return raw instanceof IEnergyStorage handler&&(receiving?handler.canReceive():handler.canExtract());}
    @Override protected int simulateCapacity(int side,int max){var raw=neighbor(side);return compatible(raw,true)?Math.min(max,Math.max(0,((IEnergyStorage)raw).receiveEnergy(max,true))):0;}
    @Override protected void transferTick(){
        if(settings().pullInputs&&energy<CAPACITY)for(int i=0;i<6&&energy<CAPACITY;i++)if(input(i)){var raw=neighbor(i);if(!compatible(raw,false))continue;var source=(IEnergyStorage)raw;int offer=Math.min(CAPACITY-energy,Math.max(0,source.extractEnergy(CAPACITY-energy,true)));if(offer<=0)continue;int count=Math.min(offer,Math.max(0,source.extractEnergy(offer,false)));if(count>0){energy+=count;transferred(i,count,false);}}
        if(energy==0||availableOutput()==0)return;int offer=boundedOutput(energy);for(int i=0;i<6;i++){capacities[i]=0;if(output(i)){var raw=neighbor(i);if(compatible(raw,true))capacities[i]=Math.min(offer,Math.max(0,((IEnergyStorage)raw).receiveEnergy(offer,true)));}}allocate(energy);
        for(int i=0;i<6;i++)if(grants[i]>0&&energy>0){var raw=neighbor(i);if(!compatible(raw,true))continue;int count=(int)Math.min(grants[i],boundedOutput(energy));if(count<=0)continue;int accepted=Math.min(count,Math.max(0,((IEnergyStorage)raw).receiveEnergy(count,false)));if(accepted>0){energy-=accepted;transferred(i,accepted,true);}}
    }
    @Override protected void writeContents(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("PortEnergy",energy);}
    @Override protected void readContents(CompoundTag tag,HolderLookup.Provider registries){if(tag.contains("PortEnergy")&&!tag.contains("PortEnergy",net.minecraft.nbt.Tag.TAG_INT))throw new IllegalArgumentException("Invalid power port buffer type");int stored=tag.getInt("PortEnergy");if(stored<0||stored>CAPACITY)throw new IllegalArgumentException("Invalid power port buffer");energy=stored;}
    private final class Face implements IEnergyStorage {
        final int side;Face(int side){this.side=side;}
        public int receiveEnergy(int max,boolean simulate){if(!input(side)||!transferAllowed())return 0;int count=Math.min(Math.max(0,max),CAPACITY-energy);if(!simulate&&count>0){energy+=count;transferred(side,count,false);}return count;}
        public int extractEnergy(int max,boolean simulate){if(max<=0||!output(side)||!transferAllowed())return 0;int count=Math.min(energy,boundedOutput(side,max));if(!simulate&&count>0){energy-=count;transferred(side,count,true);}return count;}
        public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return CAPACITY;}public boolean canExtract(){return output(side);}public boolean canReceive(){return input(side);}
    }
}
