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

    /** Current diagonal bands retain their exact flat-end transitions and filled-side masks. */
    public static NativeFluidRegions diagonal(Direction facing,boolean inverted,int mode,int fill,double lower,double upper,boolean inset){
        if(facing.getAxis()==Direction.Axis.Y||mode<0||mode>2||fill<0||fill>3)throw new IllegalArgumentException("Diagonal fluid pose");
        boolean shallow=mode==2;double[] cuts=!Double.isNaN(lower)||!Double.isNaN(upper)?new double[]{0,.25,.75,1}:new double[]{0,1};
        List<Region> regions=new ArrayList<>();List<NativeFluidSurface.Surface> surfaces=new ArrayList<>();
        for(int side=0;side<2;side++)if((fill&(1<<side))==0)for(int band=0;band+1<cuts.length;band++){
            double lo=cuts[band],hi=cuts[band+1];
            double a=com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,lo,lower,upper),b=com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,hi,lower,upper);
            double depth=(side==0?(inset?1D/16:0):(inset?3D/16:.25))+(side==0?-.001:.001);
            double slope=(b-a)/(hi-lo),intercept=a-slope*lo+depth+(shallow?com.vandorlabs.render.DiagonalWallGeometry.band(mode,inverted):0);
            List<Plane> planes=new ArrayList<>(UNIT);
            planes.add(worldPlane(facing,shallow?new Plane(0,0,-1,-lo):new Plane(0,-1,0,-lo)));
            planes.add(worldPlane(facing,shallow?new Plane(0,0,1,hi):new Plane(0,1,0,hi)));
            double sign=side==0?1:-1;Plane divider=worldPlane(facing,shallow?new Plane(0,sign,-sign*slope,sign*intercept):new Plane(0,-sign*slope,sign,sign*intercept));planes.add(divider);regions.add(new Region(side,planes));
            for(Direction face:Direction.values()){
                List<double[]> polygon=cube(face);for(Plane plane:planes)polygon=DiagonalMeshClip.plane(polygon,plane.x,plane.y,plane.z,plane.distance);
                if(NativeFluidSurface.area(polygon)>1e-10)surfaces.add(new NativeFluidSurface.Surface(polygon,side,axis(face),face.get3DDataValue()));
            }
            List<double[]> pane=new ArrayList<>();for(double[] v:new double[][]{{0,lo},{1,lo},{1,hi},{0,hi}}){double at=slope*v[1]+intercept;pane.add(worldPoint(facing,shallow?new double[]{v[0],at,v[1]}:new double[]{v[0],v[1],at}));}
            pane=DiagonalMeshClip.polygon(new double[]{0,0,0,1,1,1},pane.toArray(new double[0][]));
            if(NativeFluidSurface.area(pane)>1e-10){
                double nx=0,ny=0,nz=0;for(int j=0;j<pane.size();j++){double[] c=pane.get(j),d=pane.get((j+1)%pane.size());nx+=c[1]*d[2]-c[2]*d[1];ny+=c[2]*d[0]-c[0]*d[2];nz+=c[0]*d[1]-c[1]*d[0];}
                if(nx*divider.x+ny*divider.y+nz*divider.z<0)Collections.reverse(pane);
                surfaces.add(new NativeFluidSurface.Surface(pane,side,shallow?1:axis(facing),-1));
            }
        }
        return new NativeFluidRegions(regions,surfaces);
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
