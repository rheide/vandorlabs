package com.vandorlabs.neoforge.ports;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class NativePowerPortEntity extends NativeResourcePortEntity {
    public static final int CAPACITY=1000000;
    private int energy;
    private final NativePowerTransfer transfer;
    public NativePowerPortEntity(BlockPos pos,BlockState state){super(pos,state,NativePortKind.POWER);transfer=new NativePowerTransfer(runtime(),new NativePowerTransfer.Buffer(){public int amount(){return energy;}public int capacity(){return CAPACITY;}public int legacyRate(){return 0;}public boolean inert(){return NativePowerPortEntity.this.inert();}public void change(int delta){energy+=delta;}},capacities,grants);}
    public int stored(){return energy;}
    public IEnergyStorage energy(Direction side){return transfer.energy(side,false);}
    @Override protected int storedUnits(){return energy;}
    @Override protected Object resolve(BlockPos pos,Direction face){return level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,face);}
    @Override protected boolean compatible(Object raw,boolean receiving){return NativePowerTransfer.compatible(raw,receiving);}
    @Override protected int simulateCapacity(int side,int max){return transfer.simulateCapacity(side,max);}
    @Override protected void transferTick(){transfer.tick();}
    @Override protected void writeContents(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("PortEnergy",energy);}
    @Override protected void readContents(CompoundTag tag,HolderLookup.Provider registries){if(tag.contains("PortEnergy")&&!tag.contains("PortEnergy",net.minecraft.nbt.Tag.TAG_INT))throw new IllegalArgumentException("Invalid power port buffer type");int stored=tag.getInt("PortEnergy");if(stored<0||stored>CAPACITY)throw new IllegalArgumentException("Invalid power port buffer");energy=stored;}
}
