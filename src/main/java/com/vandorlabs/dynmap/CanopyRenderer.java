package com.vandorlabs.dynmap;

import com.vandorlabs.canopy.CanopyMesh;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

import org.dynmap.renderer.*;

import java.util.*;

/** Closed overview meshes, clipped into each occupied voxel to avoid duplicated assemblies. */
public final class CanopyRenderer extends CustomRenderer {
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return new String[] {"Anchor", "Pair", "CanopyMask"};
    }

    private static BlockPos position(Object value, BlockPos fallback) {
        return value instanceof Number ? BlockPos.fromLong(((Number) value).longValue()) : fallback;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext context) {
        BlockPos here = new BlockPos(context.getX(), context.getY(), context.getZ());
        BlockPos anchor = position(context.getBlockTileEntityField("Anchor"), here);
        int ax = anchor.getX() - here.getX(),
                ay = anchor.getY() - here.getY(),
                az = anchor.getZ() - here.getZ();
        Object pair = context.getBlockTileEntityFieldAt("Pair", ax, ay, az);
        BlockPos owner = position(pair, anchor);
        int ox = owner.getX() - here.getX(),
                oy = owner.getY() - here.getY(),
                oz = owner.getZ() - here.getZ();
        DynmapBlockState state = context.getBlockType();
        String id = state.blockName.substring(state.blockName.indexOf(':') + 1);
        EnumFacing facing =
                state.isStateMatch("facing", "east")
                        ? EnumFacing.EAST
                        : state.isStateMatch("facing", "south")
                                ? EnumFacing.SOUTH
                                : state.isStateMatch("facing", "west")
                                        ? EnumFacing.WEST
                                        : EnumFacing.NORTH;
        Object mask = context.getBlockTileEntityFieldAt("CanopyMask", ox, oy, oz);
        int bits = mask instanceof Number ? ((Number) mask).intValue() : 0;
        if (id.equals("regular_canopy_glass"))
            id = String.format(java.util.Locale.ROOT, "regular_1x1_%02x", bits & 15);
        else if (id.startsWith("angled_") && (bits & 11) != 0)
            id = String.format(java.util.Locale.ROOT, "%s_%02x", id, bits & 11);
        else if (pair instanceof Number && id.startsWith("opening_")) id = id.replace("1x2", "2x2");
        List<RenderPatch> result = new ArrayList<>();
        List<CanopyMesh.Face> mesh = CanopyMesh.MODELS.get(id);
        if (mesh == null) return new RenderPatch[0];
        for (CanopyMesh.Face face : mesh) {
            List<Vec3d> polygon = new ArrayList<>();
            for (Vec3d v : face.vertices)
                polygon.add(CanopyMesh.rotate(v, facing).addVector(ox, oy, oz));
            for (int axis = 0; axis < 3; axis++) {
                polygon = clip(polygon, axis, 0, true);
                polygon = clip(polygon, axis, 1, false);
            }
            int texture =
                    face.baseMaterial.equals("glass")
                            ? 1
                            : face.baseMaterial.equals("amber")
                                    ? 2
                                    : face.baseMaterial.equals("cyan") ? 3 : 0;
            for (int i = 1; i < polygon.size() - 1; i++) {
                Vec3d a = polygon.get(0), b = polygon.get(i), c = polygon.get(i + 1);
                RenderPatch patch =
                        context.getPatchFactory()
                                .getPatch(
                                        a.x,
                                        a.y,
                                        a.z,
                                        b.x,
                                        b.y,
                                        b.z,
                                        c.x,
                                        c.y,
                                        c.z,
                                        1,
                                        RenderPatchFactory.SideVisible.BOTH,
                                        texture);
                if (patch != null) result.add(patch);
            }
        }
        return result.toArray(new RenderPatch[0]);
    }

    private static double component(Vec3d v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    private static List<Vec3d> clip(List<Vec3d> input, int axis, double plane, boolean lower) {
        List<Vec3d> out = new ArrayList<>();
        if (input.isEmpty()) return out;
        Vec3d prev = input.get(input.size() - 1);
        boolean inside = lower ? component(prev, axis) >= plane : component(prev, axis) <= plane;
        for (Vec3d next : input) {
            boolean nextInside =
                    lower ? component(next, axis) >= plane : component(next, axis) <= plane;
            if (inside != nextInside) {
                double t =
                        (plane - component(prev, axis))
                                / (component(next, axis) - component(prev, axis));
                out.add(prev.add(next.subtract(prev).scale(t)));
            }
            if (nextInside) out.add(next);
            prev = next;
            inside = nextInside;
        }
        return out;
    }
}
