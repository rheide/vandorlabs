package com.vandorlabs.neoforge.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vandorlabs.screen.ScreenControlLayout;
import com.vandorlabs.neoforge.NativeVoxelShapes;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Released collision and square-pixel surfaces, cached for the four horizontal facings. */
public record NativeYokeGeometry(VoxelShape outline, VoxelShape collision, List<Face> surfaces) {
    private static final Map<Direction, NativeYokeGeometry> GEOMETRY = load();
    public record Face(Vec3 topLeft, Vec3 right, Vec3 down, Vec3 normal, int height) {
        public record Sample(double time, int row, int sliderStep) {}

        Sample sample(Vec3 start, Vec3 end, int rows) {
            if (rows == 0) return null;
            Vec3 segment = end.subtract(start);
            double denom = segment.dot(normal);
            if (denom >= -1e-8) return null;
            double time = topLeft.subtract(start).dot(normal) / denom;
            if (time < 0 || time > 1) return null;
            Vec3 local = start.add(segment.scale(time)).subtract(topLeft);
            double column = local.dot(right) / right.lengthSqr();
            double line = local.dot(down) / down.lengthSqr();
            if (column < 0 || column >= 1 || line < 0 || line >= 1) return null;
            int sliderStep = Math.max(0, Math.min(3, (int)((column * 128 - ScreenControlLayout.SLIDER_LEFT) * 4
                / (ScreenControlLayout.SLIDER_RIGHT - ScreenControlLayout.SLIDER_LEFT))));
            return new Sample(time, (int)(line * rows), sliderStep);
        }
    }

    static void initializeControls(net.minecraft.nbt.CompoundTag settings) {
        for (String slot : List.of("Primary", "Secondary")) {
            String key = "Redstone" + slot;
            if (!settings.contains(key)) {
                var controls = new net.minecraft.nbt.CompoundTag();
                controls.putBoolean("Enabled", true);
                settings.put(key, controls);
            }
            settings.putInt(slot + "SurfaceTexture", -2);
        }
    }

    static NativeYokeGeometry of(BlockState state) {
        return GEOMETRY.get(state.getValue(NativeScreenBlock.HORIZONTAL_FACING));
    }

    public static Face face(BlockState state, int slot) { return of(state).surfaces().get(slot); }

    private static Vec3 turn(Vec3 v, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(-v.z, v.y, v.x);
            case SOUTH -> new Vec3(-v.x, v.y, -v.z);
            case WEST -> new Vec3(v.z, v.y, -v.x);
            default -> v;
        };
    }

    private static Vec3 point(Vec3 v, Direction facing) {
        return turn(v.add(-.5, 0, -.5), facing).add(.5, 0, .5);
    }

    private static Face face(JsonObject raw, Direction facing) {
        Vec3 origin = vector(raw.getAsJsonArray("origin_blocks"),0);
        Vec3 u = vector(raw.getAsJsonArray("u_vector_blocks"),0);
        Vec3 v = vector(raw.getAsJsonArray("v_vector_blocks"),0);
        Vec3 normal = vector(raw.getAsJsonArray("normal"),0);
        Vec3 top = origin.add(v).add(normal.scale(raw.get("overlay_offset_blocks").getAsDouble()));
        int height = (int)Math.round(128 * v.length() / u.length());
        return new Face(point(top, facing), turn(u, facing), turn(v.reverse(), facing), turn(normal, facing), height);
    }

    private static AABB box(Vec3 min, Vec3 max, Direction facing) {
        return new AABB(point(min, facing), point(max, facing));
    }

    private static Map<Direction, NativeYokeGeometry> load() {
        var assembly = resource("assembly");
        var surfaces = resource("surfaces").getAsJsonArray("surfaces");
        var result = new EnumMap<Direction, NativeYokeGeometry>(Direction.class);
        var bounds = assembly.getAsJsonArray("bounds_blocks");
        for (var facing : Direction.Plane.HORIZONTAL) {
            var boxes = new ArrayList<AABB>();
            for (var entry : assembly.getAsJsonArray("collision_boxes")) {
                var raw = entry.getAsJsonObject();
                boxes.add(box(vector(raw.getAsJsonArray("min"),0), vector(raw.getAsJsonArray("max"),0), facing));
            }
            result.put(facing, new NativeYokeGeometry(Shapes.create(box(vector(bounds,0),vector(bounds,3),facing)),
                NativeVoxelShapes.union(boxes, 128*1024),
                List.of(face(surfaces.get(1).getAsJsonObject(),facing), face(surfaces.get(0).getAsJsonObject(),facing))));
        }
        return result;
    }

    private static JsonObject resource(String name) {
        String path = "/assets/vandorlabs/cockpits/yoke_screen/" + name + ".json";
        try (var stream = NativeYokeGeometry.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Vec3 vector(JsonArray values, int at) {
        return new Vec3(values.get(at).getAsDouble(), values.get(at+1).getAsDouble(), values.get(at+2).getAsDouble());
    }
}
