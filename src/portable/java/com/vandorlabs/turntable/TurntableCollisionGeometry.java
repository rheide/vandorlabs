package com.vandorlabs.turntable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact cardinal-axis terrain checks and narrow AABB strips for actor collision. */
public final class TurntableCollisionGeometry {
    private static final double STEP = 1.0 / 32;

    public static final class Box {
        public final double minX, minY, minZ, maxX, maxY, maxZ;

        public Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean intersects(Box box) {
            return minX < box.maxX && maxX > box.minX && minY < box.maxY && maxY > box.minY
                    && minZ < box.maxZ && maxZ > box.minZ;
        }
    }

    public static Box bounds(Box source, double x, double y, double z, TurntableGeometry.Face face, double angle) {
        double[] bounds = TurntableGeometry.bounds(new double[]{source.minX - x, source.minY - y, source.minZ - z,
                source.maxX - x, source.maxY - y, source.maxZ - z}, face, angle);
        return new Box(bounds[0] + x, bounds[1] + y, bounds[2] + z, bounds[3] + x, bounds[4] + y, bounds[5] + z);
    }

    /** Exact separating-axis test for a cardinal-axis rotated box against terrain. */
    public static boolean intersects(Box source, double x, double y, double z, TurntableGeometry.Face face,
                                     double angle, Box obstacle) {
        double centerX = (source.minX + source.maxX) * .5;
        double centerY = (source.minY + source.maxY) * .5;
        double centerZ = (source.minZ + source.maxZ) * .5;
        double[] rotated = TurntableGeometry.rotate(centerX - x, centerY - y, centerZ - z, face, angle);
        double[] delta = {rotated[0] + x - (obstacle.minX + obstacle.maxX) * .5,
                rotated[1] + y - (obstacle.minY + obstacle.maxY) * .5,
                rotated[2] + z - (obstacle.minZ + obstacle.maxZ) * .5};
        double[] half = {(source.maxX - source.minX) / 2, (source.maxY - source.minY) / 2, (source.maxZ - source.minZ) / 2};
        double[] other = {(obstacle.maxX - obstacle.minX) / 2,
                (obstacle.maxY - obstacle.minY) / 2, (obstacle.maxZ - obstacle.minZ) / 2};
        double[][] axes = new double[3][];
        for (int i = 0; i < 3; i++) axes[i] = TurntableGeometry.rotate(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0, face, angle);

        for (int i = 0; i < 6; i++) {
            double[] axis = i < 3 ? new double[]{i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0} : axes[i - 3];
            double distance = 0;
            double radius = 0;
            for (int j = 0; j < 3; j++) {
                distance += delta[j] * axis[j];
                radius += other[j] * Math.abs(axis[j]);
                double dot = 0;
                for (int k = 0; k < 3; k++) dot += axes[j][k] * axis[k];
                radius += half[j] * Math.abs(dot);
            }
            if (Math.abs(distance) >= radius - 1e-7) return false;
        }
        return true;
    }

    public static List<Box> strips(Box source, double x, double y, double z, TurntableGeometry.Face face,
                                   double angle, Box query) {
        Box broad = bounds(source, x, y, z, face, angle);
        if (!broad.intersects(query)) return Collections.emptyList();
        if (Math.abs(angle / 90 - Math.rint(angle / 90)) < 1e-8) return Collections.singletonList(broad);

        double[] lo = {source.minX, source.minY, source.minZ}, hi = {source.maxX, source.maxY, source.maxZ};
        double[] min = {broad.minX, broad.minY, broad.minZ}, max = {broad.maxX, broad.maxY, broad.maxZ};
        int a = (face.axis() + 1) % 3, b = (face.axis() + 2) % 3;
        if (max[a] - min[a] > max[b] - min[b]) {
            int exchange = a;
            a = b;
            b = exchange;
        }
        double[] pivot = {x, y, z};
        List<double[]> polygon = new ArrayList<>();
        for (int corner : new int[]{0, 1, 3, 2}) {
            double[] point = lo.clone();
            point[a] = (corner & 1) == 0 ? lo[a] : hi[a];
            point[b] = (corner & 2) == 0 ? lo[b] : hi[b];
            double[] rotated = TurntableGeometry.rotate(point[0] - x, point[1] - y, point[2] - z, face, angle);
            polygon.add(new double[]{rotated[a] + pivot[a], rotated[b] + pivot[b]});
        }
        double[] qMin = {query.minX, query.minY, query.minZ}, qMax = {query.maxX, query.maxY, query.maxZ};
        double first = Math.floor(Math.max(min[a], qMin[a]) / STEP) * STEP;
        double last = Math.min(max[a], qMax[a]);
        List<Box> result = new ArrayList<>();
        for (double start = first; start < last; start += STEP) {
            double low = Math.max(start, min[a]), high = Math.min(start + STEP, max[a]);
            List<double[]> clipped = clip(clip(polygon, low, true), high, false);
            if (clipped.isEmpty() || high - low < 1e-10) continue;
            double bottom = Double.POSITIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
            for (double[] point : clipped) {
                bottom = Math.min(bottom, point[1]);
                top = Math.max(top, point[1]);
            }
            double[] stripMin = lo.clone(), stripMax = hi.clone();
            stripMin[a] = low; stripMax[a] = high;
            stripMin[b] = bottom; stripMax[b] = top;
            Box strip = new Box(stripMin[0], stripMin[1], stripMin[2], stripMax[0], stripMax[1], stripMax[2]);
            if (strip.intersects(query)) result.add(strip);
        }
        return result;
    }

    private static List<double[]> clip(List<double[]> polygon, double boundary, boolean greater) {
        List<double[]> result = new ArrayList<>();
        if (polygon.isEmpty()) return result;
        double[] previous = polygon.get(polygon.size() - 1);
        boolean previousInside = greater ? previous[0] >= boundary : previous[0] <= boundary;
        for (double[] point : polygon) {
            boolean inside = greater ? point[0] >= boundary : point[0] <= boundary;
            if (inside != previousInside) {
                double fraction = (boundary - previous[0]) / (point[0] - previous[0]);
                result.add(new double[]{boundary, previous[1] + fraction * (point[1] - previous[1])});
            }
            if (inside) result.add(point);
            previous = point;
            previousInside = inside;
        }
        return result;
    }

    private TurntableCollisionGeometry() {}
}
