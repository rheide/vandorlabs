package com.vandorlabs.blocks;

import net.minecraft.util.math.AxisAlignedBB;

/** Native AABB adapter for the shared authored detent bounds. */
final class SignalControlBounds {
    static AxisAlignedBB get(int detail,String kind,String mount){com.vandorlabs.render.ControlMount.Box box=com.vandorlabs.render.SignalControlGeometry.rawBounds(detail,kind,mount);return new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    private SignalControlBounds(){}
}
