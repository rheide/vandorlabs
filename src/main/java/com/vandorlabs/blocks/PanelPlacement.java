package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/** Shared quarter-turn transform for panel and input collision boxes. */
public final class PanelPlacement {
    private PanelPlacement() { }
    public static AxisAlignedBB rotateFromNorth(AxisAlignedBB box, EnumFacing facing) {
        switch (facing) {
            case EAST:
                return new AxisAlignedBB(1-box.maxZ,box.minY,box.minX,1-box.minZ,box.maxY,box.maxX);
            case SOUTH:
                return new AxisAlignedBB(1-box.maxX,box.minY,1-box.maxZ,1-box.minX,box.maxY,1-box.minZ);
            case WEST:
                return new AxisAlignedBB(box.minZ,box.minY,1-box.maxX,box.maxZ,box.maxY,1-box.minX);
            default: return box;
        }
    }
}
