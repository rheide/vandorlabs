package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.gear.core.ArmHinge;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.neoforge.gear.*;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Detached, independently mounted columns. Endpoints stay owned by the hull, including while the arm moves. */
final class NativeVehicleArms {
    private static final int DYNAMIC_BYTES = 256;
    private static final int MAX_ROOTS = 32;
    private static final List<String> APPEARANCE = List.of("GearController", "GearColumns", "GearKind",
        "GearHeight", "GearFeet", "GearOwnerOffset", "GearDirection", "ArmMountDirection", "ArmHingeMode",
        "ArmMiddleHinge", "ArmKinkDirection", "ArmRotatePayload", "ArmStartAngle", "ArmActiveAngle",
        "ArmActiveExtension", "ArmLandingAnglesVersion", "ArmRetractingStroke", "GearProgress",
        "GearTargetDeployed", ArmResource.SAVED, ArmResource.SAVED_OUT);
    private final List<Arm> arms;
    private final Map<BlockPos, Arm> roots;
    private final Map<BlockPos, NativeRawCell> hull;
    private int revision, collisionRevision = -1;
    private List<AABB> collision = List.of();

    NativeVehicleArms(Map<BlockPos, NativeRawCell> hull, HolderLookup.Provider registries) {
        this.hull = hull;
        var values = new ArrayList<Arm>();
        for (var cell : hull.values()) {
            if (!(cell.state.getBlock() instanceof NativeGearBlock block)) continue;
            var raw = cell.tile();
            if (!raw.getBoolean("GearController")) continue;
            if (!(block.newBlockEntity(cell.pos, cell.state) instanceof NativeGearEntity detached)) {
                throw new IllegalArgumentException("Missing carried Arm factory");
            }
            detached.loadWithComponents(raw, registries);
            if (detached.inert() || !detached.controller() || !detached.canTransferResources() || detached.ownsCaptured()) {
                throw new IllegalArgumentException("Invalid native carried Arm owner");
            }
            values.add(new Arm(cell, detached, raw));
        }
        values.sort(Comparator.comparing(a -> a.cell.pos));
        arms = List.copyOf(values);
        var owners = new HashMap<BlockPos, Arm>();
        for (var arm : arms) owners.put(arm.cell.pos, arm);
        roots = Map.copyOf(owners);
    }

    static boolean block(BlockState state) {
        return state.getBlock() instanceof NativeGearBlock block && (block.definition().arm()
            || block.definition().kind == NativeGearDefinition.Kind.MOUNT);
    }

    static CompoundTag appearance(CompoundTag raw) {
        var result = new CompoundTag();
        for (var key : APPEARANCE) if (raw.contains(key)) result.put(key, raw.get(key).copy());
        return result;
    }

    static void validate(Map<BlockPos, NativeRawCell> hull) {
        var members = new HashSet<BlockPos>();
        var roots = new ArrayList<NativeRawCell>();
        var owners = new HashSet<UUID>();
        for (var cell : hull.values()) {
            if (!block(cell.state)) continue;
            var raw = cell.tile();
            if (raw == null || !raw.hasUUID("NativeGearIdentity") || !owners.add(raw.getUUID("NativeGearIdentity"))
                || !raw.contains("GearOwnerOffset", Tag.TAG_LONG) || !raw.contains("ArmMountDirection", Tag.TAG_STRING)
                || raw.getBoolean("NativeGearInert") || raw.getBoolean("ArmAdjustingStart") || raw.contains("NativeGearPayload")
                || raw.contains("ArmPayload") || raw.contains("ArmStoredSections") || raw.contains("NativeGearStoredSections")) {
                throw new IllegalArgumentException("Carry only independent Arms without payload or hidden sections");
            }
            if (raw.getBoolean("GearController")) roots.add(cell); else members.add(cell.pos);
        }
        if (roots.size() > MAX_ROOTS) throw new IllegalArgumentException("Vehicle has too many independent Arms (maximum 32)");
        for (var root : roots) {
            var raw = root.tile();
            var settings = NativeGearSettings.readSaved(raw);
            var columns = raw.getList("GearColumns", Tag.TAG_INT_ARRAY);
            int height = raw.getInt("GearHeight");
            double progress = raw.getDouble("GearProgress");
            var mount = Direction.valueOf(raw.getString("ArmMountDirection"));
            var support = root.pos.relative(mount.getOpposite());
            int[] only = columns.isEmpty() ? new int[0] : columns.getIntArray(0);
            if (!NativeGearEntity.savedOwnerOffset(raw).equals(BlockPos.ZERO) || settings.join() || settings.carryLoad()
                || settings.pushBlocks() || !raw.getString("GearKind").equals(GearTopology.FootKind.NONE.name())
                || columns.size() != 1 || !Arrays.equals(only, new int[3])
                || height < 1 || height > GearTopology.MAX_COLUMN_HEIGHT || !hull.containsKey(support)
                || raw.sizeInBytes() > NativeVehicleStructure.LIMITS.tileBytes() - DYNAMIC_BYTES
                || !raw.contains("GearProgress", Tag.TAG_DOUBLE) || !Double.isFinite(progress) || progress < 0 || progress > 1) {
                throw new IllegalArgumentException("Carry one complete independent Arm column with its mounting block");
            }
            for (int i = 1; i < height; i++) {
                var position = root.pos.relative(mount, i);
                var column = hull.get(position);
                if (!members.remove(position) || column == null || !column.state.is(root.state.getBlock())) {
                    throw new IllegalArgumentException("Vehicle is missing part of its native Arm column");
                }
                var member = column.tile();
                if (!member.getString("ArmMountDirection").equals(mount.name())
                    || !NativeGearEntity.savedOwnerOffset(member).equals(root.pos.subtract(position))) {
                    throw new IllegalArgumentException("Vehicle Arm section belongs to another native column");
                }
            }
        }
        if (!members.isEmpty()) throw new IllegalArgumentException("Vehicle Arm section has no carried controller");
    }

    static void rotate(CompoundTag raw, Rotation rotation) {
        var mount = Direction.valueOf(raw.getString("ArmMountDirection"));
        var nextMount = rotation.rotate(mount);
        raw.putString("ArmMountDirection", nextMount.name());
        NativeGearEntity.rotateSavedOwnerOffset(raw, rotation);
        for (var field : List.of("GearDirection", "ArmKinkDirection")) if (raw.contains(field, Tag.TAG_STRING)) {
            var direction = GearMotion.Direction.valueOf(raw.getString(field));
            var old = NativeGearOrientation.vector(mount, new BlockPos(direction.axisX(), 0, direction.axisZ()));
            var local = NativeGearOrientation.inverse(nextMount, old.rotate(rotation));
            for (var candidate : GearMotion.Direction.values()) {
                if (candidate.axisX() == local.getX() && candidate.axisZ() == local.getZ()) {
                    raw.putString(field, candidate.name());
                    break;
                }
            }
        }
    }

    boolean readyToPark() {
        for (var arm : arms) if (arm.gear.progress() != 1 || !arm.gear.target() || arm.gear.blocked()) return false;
        return true;
    }

    int reservedBytes() { return arms.size() * DYNAMIC_BYTES; }

    CompoundTag poses() {
        var result = new CompoundTag();
        for (var arm : arms) {
            var gear = arm.gear;
            if (gear.mode() == GearMotion.Mode.STATIC) continue;
            int phase = (int)Math.round(gear.progress() * 65535);
            if (gear.target()) phase |= 1 << 16;
            if (gear.retractionStroke()) phase |= 1 << 17;
            result.putInt(Long.toString(arm.cell.pos.asLong()), phase);
        }
        return result;
    }

    List<AABB> collision() {
        if (collisionRevision != revision) {
            var result = new ArrayList<AABB>();
            for (var arm : arms) for (var box : arm.gear.interactionBoxes(arm.gear.progress())) {
                result.add(box.move(-.5, 0, -.5));
            }
            collision = List.copyOf(result);
            collisionRevision = revision;
        }
        return collision;
    }
    int revision() { return revision; }
    List<Arm> arms() { return arms; }

    NativeRawCell updated(NativeRawCell cell, HolderLookup.Provider registries) {
        var arm = roots.get(cell.pos);
        return arm == null || !arm.changed ? cell : new NativeRawCell(cell.pos, cell.state,
            arm.gear.saveWithFullMetadata(registries), NativeVehicleStructure.LIMITS.tileBytes());
    }

    void tick(NativeGroundVehicle craft, NativeVehicleResources resources) {
        for (var arm : arms) {
            var gear = arm.gear;
            if (gear.mode() == GearMotion.Mode.STATIC || gear.settings().trigger() == 0) continue;
            boolean power;
            var channels = arm.channels;
            if (channels.isEmpty()) power = resources.localLevel(arm.cell.pos) > 0;
            else {
                boolean any = gear.settings().legacyAnyChannels();
                power = !any;
                for (var channel : channels) {
                    boolean signal = resources.channelLevel(craft, arm.cell.pos, channel) > 0;
                    power = any ? power || signal : power && signal;
                    if (power == any) break;
                }
            }
            boolean target = gear.target(), blocked = gear.blocked();
            if (!arm.known || arm.power != power) {
                target = gear.settings().trigger() == 2 ? power : !power;
                blocked = false;
            }
            var phase = gear.progress();
            boolean retracting = gear.definition().kind == NativeGearDefinition.Kind.EXTENDER
                && (gear.retractionStroke() || phase == 1 && !target);
            if (!blocked && phase != (target ? 1 : 0)) {
                // The native latch changes the arm's geometry before the first retracting step.
                gear.carriedPose(phase, target, retracting, power, false);
                double next = GearMotion.advance(phase, target, 1, NativeGearEntity.DURATION);
                if (clear(craft, arm, phase, next)) phase = next;
                else blocked = true;
            }
            if (arm.phase != phase || arm.target != target || arm.power != power || !arm.known || arm.blocked != blocked) {
                gear.carriedPose(phase, target, retracting && !(phase == 1 && target), power, blocked);
                arm.phase = phase;
                arm.target = target;
                arm.power = power;
                arm.known = true;
                arm.blocked = blocked;
                arm.changed = true;
                revision++;
            }
        }
    }

    private boolean clear(NativeGroundVehicle craft, Arm owner, double before, double after) {
        var first = NativeGearGeometry.mechanismBoxes(owner.gear, before);
        var second = NativeGearGeometry.mechanismBoxes(owner.gear, after);
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i).equals(second.get(i))) continue;
            var sweep = NativeGearGeometry.swept(owner.gear, first.get(i), second.get(i), before, after);
            if (sweep.getXsize() <= 0 || sweep.getYsize() <= 0 || sweep.getZsize() <= 0) continue;
            for (var at : BlockPos.betweenClosed(BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))) {
                if (owner.clearCells.contains(at) || !hull.containsKey(at)) continue;
                var cell = new AABB(at);
                if (!cell.intersects(sweep)) continue;
                // A single level hinge starting at zero moves the socket monotonically away from
                // its home face along the mounting axis. Arc padding can overlap an exactly flush dock.
                boolean socket = i == first.size() - 1;
                if (socket && at.equals(owner.levelDock) && !cell.intersects(first.get(i).minmax(second.get(i)))) continue;
                return false;
            }
            for (var other : arms) {
                if (other == owner) continue;
                for (var box : other.gear.interactionBoxes(other.gear.progress())) if (box.intersects(sweep)) return false;
            }
            var physical = NativeVehicleHull.worldBox(craft, sweep.move(-.5, 0, -.5));
            if (!NativeVehicleHull.loadedColumns(craft.level(), physical)
                || !craft.level().getWorldBorder().isWithinBounds(physical)
                || craft.level().getBlockCollisions(craft, physical).iterator().hasNext()
                || !craft.level().getEntities(craft, physical, entity -> !entity.isSpectator() && entity.getRootVehicle() != craft).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static final class Arm {
        final NativeRawCell cell;
        final NativeGearEntity gear;
        final List<ChannelList> channels;
        final Set<BlockPos> clearCells;
        final BlockPos levelDock;
        boolean target, blocked, power, known, changed;
        double phase;

        Arm(NativeRawCell cell, NativeGearEntity gear, CompoundTag raw) {
            this.cell = cell;
            this.gear = gear;
            phase = gear.progress();
            target = gear.target();
            blocked = gear.blocked();
            known = raw.getBoolean("GearSignalKnown");
            power = raw.getBoolean("GearLastSignal");
            var bank = gear.settings().channels();
            var selected = new ArrayList<ChannelList>();
            for (int i = 0; i < bank.size(); i++) selected.add(ChannelList.of(bank.get(i)));
            channels = List.copyOf(selected);
            var exact = new HashSet<>(gear.members());
            exact.add(cell.pos.relative(gear.mount().getOpposite()));
            clearCells = Set.copyOf(exact);
            var settings = gear.settings();
            var dock = gear.mode() == GearMotion.Mode.ROTATE_LEVEL && settings.hingeMode() == ArmHinge.Mode.SINGLE
                && settings.startAngle() == 0 && Math.abs(settings.activeAngle()) <= 90
                && settings.resourceTransfer() != ArmResource.DISABLED ? NativeGearResourceTransfer.carriedHome(gear) : null;
            levelDock = dock == null ? null : dock.cell();
        }
    }
}
