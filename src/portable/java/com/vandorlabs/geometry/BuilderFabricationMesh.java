package com.vandorlabs.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Texture-sampled bottom-up fabrication shared by both builder renderers. */
public final class BuilderFabricationMesh {
    @FunctionalInterface
    public interface Pixels {
        int sample(double u, double v);
    }

    public static final class Triangle {
        private final double[] points, uv;
        private final int surface;
        private final Pixels pixels;

        public Triangle(double[] points, double[] uv, int surface, Pixels pixels) {
            this.points = points;
            this.uv = uv;
            this.surface = surface;
            this.pixels = pixels;
        }

        private Triangle transformed(double[] positions) {
            return new Triangle(positions, uv, surface, pixels);
        }

        private int pixel(double[] barycentric) {
            double a = barycentric[0], b = barycentric[1], c = barycentric[2];
            return pixels.sample(a * uv[0] + b * uv[2] + c * uv[4], a * uv[1] + b * uv[3] + c * uv[5]);
        }

        private double coordinate(int axis, double[] barycentric) {
            return points[axis] * barycentric[0] + points[3 + axis] * barycentric[1] + points[6 + axis] * barycentric[2];
        }
    }

    private static final class Bounds {
        final double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        final double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};

        void add(double[] points, int start) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], points[start + axis]);
                max[axis] = Math.max(max[axis], points[start + axis]);
            }
        }

        boolean valid() { return Double.isFinite(min[0]) && max[0] >= min[0]; }
        double extent(int axis) { return max[axis] - min[axis]; }
        double center(int axis) { return (min[axis] + max[axis]) / 2; }
    }

    private static final class Hit {
        final double x;
        final int delta, surface;

        Hit(double x, int delta, int surface) {
            this.x = x;
            this.delta = delta;
            this.surface = surface;
        }
    }

    private static final class Vertices {
        int[] data = new int[4096];
        int size;

        void add(double[] point, int faceColor) {
            if (size + 4 > data.length) data = Arrays.copyOf(data, data.length * 2);
            for (int axis = 0; axis < 3; axis++) data[size++] = Float.floatToRawIntBits((float) point[axis]);
            data[size++] = faceColor;
        }
    }

    private static final int[][] POSITIVE = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    private static final int[][] NEGATIVE = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    private static final int[] MATTER = {0x51c7d6, 0xb23d48, 0xa8b0b8, 0x38424c};

    public static final class Model {
        private final boolean press;
        private final int resolution, nx, ny, nz;
        private final double originX, originZ;
        public final double minX, minZ, maxX, maxZ;
        private final boolean[] solid;
        private final int[] paint, rank, order, material;
        public final long bytes;

        public Model(List<Triangle> source, boolean press, boolean module, double fixedScale) {
            this.press = press;
            boolean flatten = press && !module;
            List<Triangle> triangles = new ArrayList<>(source.size());
            Bounds bounds = new Bounds();
            for (Triangle triangle : source) {
                double[] points = triangle.points.clone();
                for (int i = 0; i < points.length; i += 3) {
                    if (flatten) {
                        double y = points[i + 1];
                        points[i + 1] = -points[i + 2];
                        points[i + 2] = y;
                    }
                    bounds.add(points, i);
                }
                triangles.add(triangle.transformed(points));
            }

            double scale = flatten ? Math.min(1, fixedScale) : 1;
            double limit = press ? 1 : 2;
            if (bounds.valid()) {
                for (int axis = 0; axis < 3; axis++)
                    scale = Math.min(scale, limit / Math.max(.0001, bounds.extent(axis)));
            }
            double width = bounds.valid() ? bounds.extent(0) * scale : 0;
            double height = bounds.valid() ? bounds.extent(1) * scale : 0;
            double depth = bounds.valid() ? bounds.extent(2) * scale : 0;
            minX = -width / 2;
            maxX = width / 2;
            minZ = -depth / 2;
            maxZ = depth / 2;
            resolution = press ? 64 : 32;
            int startX = (int) Math.floor(minX * resolution);
            int startZ = (int) Math.floor(minZ * resolution);
            originX = startX / (double) resolution;
            originZ = startZ / (double) resolution;
            nx = Math.max(1, (int) Math.ceil(maxX * resolution) - startX);
            ny = Math.max(1, (int) Math.ceil(height * resolution));
            nz = Math.max(1, (int) Math.ceil(maxZ * resolution) - startZ);
            int volume = nx * ny * nz;
            solid = new boolean[volume];
            paint = new int[volume * 6];
            rank = new int[volume];
            material = press ? null : new int[volume];
            if (bounds.valid()) {
                for (Triangle triangle : triangles) {
                    double[] points = triangle.points;
                    for (int i = 0; i < points.length; i += 3) {
                        points[i] = (points[i] - bounds.center(0)) * scale - originX;
                        points[i + 1] = (points[i + 1] - bounds.min[1]) * scale;
                        points[i + 2] = (points[i + 2] - bounds.center(2)) * scale - originZ;
                    }
                }
                fillInterior(triangles);
                paintSurfaces(triangles);
            }
            if (!press) propagateGantryMaterial();

            int count = 0;
            for (boolean occupied : solid) if (occupied) count++;
            order = new int[count];
            int at = 0;
            for (int y = 0; y < ny; y++) for (int x = 0; x < nx; x++) for (int z = 0; z < nz; z++) {
                int cell = index(x, y, z);
                if (!solid[cell]) continue;
                order[at] = cell;
                rank[cell] = ++at;
            }
            bytes = 128L + volume * (press ? 29L : 33L) + order.length * 4L + source.size() * 256L;
        }

        private void fillInterior(List<Triangle> triangles) {
            List<Hit> intersections = new ArrayList<>();
            double[] barycentric = new double[3];
            Set<Integer> surfaces = new HashSet<>();
            for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
                intersections.clear();
                double py = midpoint(y), pz = midpoint(z);
                for (Triangle triangle : triangles) {
                    double[] p = triangle.points;
                    if (!project(p, 0, py, pz, barycentric) || (triangle.pixel(barycentric) >>> 24) < 128) continue;
                    double normal = (p[4] - p[1]) * (p[8] - p[2]) - (p[5] - p[2]) * (p[7] - p[1]);
                    intersections.add(new Hit(triangle.coordinate(0, barycentric), normal < 0 ? 1 : -1, triangle.surface));
                }
                intersections.sort(Comparator.comparingDouble(hit -> hit.x));
                int winding = 0, next = 0;
                for (int x = 0; x < nx; x++) {
                    double px = midpoint(x);
                    while (next < intersections.size() && intersections.get(next).x <= px) {
                        double plane = intersections.get(next).x;
                        surfaces.clear();
                        while (next < intersections.size() && Math.abs(intersections.get(next).x - plane) < 1e-6) {
                            Hit hit = intersections.get(next++);
                            if (surfaces.add(hit.surface)) winding += hit.delta;
                        }
                    }
                    if (winding != 0) solid[index(x, y, z)] = true;
                }
            }
        }

        /** Includes thin and open faces that have no enclosed volume. */
        private void paintSurfaces(List<Triangle> triangles) {
            double[] barycentric = new double[3];
            int[] position = new int[3];
            for (Triangle triangle : triangles) {
                double[] p = triangle.points;
                double[] normal = {
                        (p[4] - p[1]) * (p[8] - p[2]) - (p[5] - p[2]) * (p[7] - p[1]),
                        (p[5] - p[2]) * (p[6] - p[0]) - (p[3] - p[0]) * (p[8] - p[2]),
                        (p[3] - p[0]) * (p[7] - p[1]) - (p[4] - p[1]) * (p[6] - p[0])
                };
                int axis = 0;
                for (int a = 1; a < 3; a++) if (Math.abs(normal[a]) > Math.abs(normal[axis])) axis = a;
                double dominant = Math.abs(normal[axis]);
                if (dominant < 1e-12) continue;
                int ua = (axis + 1) % 3, va = (axis + 2) % 3;
                int minU = surfaceMin(p, ua), maxU = surfaceMax(p, ua);
                int minV = surfaceMin(p, va), maxV = surfaceMax(p, va);
                for (int u = minU; u < maxU; u++) for (int v = minV; v < maxV; v++) {
                    if (!project(p, axis, midpoint(u), midpoint(v), barycentric)) continue;
                    int color = triangle.pixel(barycentric);
                    if ((color >>> 24) < 128) continue;
                    double plane = triangle.coordinate(axis, barycentric) * resolution;
                    position[axis] = (int) Math.floor(plane - (normal[axis] > 0 ? 1e-5 : -1e-5));
                    position[ua] = u;
                    position[va] = v;
                    if (!inside(position[0], position[1], position[2])) continue;
                    int cell = index(position[0], position[1], position[2]);
                    solid[cell] = true;
                    for (int a = 0; a < 3; a++) {
                        if (Math.abs(normal[a]) > dominant * .1) paint[cell * 6 + a * 2 + (normal[a] > 0 ? 1 : 0)] = color;
                    }
                }
            }
        }

        private int surfaceMin(double[] points, int axis) {
            double min = Math.min(points[axis], Math.min(points[3 + axis], points[6 + axis]));
            return Math.max(0, (int) Math.floor(min * resolution));
        }

        private int surfaceMax(double[] points, int axis) {
            double max = Math.max(points[axis], Math.max(points[3 + axis], points[6 + axis]));
            int size = axis == 0 ? nx : axis == 1 ? ny : nz;
            return Math.min(size, (int) Math.ceil(max * resolution));
        }

        /** Gantry cut faces inherit a nearby surface pixel. Press cut faces use Matter colors. */
        private void propagateGantryMaterial() {
            int[] queue = new int[solid.length];
            int head = 0, tail = 0;
            for (int cell = 0; cell < solid.length; cell++) {
                if (!solid[cell]) continue;
                for (int side = 0; side < 6; side++) {
                    int color = paint[cell * 6 + side];
                    if (color == 0) continue;
                    material[cell] = color;
                    queue[tail++] = cell;
                    break;
                }
            }
            while (head < tail) {
                int cell = queue[head++];
                int x = cell % nx, z = cell / nx % nz, y = cell / (nx * nz);
                for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
                    int px = x + (axis == 0 ? sign : 0);
                    int py = y + (axis == 1 ? sign : 0);
                    int pz = z + (axis == 2 ? sign : 0);
                    if (!inside(px, py, pz)) continue;
                    int neighbor = index(px, py, pz);
                    if (!solid[neighbor] || material[neighbor] != 0) continue;
                    material[neighbor] = material[cell];
                    queue[tail++] = neighbor;
                }
            }
        }

        private double midpoint(int cell) { return (cell + .5) / resolution; }
        private boolean inside(int x, int y, int z) { return x >= 0 && x < nx && y >= 0 && y < ny && z >= 0 && z < nz; }
        private int index(int x, int y, int z) { return (y * nz + z) * nx + x; }

        public int count(double progress) {
            return (int) Math.floor(Math.max(0, Math.min(1, progress)) * order.length);
        }

        public double height(double progress) {
            int count = count(progress);
            return count == 0 ? 0 : (order[count - 1] / (nx * nz) + 1) / (double) resolution;
        }

        private boolean occupied(int x, int y, int z, int count) {
            if (!inside(x, y, z)) return false;
            int at = rank[index(x, y, z)];
            return at > 0 && at <= count;
        }

        /** Four words per vertex: three float bit patterns, followed by the face index and RGB. */
        public int[] build(int count) {
            Vertices output = new Vertices();
            double[] point = new double[3];
            int[] base = new int[3];
            for (int i = 0; i < count; i++) {
                int cell = order[i];
                int x = cell % nx, z = cell / nx % nz, y = cell / (nx * nz);
                base[0] = x;
                base[1] = y;
                base[2] = z;
                for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
                    if (occupied(x + (axis == 0 ? sign : 0), y + (axis == 1 ? sign : 0), z + (axis == 2 ? sign : 0), count)) continue;
                    int side = axis * 2 + (sign > 0 ? 1 : 0);
                    int color = color(cell, side);
                    double shade = axis == 1 ? (sign > 0 ? 1 : .60) : axis == 0 ? .78 : .9;
                    int rgb = (int) ((color >>> 16 & 255) * shade) << 16
                            | (int) ((color >>> 8 & 255) * shade) << 8 | (int) ((color & 255) * shade);
                    int ua = (axis + 1) % 3, va = (axis + 2) % 3;
                    for (int[] corner : sign > 0 ? POSITIVE : NEGATIVE) {
                        point[axis] = (base[axis] + (sign > 0 ? 1 : 0)) / (double) resolution;
                        point[ua] = (base[ua] + corner[0]) / (double) resolution;
                        point[va] = (base[va] + corner[1]) / (double) resolution;
                        point[0] += originX;
                        point[2] += originZ;
                        output.add(point, side << 24 | rgb);
                    }
                }
            }
            return Arrays.copyOf(output.data, output.size);
        }

        private int color(int cell, int side) {
            int pixel = paint[cell * 6 + side];
            if (pixel != 0) return pixel;
            if (press) return MATTER[(cell * 1103515245 + 12345 >>> 16) & 3];
            return material[cell] != 0 ? material[cell] : 0xffa8b0b8;
        }
    }

    /** Barycentric coordinates in the plane perpendicular to axis. */
    private static boolean project(double[] points, int axis, double u, double v, double[] barycentric) {
        int ua = (axis + 1) % 3, va = (axis + 2) % 3;
        double ax = points[ua], ay = points[va];
        double bx = points[3 + ua], by = points[3 + va];
        double cx = points[6 + ua], cy = points[6 + va];
        double denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(denominator) < 1e-12) return false;
        barycentric[0] = ((by - cy) * (u - cx) + (cx - bx) * (v - cy)) / denominator;
        barycentric[1] = ((cy - ay) * (u - cx) + (ax - cx) * (v - cy)) / denominator;
        barycentric[2] = 1 - barycentric[0] - barycentric[1];
        return barycentric[0] >= -1e-7 && barycentric[1] >= -1e-7 && barycentric[2] >= -1e-7;
    }

    private BuilderFabricationMesh() {}
}
