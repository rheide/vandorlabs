package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.turntable.TurntableCollisionGeometry;
import com.vandorlabs.turntable.TurntableCollisionGeometry.Box;
import com.vandorlabs.turntable.TurntableGeometry.Face;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Loaded controller lookup for actor collisions. Shapes are derived from frozen block state. */
public final class NativeTurntableCollisions {
    private static final int MAX_QUERY_SHAPES = 8192;
    private static volatile List<WeakReference<NativeTurntableEntity>> active = List.of();

    record Body(List<Box> boxes, double x, double y, double z, Face face, Box extent) {
        Body(List<Box> boxes, double x, double y, double z, Face face) {
            this(List.copyOf(boxes), x, y, z, face, extent(boxes, x, y, z));
        }

        private static Box extent(List<Box> boxes, double x, double y, double z) {
            Box extent = null;
            for (var part : boxes) {
                extent = extent == null ? part : new Box(Math.min(extent.minX, part.minX), Math.min(extent.minY, part.minY),
                        Math.min(extent.minZ, part.minZ), Math.max(extent.maxX, part.maxX),
                        Math.max(extent.maxY, part.maxY), Math.max(extent.maxZ, part.maxZ));
            }
            return extent == null ? new Box(x, y, z, x, y, z) : extent;
        }

        static Body client(NativeTurntableVisual view, BlockPos root) {
            var frozen = new ArrayList<Box>();
            for (var cell : view.cells()) NativeTurntableMotion.add(frozen, root.offset(cell.offset()), cell.state());
            var pivot = view.pivot();
            return new Body(frozen, root.getX() + pivot.x, root.getY() + pivot.y, root.getZ() + pivot.z,
                    Face.values()[view.normal().get3DDataValue()]);
        }

        private Box bounds(Box part, double angle) {
            return TurntableCollisionGeometry.bounds(part, x, y, z, face, angle);
        }
    }

    static synchronized void update(NativeTurntableEntity owner, boolean present) {
        var current = new ArrayList<WeakReference<NativeTurntableEntity>>();
        boolean known = false;
        for (var reference : active) {
            var candidate = reference.get();
            if (candidate == owner) known = true;
            if (candidate != null && candidate != owner) current.add(reference);
        }
        if (present) current.add(new WeakReference<>(owner));
        if (known || present || current.size() != active.size()) active = List.copyOf(current);
    }

    public static List<VoxelShape> append(Level level, AABB region, List<VoxelShape> original) {
        if (active.isEmpty()) return original;
        Box query = null;
        List<VoxelShape> combined = null;
        for (var reference : active) {
            var owner = reference.get();
            if (owner == null || owner.getLevel() != level || owner.isRemoved()) continue;
            var body = owner.body();
            if (body == null || body.boxes().isEmpty()) continue;
            if (query == null) query = NativeTurntableMotion.box(region);
            double angle = level.isClientSide() ? owner.visualAngle() : owner.positionAngle();
            if (!body.bounds(body.extent(), angle).intersects(query)) continue;
            if (combined == null) combined = new ArrayList<>(original);
            int start = combined.size();
            for (var part : body.boxes()) {
                if (!body.bounds(part, angle).intersects(query)) continue;
                var strips = TurntableCollisionGeometry.strips(part, body.x(), body.y(), body.z(), body.face(), angle, query);
                for (var strip : strips) combined.add(shape(strip));
                if (combined.size() - start > MAX_QUERY_SHAPES) {
                    combined.subList(start, combined.size()).clear();
                    combined.add(shape(body.bounds(body.extent(), angle)));
                    break;
                }
            }
        }
        return combined == null ? original : combined;
    }

    private static VoxelShape shape(Box b) {
        return Shapes.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    private NativeTurntableCollisions() {}
}
