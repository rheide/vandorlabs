package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidRegions;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Immutable wall-family publication. All methods are world-free and safe for section workers. */
public interface NativeWallGeometry {
    List<AABB> boxes();
    default List<AABB> fluidBoxes(){return boxes();}
    /** Cosmetic layout changes must not invalidate the physical fluid volume. */
    default boolean sameFluidGeometry(NativeWallGeometry other){return equals(other);}
    AABB bounds();
    Direction facing();
    int depth();
    NativePanelLayout layout();
    NativeWallGeometry withLayout(NativePanelLayout layout);
    int side(double x,double y,double z);
    NativeFluidRegions fluidRegions(boolean porthole);
    default AABB selectionBounds(){return bounds();}
    default AABB renderBounds(){return bounds().minmax(selectionBounds());}
    default boolean diagonal(){return false;}
    default boolean fullDepth(){return false;}
    default Direction[] placementFaces(){return new Direction[]{facing(),facing().getOpposite()};}
}
