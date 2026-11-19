package com.vandorlabs.neoforge.seating;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Released chair dimensions in block units, with collision clipped to each reserved cell. */
public final class NativeAdvancedChairGeometry {
    private record Box(Vec3 min, Vec3 max) {}
    private record Data(Vec3 cushion, List<Box> boxes) {}

    static final int ROOT = 0, UPPER = 1, LEFT = 2, RIGHT = 3, CELLS = 4;
    private static final Data SOURCE = load();
    private static final VoxelShape[][][] SHAPES = shapes();

    public static double height(double y, int step) {
        double join = 7.4 / 16D;
        double drop = (3 - step) / 8D;
        return y >= join ? y - drop : y * (join - drop) / join;
    }

    public static Vec3 cushion(Direction facing, int step) {
        Vec3 point = SOURCE.cushion();
        return turn(new Vec3(point.x, height(point.y, step), point.z), facing);
    }

    static BlockPos offset(int part, Direction facing) {
        if (part == UPPER) return BlockPos.ZERO.above();
        if (part == LEFT) return BlockPos.ZERO.relative(facing.getCounterClockWise());
        if (part == RIGHT) return BlockPos.ZERO.relative(facing.getClockWise());
        return BlockPos.ZERO;
    }

    static VoxelShape shape(Direction facing, int step, int part) {
        return SHAPES[facing.get2DDataValue()][step][part];
    }

    private static Vec3 turn(Vec3 point, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(1 - point.z, point.y, point.x);
            case SOUTH -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case WEST -> new Vec3(point.z, point.y, 1 - point.x);
            default -> point;
        };
    }

    private static VoxelShape[][][] shapes() {
        var result = new VoxelShape[4][4][CELLS];
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int height = 0; height < 4; height++) {
                for (int part = 0; part < CELLS; part++) {
                    var offset = offset(part, facing);
                    VoxelShape shape = Shapes.empty();
                    for (var box : SOURCE.boxes()) {
                        var min = turn(box.min(), facing);
                        var max = turn(box.max(), facing);
                        double x0 = Math.max(0, Math.min(min.x, max.x) - offset.getX());
                        double z0 = Math.max(0, Math.min(min.z, max.z) - offset.getZ());
                        double x1 = Math.min(1, Math.max(min.x, max.x) - offset.getX());
                        double z1 = Math.min(1, Math.max(min.z, max.z) - offset.getZ());
                        double y0 = Math.max(0, height(min.y, height) - offset.getY());
                        double y1 = Math.min(1, height(max.y, height) - offset.getY());
                        if (x1 > x0 && y1 > y0 && z1 > z0) shape = Shapes.or(shape, Shapes.box(x0, y0, z0, x1, y1, z1));
                    }
                    result[facing.get2DDataValue()][height][part] = shape.optimize();
                }
            }
        }
        return result;
    }

    private static Data load() {
        String path = "/assets/vandorlabs/cockpits/chair_siderests/assembly.json";
        try (var stream = NativeAdvancedChairGeometry.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing authored chair geometry");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var boxes = new ArrayList<Box>();
            for (var entry : json.getAsJsonArray("collision_boxes")) {
                var value = entry.getAsJsonObject();
                boxes.add(new Box(vec(value.getAsJsonArray("min")), vec(value.getAsJsonArray("max"))));
            }
            return new Data(vec(json.getAsJsonArray("seat_position_blocks")), List.copyOf(boxes));
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Vec3 vec(com.google.gson.JsonArray value) {
        return new Vec3(value.get(0).getAsDouble(), value.get(1).getAsDouble(), value.get(2).getAsDouble());
    }

    private NativeAdvancedChairGeometry() {}
}
