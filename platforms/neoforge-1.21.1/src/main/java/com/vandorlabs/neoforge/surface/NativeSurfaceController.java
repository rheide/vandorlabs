package com.vandorlabs.neoforge.surface;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Shared native claim contract; drawing artwork remains owned by each Surface. */
public interface NativeSurfaceController {
    UUID surfaceKey();
    boolean ownsSurface(BlockPos cell);
    NativeSurfaceLayout surfaceLayout(BlockPos cell);
    boolean surfaceActive(BlockPos cell);
    boolean surfaceLocked();
    void surfaceChanged();
    default boolean joinsArtwork(){return true;}
    default boolean joinsArtwork(BlockPos first,BlockPos next){return true;}
    default boolean surfaceSeed(BlockPos cell){return false;}
    default boolean acceptsExtension(BlockPos existing,BlockPos added){return true;}
}
