package com.vandorlabs.neoforge.fluid;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Platform host boundary: geometry snapshots contain no level references. */
public interface ContainedFluidHost {
    /** Sampled only on the owning thread; lighting workers read the auxiliary manager. */
    default int intrinsicContainedLight(){return 0;}
    default boolean openingFluidVolume(){return false;}
    default net.minecraft.core.Direction[] fluidPlacementFaces(){return new net.minecraft.core.Direction[0];}
    NativeFluidRenderSnapshot fluidRenderSnapshot();
    void publishFluidRenderSnapshot(NativeFluidRenderSnapshot snapshot);
    BlockEntity fluidOwner();
    NativeFluidSlots containedFluids();
    NativeFluidVolume fluidVolumeSnapshot();
    long fluidGeometryRevision();
    /** Deferred owning-thread rebuild, after loaded identity is checked. */
    void refreshFluidGeometry();
    int containedFluidSide(double x,double y,double z);
    /** Host marks persistence/model data dirty; lifecycle publication remains deferred. */
    void containedFluidsChanged();
}
