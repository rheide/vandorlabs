package com.vandorlabs.neoforge.coupler;

import com.google.gson.JsonParser;
import com.vandorlabs.geometry.CardinalFrame;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class NativeCouplerGeometry {
    private static final VoxelShape[][][] SHAPES = load();

    static VoxelShape shape(int kind, Direction facing, boolean locked) {
        return SHAPES[kind][facing.get3DDataValue()][locked ? 1 : 0];
    }

    private static VoxelShape[][][] load() {
        String resource = "/assets/vandorlabs/data/coupler_geometry.json";
        try (var input = NativeCouplerGeometry.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing Coupler geometry");
            var shapes = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new VoxelShape[3][6][2];
            for (int kind = 0; kind < 3; kind++) {
                String prefix = kind == 0 ? "square_coupler_" : "vehicle_coupler_slab_";
                String suffix = kind == 0 ? "" : kind == 1 ? "_bottom" : "_top";
                for (int locked = 0; locked < 2; locked++) {
                    var boxes = shapes.getAsJsonArray(prefix + (locked == 1 ? "locked" : "released") + suffix);
                    if (boxes == null || boxes.size() != 9) throw new IllegalStateException("Unexpected Coupler geometry");
                    for (var face : Direction.values()) {
                        if (kind != 0 && !face.getAxis().isHorizontal()) continue;
                        int id = face.get3DDataValue();
                        var frame = CardinalFrame.of(id, CardinalFrame.defaultUp(id));
                        VoxelShape joint = Shapes.empty();
                        for (var entry : boxes) {
                            var raw = entry.getAsJsonArray();
                            double[] min = new double[3], max = new double[3];
                            for (int axis = 0; axis < 3; axis++) {
                                double start = frame.transform(axis, raw.get(0).getAsDouble() / 16 - .5,
                                        raw.get(1).getAsDouble() / 16 - .5, raw.get(2).getAsDouble() / 16 - .5) + .5;
                                double end = frame.transform(axis, raw.get(3).getAsDouble() / 16 - .5,
                                        raw.get(4).getAsDouble() / 16 - .5, raw.get(5).getAsDouble() / 16 - .5) + .5;
                                min[axis] = Math.min(start, end);
                                max[axis] = Math.max(start, end);
                            }
                            joint = Shapes.or(joint, Shapes.box(min[0], min[1], min[2], max[0], max[1], max[2]));
                        }
                        result[kind][id][locked] = joint.optimize();
                    }
                }
            }
            return result;
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private NativeCouplerGeometry() {}
}
