package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.physics.OrientedBounds;
import com.vandorlabs.physics.RigidTransform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Local immutable collision shapes. World contacts only query already loaded terrain. */
final class NativeVehicleHull {
    private static final double EPSILON = 1e-6;
    private static final int MAX_SHAPES = 8192;

    enum FlightContact { CLEAR, WHEEL, OBSTRUCTION }

    private record Cluster(BlockPos origin, boolean wheel) {}
    private record Group(AABB bounds, List<AABB> shapes, boolean wheel) {}
    private record Contact(OrientedBounds hull, OrientedBounds terrain, boolean wheel) {}
    private record Target(BlockPos pos, List<AABB> shapes) {}

    private final List<Group> groups;
    private final List<Target> targets;
    private final AABB bounds;
    private final double radius;
    private final Supplier<List<AABB>> mechanism;
    private List<AABB> priorMechanism = List.of();
    private List<Group> mechanicalGroups = List.of();
    private AABB mechanicalBounds;

    NativeVehicleHull(Map<BlockPos, BlockState> cells, Supplier<List<AABB>> mechanism) {
        this.mechanism = mechanism;
        var view = new SnapshotBlocks(Map.copyOf(cells));
        var clusters = new LinkedHashMap<Cluster, List<AABB>>();
        var selection = new ArrayList<Target>();
        AABB envelope = null;
        int count = 0;
        for (var cell : cells.entrySet()) {
            BlockPos at = cell.getKey();
            BlockState state = cell.getValue();
            if (state.isAir()) continue;
            // An entity is centered in the controller cell horizontally. The cell's base remains local Y=0.
            AABB volume = new AABB(at).move(-.5, 0, -.5);
            envelope = envelope == null ? volume : envelope.minmax(volume);
            var outline = state.getShape(view, at, CollisionContext.empty()).toAabbs();
            if (!outline.isEmpty()) {
                selection.add(new Target(at, outline.stream().map(box -> box.move(at).move(-.5, 0, -.5)).toList()));
            }
            if (mechanism != null && NativeVehicleArms.block(state)) continue;
            for (var part : state.getCollisionShape(view, at, CollisionContext.empty()).toAabbs()) {
                if (++count > MAX_SHAPES) throw new IllegalArgumentException("Vehicle collision shape limit");
                AABB box = part.move(at).move(-.5, 0, -.5);
                var cluster = new BlockPos(at.getX() >> 2, at.getY() >> 2, at.getZ() >> 2);
                boolean wheel = state.getBlock() instanceof com.vandorlabs.neoforge.landing.NativeSideWheel;
                clusters.computeIfAbsent(new Cluster(cluster, wheel), key -> new ArrayList<>()).add(box);
            }
        }
        if (envelope == null || count == 0) throw new IllegalArgumentException("Vehicle has no collision shape");
        var assembled = new ArrayList<Group>(clusters.size());
        for (var entry : clusters.entrySet()) {
            var members = entry.getValue();
            AABB extent = members.getFirst();
            for (var box : members) extent = extent.minmax(box);
            envelope = envelope.minmax(extent);
            assembled.add(new Group(extent, List.copyOf(members), entry.getKey().wheel));
        }
        groups = List.copyOf(assembled);
        targets = List.copyOf(selection);
        bounds = envelope;
        radius = radius(bounds);
    }

    private static double radius(AABB bounds) {
        double rx = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
        double rz = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
        double ry = Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY));
        return Math.sqrt(rx * rx + rz * rz + ry * ry);
    }

    AABB bounds(Vec3 at, float yaw, float pitch, Direction facing) {
        return broad(shape(activeBounds(), transform(at, yaw, pitch, facing)));
    }

    private List<Group> mechanismGroups() {
        if (mechanism == null) return List.of();
        var current = mechanism.get();
        if (current != priorMechanism) {
            priorMechanism = current;
            var clusters = new LinkedHashMap<BlockPos, List<AABB>>();
            AABB enclosing = null;
            for (var box : current) {
                var center = box.getCenter();
                var chunk = new BlockPos(Mth.floor(center.x) >> 2, Mth.floor(center.y) >> 2, Mth.floor(center.z) >> 2);
                clusters.computeIfAbsent(chunk, unused -> new ArrayList<>()).add(box);
                enclosing = enclosing == null ? box : enclosing.minmax(box);
            }
            var groups = new ArrayList<Group>(clusters.size());
            for (var members : clusters.values()) groups.add(new Group(members.stream().reduce(AABB::minmax).orElseThrow(), members, false));
            mechanicalGroups = List.copyOf(groups);
            mechanicalBounds = enclosing;
        }
        return mechanicalGroups;
    }

    private AABB activeBounds() {
        mechanismGroups();
        return mechanicalBounds == null ? bounds : bounds.minmax(mechanicalBounds);
    }

    private double activeRadius() {
        mechanismGroups();
        return mechanicalBounds == null ? radius : Math.max(radius, radius(mechanicalBounds));
    }

    private static RigidTransform transform(Vec3 at, float yaw, float pitch, Direction facing) {
        return RigidTransform.translation(at.x, at.y, at.z)
                .then(RigidTransform.joint(0, 0, 0, facing.getStepX(), facing.getStepZ(), yaw, pitch));
    }

    static Vec3 rotate(Vec3 point, float yaw, float pitch, Direction facing) {
        double[] result = transform(Vec3.ZERO, yaw, pitch, facing).point(point.x, point.y, point.z);
        return new Vec3(result[0], result[1], result[2]);
    }

    static Vec3 inverse(Vec3 point, Vec3 at, float yaw, float pitch, Direction facing) {
        double[] result = transform(at, yaw, pitch, facing).inverse(point.x, point.y, point.z);
        return new Vec3(result[0], result[1], result[2]);
    }

    private static OrientedBounds shape(AABB box, RigidTransform transform) {
        return new OrientedBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, transform);
    }

    private static AABB broad(OrientedBounds box) {
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    static AABB worldBox(NativeGroundVehicle craft, AABB local) {
        return broad(shape(local, transform(craft.position(), craft.getYRot(), craft.hullPitch(), craft.facing())));
    }

    static boolean loadedColumns(Level world, AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (world.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private List<Contact> contacts(NativeGroundVehicle craft, Vec3 at, float yaw, float pitch, Vec3 travel) {
        var pose = transform(at, yaw, pitch, craft.facing());
        var envelope = broad(shape(activeBounds(), pose)).expandTowards(travel).inflate(EPSILON);
        if (!craft.level().getWorldBorder().isWithinBounds(envelope) || !loadedColumns(craft.level(), envelope)
                || envelope.minY < craft.level().getMinBuildHeight() - EPSILON
                || craft.level().dimension() != Level.OVERWORLD && envelope.maxY > craft.level().getMaxBuildHeight() + EPSILON) return null;
        var contacts = new ArrayList<Contact>();
        for (var group : groups) appendContacts(craft, group, pose, travel, contacts);
        for (var group : mechanismGroups()) appendContacts(craft, group, pose, travel, contacts);
        return contacts;
    }

    private static void appendContacts(NativeGroundVehicle craft, Group group, RigidTransform pose,
                                       Vec3 travel, List<Contact> contacts) {
        var sweep = broad(shape(group.bounds, pose)).expandTowards(travel).inflate(EPSILON);
        var obstacles = new ArrayList<AABB>();
        for (var candidate : craft.level().getBlockCollisions(craft, sweep)) obstacles.addAll(candidate.toAabbs());
        if (obstacles.isEmpty()) return;
        for (var local : group.shapes) {
            var body = shape(local, pose);
            var volume = broad(body).expandTowards(travel);
            for (var terrain : obstacles) {
                if (volume.intersects(terrain)) contacts.add(new Contact(body, shape(terrain, RigidTransform.IDENTITY), group.wheel));
            }
        }
    }

    int launchTicks() { return Math.max(5, (int)Math.ceil(activeRadius() * .31) + 2); }

    double ceiling(NativeGroundVehicle craft, float yaw, float pitch) {
        return craft.level().getMaxBuildHeight() - bounds(Vec3.ZERO, yaw, pitch, craft.facing()).maxY;
    }

    float clearance(NativeGroundVehicle craft) {
        if (craft.getY() >= com.vandorlabs.physics.FlightEnvelope.WORLD_CEILING) return -1;
        var world = craft.level();
        var box = craft.getBoundingBox();
        if (!loadedColumns(world, box)) return -1;
        var pos = new BlockPos.MutableBlockPos();
        double highest = Double.NEGATIVE_INFINITY;
        int top = Math.min(world.getMaxBuildHeight() - 1, Mth.floor(box.minY));
        for (int x = Mth.floor(box.minX + .001); x <= Mth.floor(box.maxX - .001); x++) {
            for (int z = Mth.floor(box.minZ + .001); z <= Mth.floor(box.maxZ - .001); z++) {
                var chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
                int start = Math.min(top, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15));
                for (int y = start; y >= world.getMinBuildHeight() && y + 1 > highest; y--) {
                    pos.set(x, y, z);
                    var state = chunk.getBlockState(pos);
                    if (state.isAir() || state.liquid()) continue;
                    var shape = state.getCollisionShape(world, pos);
                    if (shape.isEmpty()) continue;
                    double surface = y + shape.max(Direction.Axis.Y);
                    if (surface <= box.minY + .001) highest = Math.max(highest, surface);
                }
                if (highest >= box.minY - .001) return 0;
            }
        }
        return highest == Double.NEGATIVE_INFINITY ? -1 : (float)Math.max(0, box.minY - highest);
    }

    boolean groundWithin(NativeGroundVehicle craft, double distance) {
        var contacts = contacts(craft, craft.position(), craft.getYRot(), craft.hullPitch(), new Vec3(0, -distance, 0));
        return contacts != null && blocked(contacts, new Vec3(0, -distance, 0), 1);
    }

    FlightContact flightContact(NativeGroundVehicle craft, float yaw, float pitch, Vec3 travel) {
        float yawChange = Mth.wrapDegrees(yaw - craft.getYRot());
        float pitchChange = pitch - craft.hullPitch();
        int steps = Math.max(1, (int)Math.ceil(activeRadius() * Math.toRadians(Math.abs(yawChange) + Math.abs(pitchChange)) / .15));
        FlightContact result = FlightContact.CLEAR;
        for (int step = 1; step <= steps; step++) {
            float fraction = (float)step / steps;
            var contacts = contacts(craft, craft.position(), craft.getYRot() + yawChange * fraction,
                    craft.hullPitch() + pitchChange * fraction, travel);
            if (contacts == null) return FlightContact.OBSTRUCTION;
            for (var contact : contacts) {
                if (!contact.hull.sweepsInto(contact.terrain, travel.x, travel.y, travel.z)) continue;
                if (!contact.wheel) return FlightContact.OBSTRUCTION;
                result = FlightContact.WHEEL;
            }
        }
        return result;
    }

    float turn(NativeGroundVehicle craft, float yaw, float amount) {
        if (amount == 0) return yaw;
        int steps = Math.max(1, (int)Math.ceil(activeRadius() * Math.abs(Math.toRadians(amount)) / .15));
        float accepted = yaw;
        for (int step = 1; step <= steps; step++) {
            float angle = yaw + amount * step / steps;
            var contacts = contacts(craft, craft.position(), angle, craft.hullPitch(), Vec3.ZERO);
            if (contacts == null || blocked(contacts, Vec3.ZERO, 0)) break;
            accepted = angle;
        }
        return accepted;
    }

    double clip(NativeGroundVehicle craft, Vec3 at, float yaw, double amount, Direction.Axis axis) {
        if (amount == 0) return 0;
        var travel = switch (axis) {
            case X -> new Vec3(amount, 0, 0);
            case Y -> new Vec3(0, amount, 0);
            case Z -> new Vec3(0, 0, amount);
        };
        var contacts = contacts(craft, at, yaw, craft.hullPitch(), travel);
        if (contacts == null) return 0;
        if (!blocked(contacts, travel, 1)) return amount;
        double left = 0, right = 1;
        for (int step = 0; step < 13; step++) {
            double middle = (left + right) / 2;
            if (blocked(contacts, travel, middle)) right = middle;
            else left = middle;
        }
        return Math.abs(amount * left) < EPSILON ? 0 : amount * left;
    }

    BlockPos hit(Vec3 start, Vec3 end, BlockPos riddenSeat) {
        BlockPos result = null;
        double distance = start.distanceToSqr(end);
        var upper = riddenSeat == null ? null : riddenSeat.above();
        for (var target : targets) {
            if (target.pos.equals(riddenSeat) || target.pos.equals(upper)) continue;
            for (var shape : target.shapes) {
                var hit = shape.clip(start, end);
                if (hit.isEmpty()) continue;
                double next = start.distanceToSqr(hit.get());
                if (next < distance) {
                    distance = next;
                    result = target.pos;
                }
            }
        }
        return result;
    }

    private static boolean blocked(List<Contact> contacts, Vec3 travel, double fraction) {
        for (var pair : contacts) {
            if (pair.hull.sweepsInto(pair.terrain, travel.x * fraction, travel.y * fraction, travel.z * fraction)) return true;
        }
        return false;
    }

    private record SnapshotBlocks(Map<BlockPos, BlockState> states) implements BlockGetter {
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 128; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
