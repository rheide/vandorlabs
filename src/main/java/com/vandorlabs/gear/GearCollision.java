package com.vandorlabs.gear;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * Conservative deployed-pose boxes around the two angled native arm bars.
 * Minecraft 1.12 accepts axis-aligned collision boxes, so each bar is split
 * in two before taking its bounds. These boxes are never used during motion.
 */
final class GearCollision {
    private static final AxisAlignedBB[][] ARM_CELL = new AxisAlignedBB[4][];
    private static final AxisAlignedBB[][] FOOT_CELL = new AxisAlignedBB[4][];

    static {
        double angle = Math.PI / 8;
        double length = 10 / Math.cos(angle);
        double kneeZ = 8 + 10 * Math.tan(angle);
        List<AxisAlignedBB> nativeBars = new ArrayList<AxisAlignedBB>();
        for (int half = 0; half < 2; half++) {
            nativeBars.add(rotatedBar(9 - length * (half + 1) / 2, 9 - length * half / 2,
                    6, 10, 9, 8, -angle));
            nativeBars.add(rotatedBar(-1 - length * (half + 1) / 2, -1 - length * half / 2,
                    kneeZ - 2, kneeZ + 2, -1, kneeZ, angle));
        }
        nativeBars.add(new AxisAlignedBB(5.0 / 16, -2.5 / 16, (kneeZ - 1.5) / 16,
                11.0 / 16, 0.5 / 16, (kneeZ + 1.5) / 16));
        for (int turns = 0; turns < 4; turns++) {
            List<AxisAlignedBB> upper = new ArrayList<AxisAlignedBB>();
            List<AxisAlignedBB> lower = new ArrayList<AxisAlignedBB>();
            for (AxisAlignedBB raw : nativeBars) {
                AxisAlignedBB box = yaw(raw, turns);
                clipY(box, 0, 1, 0, upper);
                clipY(box, -1, 0, 1, lower);
            }
            lower.add(yaw(new AxisAlignedBB(4.0 / 16, 3.0 / 16, 5.0 / 16,
                    12.0 / 16, 7.0 / 16, 11.0 / 16), turns));
            lower.add(yaw(new AxisAlignedBB(3.0 / 16, 4.0 / 16, 6.0 / 16,
                    13.0 / 16, 6.0 / 16, 10.0 / 16), turns));
            ARM_CELL[turns] = upper.toArray(new AxisAlignedBB[upper.size()]);
            FOOT_CELL[turns] = lower.toArray(new AxisAlignedBB[lower.size()]);
        }
    }

    private GearCollision() { }

    static int quarterTurns(EnumFacing facing) {
        switch (facing) {
            case EAST: return 1;
            case SOUTH: return 2;
            case WEST: return 3;
            default: return 0;
        }
    }

    static void addSwingArm(BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collisions,
                            int turns, boolean footCell) {
        for (AxisAlignedBB local : (footCell ? FOOT_CELL : ARM_CELL)[turns & 3]) {
            AxisAlignedBB box = local.offset(pos);
            if (box.intersects(entityBox)) collisions.add(box);
        }
    }

    private static void clipY(AxisAlignedBB box, double low, double high, double offset,
                              List<AxisAlignedBB> output) {
        double min = Math.max(low, box.minY);
        double max = Math.min(high, box.maxY);
        if (min < max) output.add(new AxisAlignedBB(box.minX, min + offset, box.minZ,
                box.maxX, max + offset, box.maxZ));
    }

    private static AxisAlignedBB rotatedBar(double minY, double maxY, double minZ, double maxZ,
                                            double pivotY, double pivotZ, double radians) {
        double loY = Double.POSITIVE_INFINITY, hiY = Double.NEGATIVE_INFINITY;
        double loZ = Double.POSITIVE_INFINITY, hiZ = Double.NEGATIVE_INFINITY;
        double cosine = Math.cos(radians), sine = Math.sin(radians);
        for (int corner = 0; corner < 4; corner++) {
            double y = ((corner & 1) == 0 ? minY : maxY) - pivotY;
            double z = ((corner & 2) == 0 ? minZ : maxZ) - pivotZ;
            double ry = pivotY + y * cosine - z * sine;
            double rz = pivotZ + y * sine + z * cosine;
            loY = Math.min(loY, ry); hiY = Math.max(hiY, ry);
            loZ = Math.min(loZ, rz); hiZ = Math.max(hiZ, rz);
        }
        return new AxisAlignedBB(6.0 / 16, loY / 16, loZ / 16,
                10.0 / 16, hiY / 16, hiZ / 16);
    }

    private static AxisAlignedBB yaw(AxisAlignedBB box, int turns) {
        switch (turns & 3) {
            case 1: return new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX);
            case 2: return new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                    1 - box.minX, box.maxY, 1 - box.minZ);
            case 3: return new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                    box.maxZ, box.maxY, 1 - box.minX);
            default: return box;
        }
    }
}
