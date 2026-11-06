package com.vandorlabs.neoforge.fluid;

import net.neoforged.neoforge.fluids.FluidStack;

/** Owning-thread preparation of an immutable opening render descriptor; readers never query a level. */
public final class NativeOpeningFluidRegions {
    private record Key(double x,double y,double z,double near,double far,boolean open,int present,boolean joined,double firstHeight,double secondHeight) {}
    private Key key;
    private NativeFluidSlots.Snapshot fluids;
    private int present;
    private boolean same;
    private volatile NativeFluidRegions regions=NativeFluidRegions.empty();
    public NativeFluidRegions snapshot(){return regions;}
    public void clear(){key=null;fluids=null;regions=NativeFluidRegions.empty();}
    /** Source SpaceDoor top clearance: native fluid ignores components, contained coverage requires them. */
    public void prepareDoor(ContainedFluidHost owner,double x,double z,double near,double far,boolean open,NativeFluidSlots.Snapshot snapshot){
        double first=.999,second=.999;var level=owner.fluidOwner().getLevel();var above=owner.fluidOwner().getBlockPos().above();
        if(level!=null&&(snapshot.side(0).fluidType()!=null||snapshot.side(1).fluidType()!=null)&&NativeFluidWorld.loaded(level,above)){
            var nativeFluid=NativeFluidWorld.fromState(level.getFluidState(above));var tile=level.getBlockEntity(above);var adjacent=tile instanceof ContainedFluidHost host?host.effectiveFluidSnapshot():null;
            if(covered(snapshot.side(0).fluid(),nativeFluid,adjacent))first=1;
            if(covered(snapshot.side(1).fluid(),nativeFluid,adjacent))second=1;
        }
        prepare(x,0D,z,near,far,open,snapshot,first,second);
    }
    private static boolean covered(FluidStack own,FluidStack nativeFluid,NativeFluidSlots.Snapshot adjacent){
        return !own.isEmpty()&&(!nativeFluid.isEmpty()&&own.getFluid()==nativeFluid.getFluid()||adjacent!=null&&(FluidStack.isSameFluidSameComponents(own,adjacent.side(0).fluid())||FluidStack.isSameFluidSameComponents(own,adjacent.side(1).fluid())));
    }
    public void prepare(double x,double y,double z,double near,double far,boolean open,NativeFluidSlots.Snapshot snapshot,double firstHeight,double secondHeight){
        if(fluids!=snapshot){fluids=snapshot;var first=snapshot.side(0).fluid();var second=snapshot.side(1).fluid();present=(first.isEmpty()?0:1)|(second.isEmpty()?0:2);same=present==3&&FluidStack.isSameFluidSameComponents(first,second);}
        var next=new Key(x,y,z,near,far,open,present,open&&same,firstHeight,secondHeight);
        if(next.equals(key))return;
        var prepared=NativeFluidRegions.opening(x,y,z,near,far,open,present,open&&same,firstHeight,secondHeight);
        key=next;regions=prepared;
    }
}
