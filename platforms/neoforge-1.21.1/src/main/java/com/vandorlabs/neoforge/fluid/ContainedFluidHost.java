package com.vandorlabs.neoforge.fluid;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Platform host boundary: geometry snapshots contain no level references. */
public interface ContainedFluidHost {
    /** Sampled only on the owning thread; lighting workers read the auxiliary manager. */
    default int intrinsicContainedLight(){return 0;}
    default boolean openingFluidVolume(){return false;}
    default net.minecraft.core.Direction[] fluidPlacementFaces(){return new net.minecraft.core.Direction[0];}
    /** Optional continuous render planes; physical contacts still use the volume snapshot. */
    default NativeFluidRegions fluidSurfaceRegions(){return null;}
    /** Placement availability is distinct from explicit bucket availability. */
    default boolean acceptsPlacementFluid(int side){
        return openingFluidVolume()||fluidVolumeSnapshot().hasSide(side);
    }
    /** Failed retained snapshots may remain installed without allowing bucket or sponge mutation. */
    default boolean fluidActionsAllowed(){return true;}
    NativeFluidRenderSnapshot fluidRenderSnapshot();
    void publishFluidRenderSnapshot(NativeFluidRenderSnapshot snapshot);
    BlockEntity fluidOwner();
    NativeFluidSlots containedFluids();
    /** Derived fluid presence for rendering/contact; ownership, buckets and saving always use containedFluids. */
    default NativeFluidSlots.Snapshot effectiveFluidSnapshot(){return containedFluids().snapshot();}
    NativeFluidVolume fluidVolumeSnapshot();
    long fluidGeometryRevision();
    /** Cosmetic snapshot publication is independent of physical/fluid neighbor changes. */
    default long modelDataRevision(){return fluidGeometryRevision();}
    /** Deferred owning-thread rebuild, after loaded identity is checked. */
    void refreshFluidGeometry();
    int containedFluidSide(double x,double y,double z);
    /** Host marks persistence/model data dirty; lifecycle publication remains deferred. */
    void containedFluidsChanged();
}
