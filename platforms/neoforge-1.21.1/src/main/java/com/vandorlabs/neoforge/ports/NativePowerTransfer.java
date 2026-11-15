package com.vandorlabs.neoforge.ports;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Shared port/Battery operations against a single host-owned energy value. */
public final class NativePowerTransfer {
    public interface Buffer {
        int amount();
        int capacity();
        int legacyRate();
        boolean inert();
        void change(int delta);
    }
    private final NativePortRuntime runtime;
    private final Buffer buffer;
    private final IEnergyStorage[] faces=new IEnergyStorage[6];
    private final IEnergyStorage unsided=new Face(-1);
    private final long[] capacities,grants;
    public NativePowerTransfer(NativePortRuntime runtime,Buffer buffer,long[] capacities,long[] grants){this.runtime=runtime;this.buffer=buffer;this.capacities=capacities;this.grants=grants;for(int i=0;i<6;i++)faces[i]=new Face(i);}
    public IEnergyStorage energy(Direction side,boolean allowUnsided){return buffer.inert()?null:side==null?(allowUnsided?unsided:null):runtime.settings().face(side.get3DDataValue())!=com.vandorlabs.ports.ResourcePortSettings.Face.DISABLED?faces[side.get3DDataValue()]:null;}
    public static boolean compatible(Object raw,boolean receiving){return raw instanceof IEnergyStorage energy&&(receiving?energy.canReceive():energy.canExtract());}
    private int legacyBound(int maximum){return buffer.legacyRate()>0?Math.min(Math.max(0,maximum),buffer.legacyRate()):Math.max(0,maximum);}
    public int simulateCapacity(int side,int maximum){var raw=runtime.neighbor(side);return compatible(raw,true)?Math.min(maximum,Math.max(0,((IEnergyStorage)raw).receiveEnergy(legacyBound(maximum),true))):0;}
    public void tick(){
        if(runtime.settings().pullInputs&&buffer.amount()<buffer.capacity())for(int i=0;i<6&&buffer.amount()<buffer.capacity();i++)if(runtime.input(i)){
            var raw=runtime.neighbor(i);if(!compatible(raw,false))continue;var source=(IEnergyStorage)raw;int room=legacyBound(buffer.capacity()-buffer.amount());int offer=Math.min(room,Math.max(0,source.extractEnergy(room,true)));if(offer<=0)continue;int count=Math.min(offer,Math.max(0,source.extractEnergy(offer,false)));if(count>0){buffer.change(count);runtime.transferred(i,count,false);}
        }
        if(buffer.amount()==0||runtime.availableOutput()==0)return;int offer=legacyBound(runtime.boundedOutput(buffer.amount()));
        for(int i=0;i<6;i++){capacities[i]=0;if(runtime.output(i)){var raw=runtime.neighbor(i);if(compatible(raw,true))capacities[i]=Math.min(offer,Math.max(0,((IEnergyStorage)raw).receiveEnergy(offer,true)));}}
        runtime.allocate(buffer.amount());
        for(int i=0;i<6;i++)if(grants[i]>0&&buffer.amount()>0){var raw=runtime.neighbor(i);if(!compatible(raw,true))continue;int count=legacyBound((int)Math.min(grants[i],runtime.boundedOutput(buffer.amount())));if(count<=0)continue;int accepted=Math.min(count,Math.max(0,((IEnergyStorage)raw).receiveEnergy(count,false)));if(accepted>0){buffer.change(-accepted);runtime.transferred(i,accepted,true);}}
    }
    private final class Face implements IEnergyStorage {
        final int side;Face(int side){this.side=side;}
        private int effective(boolean receiving){if(side>=0)return side;for(int i=0;i<6;i++)if(receiving?runtime.input(i):runtime.output(i))return i;return -1;}
        public int receiveEnergy(int max,boolean simulate){int face=effective(true);if(face<0||!runtime.input(face)||!runtime.transferAllowed())return 0;int count=Math.min(legacyBound(max),buffer.capacity()-buffer.amount());if(count>0&&!simulate){buffer.change(count);runtime.transferred(face,count,false);}return count;}
        public int extractEnergy(int max,boolean simulate){int face=effective(false);if(max<=0||face<0||!runtime.output(face)||!runtime.transferAllowed())return 0;int count=Math.min(buffer.amount(),legacyBound(runtime.boundedOutput(face,max)));if(count>0&&!simulate){buffer.change(-count);runtime.transferred(face,count,true);}return count;}
        public int getEnergyStored(){return buffer.inert()?0:buffer.amount();}public int getMaxEnergyStored(){return buffer.capacity();}
        public boolean canReceive(){return !buffer.inert()&&effective(true)>=0&&runtime.input(effective(true));}public boolean canExtract(){return !buffer.inert()&&effective(false)>=0&&runtime.output(effective(false));}
    }
}
