package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.surface.core.SurfaceLayout;
import com.vandorlabs.turntable.TurntableCollisionGeometry;
import com.vandorlabs.turntable.TurntableCollisionGeometry.Box;
import com.vandorlabs.turntable.TurntableGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Shares Forge collision geometry. World queries remain on the physical server thread. */
final class NativeTurntableMotion {
    private static final int MAX_BOXES = 16384;
    private final NativeTurntableCollisions.Body body;
    private final SurfaceLayout layout;
    private final int height;

    NativeTurntableMotion(NativeTurntableCapture capture) {
        layout = capture.surface().geometry;
        height = capture.depth();
        var boxes = new ArrayList<Box>();
        for (var cell : capture.blocks().cells()) {
            if (!cell.state.isAir()) add(boxes, cell.pos, cell.state);
        }
        body = new NativeTurntableCollisions.Body(boxes, layout.pivotX, layout.pivotY, layout.pivotZ, layout.face);
    }

    NativeTurntableCollisions.Body body() { return body; }

    static void add(List<Box> boxes, BlockPos pos, BlockState state) {
        for (var part : state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
            if (boxes.size() >= MAX_BOXES) throw new IllegalArgumentException("Turntable collision box limit");
            boxes.add(box(part.move(pos)));
        }
    }

    String obstruction(ServerLevel world, double from, double delta) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        double margin = (layout.width + height) * (1 - Math.cos(Math.toRadians(Math.abs(delta) / steps / 2)));
        for (var part : body.boxes()) {
            var first = bounds(part, from);
            var last = bounds(part, from + delta);
            var sweep = first.minmax(last).inflate(margin * (1 - Math.abs(layout.face.x)),
                    margin * (1 - Math.abs(layout.face.y)), margin * (1 - Math.abs(layout.face.z)));
            if (!loaded(world, sweep)) return "Load the full Turntable sweep to continue";
            for (var shape : world.getBlockCollisions(null, sweep)) {
                for (var obstacle : shape.toAabbs()) {
                    var candidate = box(obstacle.inflate(margin * (1 - Math.abs(layout.face.x)),
                            margin * (1 - Math.abs(layout.face.y)), margin * (1 - Math.abs(layout.face.z))).deflate(.0001));
                    for (int step = 0; step <= steps; step++) {
                        if (TurntableCollisionGeometry.intersects(part, layout.pivotX, layout.pivotY, layout.pivotZ,
                                layout.face, from + delta * step / steps, candidate)) {
                            return "Turntable sweep blocked by terrain";
                        }
                    }
                }
            }
            if (!world.getEntities(null, sweep.inflate(.02)).isEmpty()) return "Clear actors from the Turntable sweep";
        }
        return null;
    }

    AABB bounds(Box part, double angle) {
        var b = TurntableCollisionGeometry.bounds(part, layout.pivotX, layout.pivotY, layout.pivotZ, layout.face, angle);
        return new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    private static boolean loaded(ServerLevel world, AABB box) {
        var min = BlockPos.containing(box.minX, box.minY, box.minZ);
        var max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (world.isOutsideBuildHeight(min) || world.isOutsideBuildHeight(max)
                || !world.getWorldBorder().isWithinBounds(min) || !world.getWorldBorder().isWithinBounds(max)) return false;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (world.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    static Box box(AABB b) { return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
}
