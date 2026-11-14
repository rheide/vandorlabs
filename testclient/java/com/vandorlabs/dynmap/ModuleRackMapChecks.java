package com.vandorlabs.dynmap;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/** Exercise the native rack assets through Dynmap, including both vertical mounts. */
public final class ModuleRackMapChecks {
    private static final class Patch implements RenderPatch {
        final int texture;
        final double[] coordinates, limits;
        Patch(int texture, double[] coordinates, double[] limits) {
            this.texture = texture;
            this.coordinates = coordinates;
            this.limits = limits;
        }
        @Override public int getTextureIndex() { return texture; }
        double[][] corners() {
            double[][] result = new double[4][3];
            double[][] uv = {{limits[0],limits[2]},{limits[0],limits[3]},
                    {limits[1],limits[4]},{limits[1],limits[5]}};
            for (int point = 0; point < 4; point++) for (int axis = 0; axis < 3; axis++)
                result[point][axis] = coordinates[axis]
                        + uv[point][0] * (coordinates[3+axis] - coordinates[axis])
                        + uv[point][1] * (coordinates[6+axis] - coordinates[axis]);
            return result;
        }
    }

    private static final RenderPatchFactory FACTORY = (RenderPatchFactory)Proxy.newProxyInstance(
            ModuleRackMapChecks.class.getClassLoader(), new Class<?>[]{RenderPatchFactory.class},
            (proxy, method, arguments) -> {
                if (!method.getName().equals("getPatch")) throw new AssertionError(method);
                double[] coordinates = new double[9];
                for (int i = 0; i < 9; i++) coordinates[i] = (Double)arguments[i];
                double[] limits = arguments.length == 17
                        ? new double[]{(Double)arguments[9],(Double)arguments[10],(Double)arguments[11],
                                (Double)arguments[13],(Double)arguments[12],(Double)arguments[14]}
                        : new double[]{(Double)arguments[9],(Double)arguments[10],(Double)arguments[11],
                                (Double)arguments[12],(Double)arguments[11],(Double)arguments[12]};
                return new Patch((Integer)arguments[arguments.length-1], coordinates, limits);
            });

    private static final class Context implements MapDataContext {
        final DynmapBlockState state;
        final int mask;
        Context(String facing, int mask) {
            state = new DynmapBlockState(null, 0, "vandorlabs:module_rack", "facing="+facing, "IRON");
            this.mask = mask;
        }
        public RenderPatchFactory getPatchFactory() { return FACTORY; }
        public DynmapBlockState getBlockType() { return state; }
        public Object getBlockTileEntityField(String name) { return "ModuleMask".equals(name) ? mask : null; }
        public DynmapBlockState getBlockTypeAt(int x, int y, int z) { return state; }
        public Object getBlockTileEntityFieldAt(String name, int x, int y, int z) { return null; }
        public int getX() { return 0; }
        public int getY() { return 80; }
        public int getZ() { return 0; }
    }

    public static void main(String[] args) {
        ModuleRackRenderer renderer = new ModuleRackRenderer();
        int states = 0;
        for (String facing : new String[]{"south","west","north","east","up","down"}) {
            Set<Long> layouts = new HashSet<>();
            for (int mask = 0; mask < 16; mask++) {
                Context context = new Context(facing, mask);
                RenderPatch[] mesh = renderer.getRenderPatchList(context);
                if (mesh.length <= 6) throw new AssertionError("Rack reduced to a cube: " + facing);
                if (mesh != renderer.getRenderPatchList(context)) throw new AssertionError("Settled map cache missed");
                if (mask == 1) checkTopIndicator(facing, mesh);
                long signature = 1;
                for (RenderPatch raw : mesh) {
                    Patch patch = (Patch)raw;
                    if (patch.texture < 0 || patch.texture >= 6) throw new AssertionError("Missing native texture");
                    signature = 31 * signature + patch.texture;
                    for (double uv : patch.limits) {
                        if (!Double.isFinite(uv) || uv < -1e-8 || uv > 1+1e-8)
                            throw new AssertionError("Native rack UV outside texture");
                        signature = 31 * signature + Math.round(uv * 1000000);
                    }
                    for (double[] point : patch.corners()) for (double coordinate : point) {
                        if (!Double.isFinite(coordinate) || coordinate < -1e-8 || coordinate > 1+1e-8)
                            throw new AssertionError("Rack map geometry outside its cell: " + facing);
                        signature = 31 * signature + Math.round(coordinate * 1000000);
                    }
                }
                if (!layouts.add(signature)) throw new AssertionError("Distinct occupied slots share a map layout");
                states++;
            }
        }
        System.out.println("Module Rack Dynmap PASS states=" + states
                + " all six facings, all occupancy masks, native UV bounds, shared settled meshes, asymmetric top-slot landmarks");
    }

    private static void checkTopIndicator(String facing, RenderPatch[] mesh) {
        // Only row zero is lit. Independent asymmetric model-unit landmarks
        // catch mirrored/right-left errors and all facings accidentally using SOUTH.
        double[] expected;
        switch (facing) {
            case "north": expected = new double[]{.5,12.625,.75,2.5,13.875,.875}; break;
            case "west": expected = new double[]{.75,12.625,13.5,.875,13.875,15.5}; break;
            case "east": expected = new double[]{15.125,12.625,.5,15.25,13.875,2.5}; break;
            case "up": expected = new double[]{13.5,15.125,2.125,15.5,15.25,3.375}; break;
            case "down": expected = new double[]{13.5,.75,12.625,15.5,.875,13.875}; break;
            default: expected = new double[]{13.5,12.625,15.125,15.5,13.875,15.25};
        }
        double[] actual = {Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for (RenderPatch raw : mesh) {
            Patch patch = (Patch)raw;
            if (patch.texture != 4 || patch.limits[0] < .5 - 1e-8) continue;
            for (double[] point : patch.corners()) for (int axis = 0; axis < 3; axis++) {
                actual[axis] = Math.min(actual[axis], point[axis] * 16);
                actual[axis+3] = Math.max(actual[axis+3], point[axis] * 16);
            }
        }
        for (int value = 0; value < 6; value++)
            if (Math.abs(actual[value] - expected[value]) > 1e-8)
                throw new AssertionError("Top indicator orientation: " + facing + " axis=" + value
                        + " expected=" + expected[value] + " actual=" + actual[value]);
    }

    private ModuleRackMapChecks() { }
}
