package com.vandorlabs.blocks;

import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmGeometry.Box;
import com.vandorlabs.display.DisplayArmGeometry.Vec;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact component picking and small collision slices, cached with the tile's pose. */
public final class DisplayArmShape {
    public final DisplayArmGeometry geometry;
    public final AxisAlignedBB bounds;
    public final List<AxisAlignedBB> collision;

    public DisplayArmShape(DisplayArmGeometry geometry) {
        this.geometry = geometry;
        DisplayArmGeometry.Bounds b = geometry.bounds;
        bounds = new AxisAlignedBB(b.minX / 16, b.minY / 16, b.minZ / 16,
                b.maxX / 16, b.maxY / 16, b.maxZ / 16);
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (Box part : geometry.boxes) {
            if (part.kind == DisplayArmGeometry.DETAIL) continue;
            int nx = slices(part.halfX), ny = slices(part.halfY), nz = slices(part.halfZ);
            for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
                double hx = part.halfX / nx, hy = part.halfY / ny, hz = part.halfZ / nz;
                Vec center = part.point(-part.halfX + (2 * x + 1) * hx,
                        -part.halfY + (2 * y + 1) * hy, -part.halfZ + (2 * z + 1) * hz);
                double ex = Math.abs(part.xAxis.x) * hx + Math.abs(part.yAxis.x) * hy + Math.abs(part.zAxis.x) * hz;
                double ey = Math.abs(part.xAxis.y) * hx + Math.abs(part.yAxis.y) * hy + Math.abs(part.zAxis.y) * hz;
                double ez = Math.abs(part.xAxis.z) * hx + Math.abs(part.yAxis.z) * hy + Math.abs(part.zAxis.z) * hz;
                boxes.add(new AxisAlignedBB((center.x - ex) / 16, (center.y - ey) / 16, (center.z - ez) / 16,
                        (center.x + ex) / 16, (center.y + ey) / 16, (center.z + ez) / 16));
            }
        }
        collision = Collections.unmodifiableList(boxes);
    }

    private static int slices(double halfSize) { return Math.max(1, (int) Math.ceil(halfSize / 2)); }

    public void addCollisions(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> boxes) {
        if (!bounds.offset(pos).intersects(query)) return;
        for (AxisAlignedBB local : collision) {
            AxisAlignedBB placed = local.offset(pos);
            if (placed.intersects(query) && !boxes.contains(placed)) boxes.add(placed);
        }
    }

    public RayTraceResult trace(BlockPos pos, Vec3d start, Vec3d end) {
        Vec a = new Vec((start.x - pos.getX()) * 16, (start.y - pos.getY()) * 16,
                (start.z - pos.getZ()) * 16);
        Vec b = new Vec((end.x - pos.getX()) * 16, (end.y - pos.getY()) * 16,
                (end.z - pos.getZ()) * 16);
        double nearest = Double.POSITIVE_INFINITY;
        Box selected = null;
        for (Box part : geometry.boxes) {
            double t = part.ray(a, b);
            if (!Double.isNaN(t) && t < nearest) { nearest = t; selected = part; }
        }
        if (selected == null) return null;
        Vec local = selected.inverse(a.add(b.subtract(a).scale(nearest)));
        double dx = Math.abs(Math.abs(local.x) - selected.halfX);
        double dy = Math.abs(Math.abs(local.y) - selected.halfY);
        double dz = Math.abs(Math.abs(local.z) - selected.halfZ);
        Vec normal = dx <= dy && dx <= dz ? selected.xAxis.scale(Math.signum(local.x))
                : dy <= dz ? selected.yAxis.scale(Math.signum(local.y))
                : selected.zAxis.scale(Math.signum(local.z));
        EnumFacing face = EnumFacing.getFacingFromVector((float) normal.x, (float) normal.y, (float) normal.z);
        return new RayTraceResult(start.add(end.subtract(start).scale(nearest)), face, pos);
    }
}
