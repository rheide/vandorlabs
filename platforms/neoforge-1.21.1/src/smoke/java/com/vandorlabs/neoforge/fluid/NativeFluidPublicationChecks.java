package com.vandorlabs.neoforge.fluid;

/** Run the production revision classifier through independent cosmetic/physical update sequences. */
public final class NativeFluidPublicationChecks {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var initial=new NativeFluidWorld.Revision(0,0,1);
        require(initial.neighborsChanged(null)&&initial.modelChanged(null),"first installed snapshot");
        var stable=new NativeFluidWorld.Revision(0,0,1);
        require(!stable.neighborsChanged(initial)&&!stable.modelChanged(initial),"unchanged empty neighbor");
        var cosmetic=new NativeFluidWorld.Revision(0,0,2);
        require(!cosmetic.neighborsChanged(stable)&&cosmetic.modelChanged(stable),"dry layout/glass/aperture update publishes without fluid neighbor cascade");
        var fluid=new NativeFluidWorld.Revision(1,0,2);
        require(fluid.neighborsChanged(cosmetic)&&!fluid.modelChanged(cosmetic),"fluid snapshot owns its model change");
        var geometry=new NativeFluidWorld.Revision(1,1,3);
        require(geometry.neighborsChanged(fluid)&&geometry.modelChanged(fluid),"derived geometry and cosmetic change");
        var physicalOnly=new NativeFluidWorld.Revision(1,2,3);
        require(physicalOnly.neighborsChanged(geometry)&&!physicalOnly.modelChanged(geometry),"physical-only volume refresh");
        require(new NativeFluidEvents.Updated(null,true).modelChanged&&!new NativeFluidEvents.Updated(null).modelChanged,"event retains explicit model revision decision");
        System.out.println("Native fluid publication PASS dry cosmetic update, stable empty neighbor, independent fluid/geometry revision and model event");
    }
}
