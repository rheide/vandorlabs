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
    public enum Action { SURFACE, BOARD, DOOR, CANOPY, SCREEN }

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
                    : VehicleCanopies.supports(cell.state.getBlock()) ? Action.CANOPY
                    : VehicleDoors.supports(cell.state.getBlock())
                    ? Action.DOOR : (cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockVandorSwitch || cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockIndustrialLever || com.vandorlabs.blocks.RedstoneScreenInteractions.supports(cell.state.getBlock())) ? Action.SCREEN : Action.SURFACE;
        }
    }

    /** All captured cells participate, so a nearer wall occludes a seat or door. */
    public static Hit local(VehicleStructure structure, World view, Vec3d start, Vec3d end, double limitSq) {
        return local(structure,view,start,end,limitSq,null);
    }
    public static Hit local(VehicleStructure structure, World view, Vec3d start, Vec3d end, double limitSq,BlockPos riderSeat) {
        if (structure == null || view == null || !finite(start) || !finite(end)
                || !Double.isFinite(limitSq) || limitSq < 0) return null;
        Hit nearest = null;
        java.util.Set<BlockPos> canopies=new java.util.HashSet<>();
        for (VehicleStructure.Cell cell : structure.cells) {
            // The occupied seat surrounds the pilot's eyes; its selection box must
            // not swallow clicks aimed at the canopy, doors or controls above it.
            if(riderSeat!=null && cell.state.getBlock() instanceof BlockPilotSeat
                    && (cell.pos.equals(riderSeat) || cell.pos.equals(riderSeat.up())))continue;
            VehicleStructure.Cell selected=cell;
            IBlockState state = view.getBlockState(cell.pos);
            if(state.getBlock() instanceof com.vandorlabs.blocks.BlockCanopy){
                com.vandorlabs.tiles.TileEntityCanopy tile=com.vandorlabs.tiles.TileEntityCanopy.at(view,cell.pos);
                if(tile==null)continue;tile=tile.owner();
                if(!tile.draws() || !canopies.add(tile.getPos()))continue;
                selected=structure.at(tile.getPos());if(selected==null)continue;state=view.getBlockState(selected.pos);
            }
            RayTraceResult hit;
            try {
                hit = state.collisionRayTrace(view, selected.pos, start, end);
            } catch (RuntimeException | LinkageError error) {
                VehicleCompatibility.warn(state.getBlock(), "selection", error);
                // Unknown blocks with unavailable selection callbacks retain bounded cell targeting.
                hit = new AxisAlignedBB(selected.pos).calculateIntercept(start, end);
                if (hit != null) hit = new RayTraceResult(hit.hitVec, hit.sideHit, selected.pos);
            }
            if (hit == null || !finite(hit.hitVec)) continue;
            double distance = start.squareDistanceTo(hit.hitVec);
            // Terrain at the same distance wins, preventing clicks through a touching wall.
            if (distance < limitSq && (nearest == null || distance < nearest.distanceSq))
                nearest = new Hit(selected, hit, distance);
        }
        return nearest;
    }

    /** Caller supplies terrain/nearer-craft distance; no enclosing-box target is used. */
    public static Hit trace(EntityGroundVehicle craft, Vec3d start, Vec3d end, double limitSq) {
        return trace(craft,start,end,limitSq,false);
    }
    public static Hit trace(EntityGroundVehicle craft, Vec3d start, Vec3d end, double limitSq,boolean rider) {
        if (craft == null || craft.isDead || craft.structure == null || !finite(start) || !finite(end)) return null;
        return local(craft.structure, craft.view, craft.toLocal(start), craft.toLocal(end), limitSq,rider?craft.structure.seat:null);
    }

    private static boolean finite(Vec3d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
