package com.vandorlabs.neoforge.fluid;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Platform host boundary: geometry snapshots contain no level references. */
public interface ContainedFluidHost {
    BlockEntity fluidOwner();
    NativeFluidSlots containedFluids();
    NativeFluidVolume fluidVolumeSnapshot();
    long fluidGeometryRevision();
    int containedFluidSide(double x,double y,double z);
    /** Host marks persistence/model data dirty; lifecycle publication remains deferred. */
    void containedFluidsChanged();
}
