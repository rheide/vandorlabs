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
    public void prepare(double x,double y,double z,double near,double far,boolean open,NativeFluidSlots.Snapshot snapshot,double firstHeight,double secondHeight){
        if(fluids!=snapshot){fluids=snapshot;var first=snapshot.side(0).fluid();var second=snapshot.side(1).fluid();present=(first.isEmpty()?0:1)|(second.isEmpty()?0:2);same=present==3&&FluidStack.isSameFluidSameComponents(first,second);}
        var next=new Key(x,y,z,near,far,open,present,open&&same,firstHeight,secondHeight);
        if(next.equals(key))return;
        var prepared=NativeFluidRegions.opening(x,y,z,near,far,open,present,open&&same,firstHeight,secondHeight);
        key=next;regions=prepared;
    }
}
