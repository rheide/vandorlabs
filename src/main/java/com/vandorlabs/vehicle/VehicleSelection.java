package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Shared simulation-pose selection for validated craft interaction packets. */
public final class VehicleSelection {
    private VehicleSelection() {}
    public enum Action { SURFACE, BOARD, DOOR }

    public static final class Hit {
        public final VehicleStructure.Cell cell;
        public final RayTraceResult local;
        public final Action action;
        public final double distanceSq;
        private Hit(VehicleStructure.Cell cell, RayTraceResult local, double distanceSq) {
            this.cell = cell;
            this.local = local;
            this.distanceSq = distanceSq;
            action = cell.state.getBlock() instanceof BlockPilotSeat ? Action.BOARD
                    : VehicleDoors.supports(cell.state.getBlock())
                    ? Action.DOOR : Action.SURFACE;
        }
    }

    /** All captured cells participate, so a nearer wall occludes a seat or door. */
    public static Hit local(VehicleStructure structure, World view, Vec3d start, Vec3d end, double limitSq) {
        if (structure == null || view == null || !finite(start) || !finite(end)
                || !Double.isFinite(limitSq) || limitSq < 0) return null;
        Hit nearest = null;
        for (VehicleStructure.Cell cell : structure.cells) {
            IBlockState state = view.getBlockState(cell.pos);
            RayTraceResult hit;
            try {
                hit = state.collisionRayTrace(view, cell.pos, start, end);
            } catch (RuntimeException | LinkageError error) {
                VehicleCompatibility.warn(state.getBlock(), "selection", error);
                // Unknown blocks with unavailable selection callbacks retain bounded cell targeting.
                hit = new AxisAlignedBB(cell.pos).calculateIntercept(start, end);
                if (hit != null) hit = new RayTraceResult(hit.hitVec, hit.sideHit, cell.pos);
            }
            if (hit == null || !finite(hit.hitVec)) continue;
            double distance = start.squareDistanceTo(hit.hitVec);
            // Terrain at the same distance wins, preventing clicks through a touching wall.
            if (distance < limitSq && (nearest == null || distance < nearest.distanceSq))
                nearest = new Hit(cell, hit, distance);
        }
        return nearest;
    }

    /** Caller supplies terrain/nearer-craft distance; no enclosing-box target is used. */
    public static Hit trace(EntityGroundVehicle craft, Vec3d start, Vec3d end, double limitSq) {
        if (craft == null || craft.isDead || craft.structure == null || !finite(start) || !finite(end)) return null;
        return local(craft.structure, craft.view, craft.toLocal(start), craft.toLocal(end), limitSq);
    }

    private static boolean finite(Vec3d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
