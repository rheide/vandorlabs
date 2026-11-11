package com.vandorlabs.blocks;

import java.util.List;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Immutable broad phase for a native door's exact, ordered collision boxes. */
public final class DoorCollisionIndex {
    private static final int MIN_BOXES = 32, LEAF_BOXES = 8;
    private final AxisAlignedBB[] boxes;
    // Preorder nodes: six bounds, then first box, exclusive end and next sibling.
    // Contiguous source ranges preserve collision-list order and ray-hit ties.
    private final double[] bounds;
    private final int[] ranges;

    public DoorCollisionIndex(List<AxisAlignedBB> source) {
        boxes = source.toArray(new AxisAlignedBB[0]);
        boolean nativeBoxes = boxes.length >= MIN_BOXES;
        for (AxisAlignedBB box : boxes) nativeBoxes &= box.getClass() == AxisAlignedBB.class
                && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
        if (!nativeBoxes) { bounds = null; ranges = null; return; }
        int nodes = nodes(boxes.length);
        bounds = new double[nodes * 6]; ranges = new int[nodes * 3];
        build(0, 0, boxes.length);
    }

    private static int nodes(int count) {
        if (count <= LEAF_BOXES) return 1;
        return 1 + nodes(count / 2) + nodes(count - count / 2);
    }
    private int build(int node, int first, int end) {
        AxisAlignedBB firstBox = boxes[first]; int b = node * 6, r = node * 3;
        double minX = firstBox.minX, minY = firstBox.minY, minZ = firstBox.minZ;
        double maxX = firstBox.maxX, maxY = firstBox.maxY, maxZ = firstBox.maxZ;
        for (int i = first + 1; i < end; i++) {
            AxisAlignedBB box = boxes[i];
            minX = Math.min(minX, box.minX); minY = Math.min(minY, box.minY); minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX); maxY = Math.max(maxY, box.maxY); maxZ = Math.max(maxZ, box.maxZ);
        }
        bounds[b] = minX; bounds[b + 1] = minY; bounds[b + 2] = minZ;
        bounds[b + 3] = maxX; bounds[b + 4] = maxY; bounds[b + 5] = maxZ;
        ranges[r] = first; ranges[r + 1] = end;
        int next = node + 1;
        if (end - first > LEAF_BOXES) {
            int middle = (first + end) >>> 1;
            next = build(next, first, middle); next = build(next, middle, end);
        }
        ranges[r + 2] = next;
        return next;
    }

    /** Append the exact original boxes, preserving order and duplicate suppression. */
    public void addIntersecting(AxisAlignedBB query, List<AxisAlignedBB> out) {
        if (bounds == null || covers(query.minX, query.minY, query.minZ, query.maxX, query.maxY, query.maxZ)) {
            add(0, boxes.length, query, out); return;
        }
        for (int node = 0; node < ranges.length / 3;) {
            int r = node * 3, b = node * 6;
            if (!(bounds[b] < query.maxX && bounds[b + 3] > query.minX
                    && bounds[b + 1] < query.maxY && bounds[b + 4] > query.minY
                    && bounds[b + 2] < query.maxZ && bounds[b + 5] > query.minZ)) {
                node = ranges[r + 2]; continue;
            }
            if (ranges[r + 1] - ranges[r] <= LEAF_BOXES) add(ranges[r], ranges[r + 1], query, out);
            node++;
        }
    }
    private void add(int first, int end, AxisAlignedBB query, List<AxisAlignedBB> out) {
        for (int i = first; i < end; i++) {
            AxisAlignedBB box = boxes[i];
            if (box.intersects(query) && !out.contains(box)) out.add(box);
        }
    }

    /** Run Minecraft's exact intercept calculation only on spatial candidates. */
    public RayTraceResult trace(Vec3d start, Vec3d end) {
        RayTraceResult nearest = null; double distance = Double.MAX_VALUE;
        if (bounds == null || !finite(start) || !finite(end)) return traceLinear(start, end);
        // A closed, slightly expanded segment envelope also retains grazing rays,
        // rays within a node and rounding at the endpoints of Minecraft's lerp.
        double px = padding(start.x, end.x), py = padding(start.y, end.y), pz = padding(start.z, end.z);
        double minX = Math.min(start.x, end.x) - px, maxX = Math.max(start.x, end.x) + px;
        double minY = Math.min(start.y, end.y) - py, maxY = Math.max(start.y, end.y) + py;
        double minZ = Math.min(start.z, end.z) - pz, maxZ = Math.max(start.z, end.z) + pz;
        if (covers(minX, minY, minZ, maxX, maxY, maxZ)) return traceLinear(start, end);
        for (int node = 0; node < ranges.length / 3;) {
            int r = node * 3, b = node * 6;
            if (bounds[b + 3] < minX || bounds[b] > maxX || bounds[b + 4] < minY
                    || bounds[b + 1] > maxY || bounds[b + 5] < minZ || bounds[b + 2] > maxZ) {
                node = ranges[r + 2]; continue;
            }
            if (ranges[r + 1] - ranges[r] <= LEAF_BOXES) for (int i = ranges[r]; i < ranges[r + 1]; i++) {
                RayTraceResult hit = boxes[i].calculateIntercept(start, end);
                if (hit != null && start.squareDistanceTo(hit.hitVec) < distance) {
                    nearest = hit; distance = start.squareDistanceTo(hit.hitVec);
                }
            }
            node++;
        }
        return nearest;
    }
    private RayTraceResult traceLinear(Vec3d start, Vec3d end) {
        RayTraceResult nearest = null; double distance = Double.MAX_VALUE;
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = box.calculateIntercept(start, end);
            if (hit != null && start.squareDistanceTo(hit.hitVec) < distance) {
                nearest = hit; distance = start.squareDistanceTo(hit.hitVec);
            }
        }
        return nearest;
    }
    private boolean covers(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return minX <= bounds[0] && minY <= bounds[1] && minZ <= bounds[2]
                && maxX >= bounds[3] && maxY >= bounds[4] && maxZ >= bounds[5];
    }
    private static boolean finite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static double padding(double a, double b) {
        return 1.0e-7 + 8 * Math.ulp(Math.max(Math.abs(a), Math.abs(b)));
    }
}
