package com.vandorlabs.neoforge.fluid;

import com.vandorlabs.fluids.FluidVolume;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A contained-fluid family supplies collision-free geometry without exposing its world adapter. */
public interface ContainedFluidHost {
    BlockEntity fluidOwner();
    NativeContainedFluids containedFluids();
    /** Immutable, previously published geometry; safe to read without querying neighbours. */
    FluidVolume fluidVolumeSnapshot();
    long fluidGeometryRevision();
    /** Refresh connected geometry on the level thread before publishing a fluid snapshot. */
    default void refreshFluidGeometry(){}
    /** Stable side classification, including points on the solid leaf or just outside the cell. */
    default int containedFluidSide(double localX,double localY,double localZ){return 0;}
    /** Panels inspect only the two boundary neighbours; slabs/stairs use the replaced fluid. */
    default Direction[] fluidPlacementFaces(){return new Direction[0];}
    default boolean openingFluidVolume(){return false;}
    /** Called on the authoritative main thread after a bucket or placement mutation. */
    default void containedFluidsChanged(){
        BlockEntity owner=fluidOwner();owner.setChanged();owner.requestModelDataUpdate();
        var level=owner.getLevel();if(level!=null)level.sendBlockUpdated(owner.getBlockPos(),owner.getBlockState(),owner.getBlockState(),3);
    }
}
