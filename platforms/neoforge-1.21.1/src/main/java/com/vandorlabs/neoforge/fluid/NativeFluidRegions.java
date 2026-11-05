package com.vandorlabs.neoforge.fluid;

import com.vandorlabs.render.DiagonalMeshClip;
import com.vandorlabs.render.PanelDepthPolicy;
import java.util.*;
import net.minecraft.core.Direction;

/** Continuous panel half-spaces, separate from conservative physical collision runs. */
public final class NativeFluidRegions {
    public record Plane(double x, double y, double z, double distance) {}

    public record Region(int side, List<Plane> planes) {
        public Region {
            if (side < 0 || side > 1) throw new IllegalArgumentException("Fluid side");
            planes = List.copyOf(planes);
        }
    }

    private static final List<Plane> UNIT = List.of(
            new Plane(-1, 0, 0, 0), new Plane(1, 0, 0, 1),
            new Plane(0, -1, 0, 0), new Plane(0, 1, 0, 1),
            new Plane(0, 0, -1, 0), new Plane(0, 0, 1, 1));
    private static final NativeFluidRegions[] FLAT = new NativeFluidRegions[36];
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL)
            for (int depth = 0; depth < 3; depth++)
                for (int inset = 0; inset < 2; inset++)
                    FLAT[facing.get3DDataValue() * 6 + depth * 2 + inset] =
                            buildFlat(facing, depth, inset != 0);
    }

    private final List<Region> regions;
    private final List<NativeFluidSurface.Surface> surfaces;

    private NativeFluidRegions(List<Region> regions, List<NativeFluidSurface.Surface> surfaces) {
        this.regions = List.copyOf(regions);
        this.surfaces = List.copyOf(surfaces);
    }

    public List<Region> regions() { return regions; }
    public List<NativeFluidSurface.Surface> surfaces() { return surfaces; }

    /** Current flat wall joins retain the same analytic separating leaf. */
    public static NativeFluidRegions flat(Direction facing, int depth, boolean inset) {
        if (facing.getAxis() == Direction.Axis.Y || depth < 0 || depth > 2)
            throw new IllegalArgumentException("Flat fluid pose");
        return FLAT[facing.get3DDataValue() * 6 + depth * 2 + (inset ? 1 : 0)];
    }

    private static NativeFluidRegions buildFlat(Direction facing, int depth, boolean inset) {
        List<Region> regions = new ArrayList<>(2);
        List<NativeFluidSurface.Surface> surfaces = new ArrayList<>();
        double near = PanelDepthPolicy.start(depth) / 16D;
        for (int side = 0; side < 2; side++) {
            double offset = (side == 0 ? (inset ? 1D / 16 : 0) : (inset ? 3D / 16 : .25))
                    + (side == 0 ? -.001 : .001);
            // Preserve current Forge arithmetic order, including the leaf-plane inset.
            double intercept = near - 0D + offset + 0D;
            double sign = side == 0 ? 1 : -1;
            Plane divider = worldPlane(facing, new Plane(0, 0, sign, sign * intercept));
            List<Plane> planes = new ArrayList<>(UNIT);
            planes.add(divider);
            regions.add(new Region(side, planes));
            for (Direction face : Direction.values()) {
                List<double[]> polygon = cube(face);
                for (Plane plane : planes)
                    polygon = DiagonalMeshClip.plane(polygon, plane.x, plane.y, plane.z, plane.distance);
                if (NativeFluidSurface.area(polygon) > 1e-10)
                    surfaces.add(new NativeFluidSurface.Surface(
                            polygon, side, axis(face), face.get3DDataValue()));
            }
            List<double[]> pane = new ArrayList<>();
            for (double[] pair : new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}})
                pane.add(worldPoint(facing, new double[]{pair[0], pair[1], intercept}));
            pane = DiagonalMeshClip.polygon(new double[]{0, 0, 0, 1, 1, 1},
                    pane.toArray(new double[0][]));
            if (NativeFluidSurface.area(pane) > 1e-10) {
                double[] normal = NativeFluidSurface.normal(pane);
                if (normal[0] * divider.x + normal[1] * divider.y + normal[2] * divider.z < 0)
                    Collections.reverse(pane);
                surfaces.add(new NativeFluidSurface.Surface(pane, side, axis(facing), -1));
            }
        }
        return new NativeFluidRegions(regions, surfaces);
    }

    public static List<double[]> clip(List<double[]> polygon, Region region, int dx, int dy, int dz) {
        for (Plane plane : region.planes) {
            double d = plane.distance + plane.x * dx + plane.y * dy + plane.z * dz;
            polygon = DiagonalMeshClip.plane(polygon, plane.x, plane.y, plane.z, d);
            if (NativeFluidSurface.area(polygon) < 1e-10) return List.of();
        }
        return polygon;
    }

    public static void subtract(List<double[]> polygon, Region region, int dx, int dy, int dz,
                                List<List<double[]>> out) {
        List<double[]> inside = polygon;
        for (Plane plane : region.planes) {
            double d = plane.distance + plane.x * dx + plane.y * dy + plane.z * dz;
            List<double[]> outside = DiagonalMeshClip.plane(
                    inside, -plane.x, -plane.y, -plane.z, -d - 1e-8);
            if (NativeFluidSurface.area(outside) > 1e-10) out.add(outside);
            inside = DiagonalMeshClip.plane(inside, plane.x, plane.y, plane.z, d);
            if (NativeFluidSurface.area(inside) < 1e-10) return;
        }
    }

    private static int axis(Direction face) {
        return face.getAxis() == Direction.Axis.X ? 0 : face.getAxis() == Direction.Axis.Y ? 1 : 2;
    }

    private static double[] worldPoint(Direction facing, double[] p) {
        return switch (facing) {
            case EAST -> new double[]{1 - p[2], p[1], p[0]};
            case SOUTH -> new double[]{1 - p[0], p[1], 1 - p[2]};
            case WEST -> new double[]{p[2], p[1], 1 - p[0]};
            default -> p.clone();
        };
    }

    private static Plane worldPlane(Direction facing, Plane p) {
        double[] origin = worldPoint(facing, new double[]{0, 0, 0});
        double[] tip = worldPoint(facing, new double[]{p.x, p.y, p.z});
        double x = tip[0] - origin[0], y = tip[1] - origin[1], z = tip[2] - origin[2];
        return new Plane(x, y, z, p.distance + x * origin[0] + y * origin[1] + z * origin[2]);
    }

    private static List<double[]> cube(Direction face) {
        double[][] p = switch (face) {
            case DOWN -> new double[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case UP -> new double[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case WEST -> new double[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
            case EAST -> new double[][]{{1, 1, 0}, {1, 1, 1}, {1, 0, 1}, {1, 0, 0}};
            case NORTH -> new double[][]{{0, 1, 0}, {1, 1, 0}, {1, 0, 0}, {0, 0, 0}};
            default -> new double[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
        };
        return new ArrayList<>(Arrays.asList(p));
    }
}
