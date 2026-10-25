package com.vandorlabs.surface;

import java.util.UUID;
import net.minecraft.util.math.BlockPos;

/** Explicit ownership lets different controllers share pad blocks without overlapping claims. */
public interface SurfaceController {
    UUID surfaceKey();
    boolean ownsSurface(BlockPos pos);
    SquareLayout surfaceLayout(BlockPos pos);
    boolean surfaceActive(BlockPos pos);
    boolean surfaceLocked();
    void surfaceChanged();
}
