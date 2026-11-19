package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.ramp.NativeRampRiders;
import com.vandorlabs.surface.core.SurfaceLayout;
import com.vandorlabs.turntable.TurntableCollisionGeometry;
import com.vandorlabs.turntable.TurntableCollisionGeometry.Box;
import com.vandorlabs.turntable.TurntableGeometry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    record Frame(List<NativeTurntableRiders.Step> riders, String obstacle) {
        private static Frame blocked(String reason) { return new Frame(List.of(), reason); }
    }

    Frame prepare(ServerLevel world, double from, double delta) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        double margin = (layout.width + height) * (1 - Math.cos(Math.toRadians(Math.abs(delta) / steps / 2)));
        double mx = margin * (1 - Math.abs(layout.face.x));
        double my = margin * (1 - Math.abs(layout.face.y));
        double mz = margin * (1 - Math.abs(layout.face.z));
        var extent = bounds(body.extent(), from).minmax(bounds(body.extent(), from + delta));
        if (layout.face == TurntableGeometry.Face.UP) {
            double radius = layout.width * TurntableGeometry.DISC_RADIUS;
            extent = extent.minmax(new AABB(layout.pivotX - radius, layout.pivotY, layout.pivotZ - radius,
                    layout.pivotX + radius, layout.pivotY, layout.pivotZ + radius));
        }
        var actors = new ArrayList<Entity>();
        world.getEntities(EntityTypeTest.forClass(Entity.class), extent.inflate(mx + .02, my + .15, mz + .02),
                actor -> !actor.isSpectator(), actors, NativeTurntableRiders.MAX_RIDERS + 1);
        if (actors.size() > NativeTurntableRiders.MAX_RIDERS || (long) actors.size() * body.boxes().size() > 500_000) {
            return Frame.blocked("Too many actors in the Turntable sweep");
        }
        var riders = new IdentityHashMap<Entity, NativeTurntableRiders.Step>();
        if (layout.face == TurntableGeometry.Face.UP) {
            for (var actor : actors) {
                if (!NativeTurntableRiders.eligible(actor) || !supported(actor, from)) continue;
                var offset = TurntableGeometry.rotate(actor.getX() - layout.pivotX,
                        actor.getY() - layout.pivotY, actor.getZ() - layout.pivotZ, layout.face, delta);
                var motion = new Vec3(layout.pivotX + offset[0] - actor.getX(), 0, layout.pivotZ + offset[2] - actor.getZ());
                riders.put(actor, new NativeTurntableRiders.Step(actor, motion, (float) delta));
            }
        }
        for (var step : riders.values()) {
            if (!riderClear(world, riders, step)) return Frame.blocked("Clear the Turntable rider path");
        }
        for (var part : body.boxes()) {
            var first = bounds(part, from);
            var last = bounds(part, from + delta);
            var sweep = first.minmax(last).inflate(mx, my, mz);
            if (!loaded(world, sweep)) return Frame.blocked("Load the full Turntable sweep to continue");
            var obstacles = new ArrayList<AABB>();
            for (var shape : world.getBlockCollisions(null, sweep)) obstacles.addAll(shape.toAabbs());
            for (var shape : NativeTurntableCollisions.appendExcept(world, sweep, List.of(), body)) obstacles.addAll(shape.toAabbs());
            for (var obstacle : obstacles) {
                var candidate = box(obstacle.inflate(mx, my, mz).deflate(.0001));
                for (int step = 0; step <= steps; step++) {
                    if (intersects(part, candidate, from + delta * step / steps)) {
                        return Frame.blocked("Turntable sweep blocked by terrain");
                    }
                }
            }
            var clearance = sweep.inflate(.02);
            for (var actor : actors) {
                if (!riders.containsKey(actor) && clearance.intersects(actor.getBoundingBox())) {
                    return Frame.blocked("Clear actors from the Turntable sweep");
                }
            }
        }
        return new Frame(List.copyOf(riders.values()), null);
    }

    private boolean supported(Entity actor, double angle) {
        var feet = actor.getBoundingBox();
        if (Math.abs(feet.minY - layout.pivotY) < .12 && TurntableGeometry.onDisc(
                actor.getX() - layout.pivotX, 0, actor.getZ() - layout.pivotZ, layout.face, layout.width)) return true;
        for (var part : body.boxes()) {
            if (Math.abs(feet.minY - part.maxY) >= .12) continue;
            var top = new Box(feet.minX + .001, part.maxY - .001, feet.minZ + .001,
                    feet.maxX - .001, part.maxY, feet.maxZ - .001);
            if (intersects(part, top, angle)) return true;
        }
        return false;
    }

    private boolean riderClear(ServerLevel world, Map<Entity, NativeTurntableRiders.Step> riders,
                               NativeTurntableRiders.Step step) {
        var actor = step.actor();
        var sweep = actor.getBoundingBox().expandTowards(step.delta()).inflate(.0001);
        if (!loaded(world, sweep) || !NativeTurntableRiders.sameSaveOwner(step)) return false;
        var boxes = new ArrayList<AABB>();
        for (var collision : world.getBlockCollisions(actor, sweep)) boxes.addAll(collision.toAabbs());
        for (var collision : NativeTurntableCollisions.appendExcept(world, sweep, List.of(), body)) {
            boxes.addAll(collision.toAabbs());
        }
        for (var other : world.getEntities(actor, sweep, entity -> !riders.containsKey(entity) && !entity.isSpectator()
                && actor.canCollideWith(entity))) boxes.add(other.getBoundingBox());
        return NativeRampRiders.clear(actor, step.delta(), boxes);
    }

    private boolean intersects(Box part, Box candidate, double angle) {
        return TurntableCollisionGeometry.intersects(part, layout.pivotX, layout.pivotY, layout.pivotZ,
                layout.face, angle, candidate);
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
