package com.vandorlabs.turntable;

import com.vandorlabs.turntable.TurntableCollisionGeometry.Box;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/** The Forge adapter for shared Turntable collision geometry. */
public final class TurntableCollision {
    public static boolean intersects(AxisAlignedBB source, Vec3d pivot, TurntableGeometry.Face face, double angle,
                                     AxisAlignedBB obstacle) {
        return TurntableCollisionGeometry.intersects(box(source), pivot.x, pivot.y, pivot.z, face, angle, box(obstacle));
    }

    public static List<AxisAlignedBB> boxes(AxisAlignedBB source, Vec3d pivot, TurntableGeometry.Face face, double angle,
                                            AxisAlignedBB query) {
        List<Box> strips = TurntableCollisionGeometry.strips(box(source), pivot.x, pivot.y, pivot.z, face, angle, box(query));
        List<AxisAlignedBB> result = new ArrayList<>(strips.size());
        for (Box strip : strips) {
            result.add(new AxisAlignedBB(strip.minX, strip.minY, strip.minZ, strip.maxX, strip.maxY, strip.maxZ));
        }
        return result;
    }

    private static Box box(AxisAlignedBB bounds) {
        return new Box(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
    }

    private TurntableCollision() {}
}
