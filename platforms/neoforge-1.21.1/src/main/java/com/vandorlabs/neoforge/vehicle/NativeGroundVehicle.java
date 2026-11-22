package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.persistence.ChairSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/** Controller-anchored carried vehicle. Only authenticated pilot commands can drive server motion. */
public final class NativeGroundVehicle extends Entity implements IEntityWithComplexSpawn {
    private static final String SAVE_KEY = "Vehicle";
    private static final int SCHEMA = 1;
    private static final Set<String> ENTITY_FIELDS = Set.of("id", "UUID", "Pos", "Motion", "Rotation", "FallDistance",
            "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown", "CustomName", "CustomNameVisible",
            "Silent", "NoGravity", "Glowing", "TicksFrozen", "HasVisualFire", "Tags", "Passengers",
            "NeoForgeData", "neoforge:attachments");
    private static final double TAXI_SPEED = .30;
    private static final EntityDataAccessor<Boolean> READY = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> PILOT = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> CUSHION = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);

    private NativeTransferSnapshot snapshot;
    private NativeVehicleHull hull;
    private NativeVehicleVisual visual;
    private CompoundTag visualTag;
    private CompoundTag retained = new CompoundTag();
    private BlockPos pilot;
    private Direction heading;
    private UUID owner;
    private double speed, hoverTarget;
    private long inputAt = Long.MIN_VALUE, usedAt = Long.MIN_VALUE;
    private int keys;
    private Vec3 target;
    private float targetYaw;
    private int interpolation;

    public NativeGroundVehicle(EntityType<? extends NativeGroundVehicle> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(READY, false);
        data.define(PILOT, BlockPos.ZERO);
        data.define(CUSHION, (float) ChairSettings.pilotCushion(0));
        data.define(FACING, Direction.NORTH);
        data.define(MODE, (byte) 0);
        data.define(PITCH, 0F);
        data.define(SPEED, 0F);
    }

    public NativeTransferSnapshot snapshot() { return snapshot; }
    public NativeVehicleVisual visual() { return visual; }
    public BlockPos pilotSeat() { return pilot != null ? pilot : entityData.get(PILOT); }
    public Direction facing() { return heading != null ? heading : entityData.get(FACING); }
    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public int mode() { return entityData.get(MODE); }
    public float hullPitch() { return entityData.get(PITCH); }
    public float driveSpeed() { return entityData.get(SPEED); }
    public boolean admitted() { return entityData.get(READY) && hull != null; }

    public void install(NativeTransferSnapshot blocks, BlockPos localPilot, Direction facing) {
        NativeVehicleStructure.validate(blocks, localPilot, facing);
        var states = new LinkedHashMap<BlockPos, BlockState>();
        CompoundTag seat = null;
        for (var cell : blocks.cells()) {
            states.put(cell.pos, cell.state);
            if (cell.pos.equals(localPilot)) seat = cell.tile();
        }
        int height = seat == null ? 0 : Mth.clamp(seat.getInt(ChairSettings.PILOT_HEIGHT), 0, 3);
        var geometry = new NativeVehicleHull(states);
        var appearance = NativeVehicleVisual.project(blocks, level().registryAccess());
        snapshot = blocks;
        hull = geometry;
        visualTag = appearance;
        pilot = localPilot.immutable();
        heading = facing;
        entityData.set(PILOT, pilot);
        entityData.set(FACING, heading);
        entityData.set(CUSHION, (float) ChairSettings.pilotCushion(height));
        entityData.set(READY, true);
        setBoundingBox(makeBoundingBox());
    }

    public boolean canPark() {
        return snapshot != null && admitted() && isAlive() && getPassengers().isEmpty() && mode() == 0
                && onGround() && Math.abs(speed) < .001 && getDeltaMovement().lengthSqr() < 1e-6;
    }

    @Override protected AABB makeBoundingBox() {
        return hull == null ? super.makeBoundingBox() : hull.bounds(position(), getYRot(), hullPitch(), facing());
    }

    @Override public boolean isPickable() { return admitted() && isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }

    @Override protected boolean canAddPassenger(Entity rider) {
        return rider instanceof Player && admitted() && getPassengers().isEmpty()
                && (level().isClientSide || !NativeVehicleTransfer.escrowed(this));
    }

    // Leave Entity.getControllingPassenger() at its default. Vanilla accepts arbitrary vehicle poses from that rider.
    private ServerPlayer pilot() { return getFirstPassenger() instanceof ServerPlayer rider ? rider : null; }

    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        BlockPos at = pilotSeat();
        return NativeVehicleHull.rotate(new Vec3(at.getX(), at.getY() + entityData.get(CUSHION), at.getZ()), getYRot(), hullPitch(), facing());
    }

    @Override protected void removePassenger(Entity rider) {
        super.removePassenger(rider);
        keys = 0;
        inputAt = Long.MIN_VALUE;
    }

    @Override public InteractionResult interact(Player actor, InteractionHand hand) {
        if (!admitted() || hand != InteractionHand.MAIN_HAND || !actor.isAlive() || actor.isSpectator()
                || !level().isClientSide && NativeVehicleTransfer.escrowed(this)) return InteractionResult.PASS;
        BlockPos target = hit(actor);
        if (target == null) return InteractionResult.PASS;
        if (controllerHit(actor, target)) {
            return NativeVehicleBindings.open(actor, this) ? InteractionResult.sidedSuccess(level().isClientSide) : InteractionResult.PASS;
        }
        if ((target.equals(pilotSeat()) || target.equals(pilotSeat().above())) && !actor.isPassenger()) {
            if (!level().isClientSide && (owner == null || owner.equals(actor.getUUID()) || actor.isCreative())) actor.startRiding(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override public InteractionResult interactAt(Player actor, Vec3 hit, InteractionHand hand) {
        return interact(actor, hand);
    }

    private static boolean controllerHit(Player actor, BlockPos target) {
        return target.equals(BlockPos.ZERO) || actor.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get());
    }

    public boolean pointsAtMountedControl(Player actor) {
        BlockPos target = hit(actor);
        return target != null && controllerHit(actor, target);
    }

    public BlockPos hit(Player actor) {
        if (hull == null) return null;
        Vec3 start = actor.getEyePosition();
        Vec3 end = start.add(actor.getLookAngle().scale(Math.min(8, actor.entityInteractionRange())));
        if (!NativeVehicleHull.loadedColumns(level(), new AABB(start, end).inflate(1e-5))) return null;
        var terrain = level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, actor));
        if (terrain.getType() != HitResult.Type.MISS) end = terrain.getLocation();
        return hull.hit(NativeVehicleHull.inverse(start, position(), getYRot(), hullPitch(), facing()),
                NativeVehicleHull.inverse(end, position(), getYRot(), hullPitch(), facing()),
                actor.getVehicle() == this ? pilotSeat() : null);
    }

    void input(ServerPlayer actor, int mask) {
        if (actor != pilot() || actor.level() != level() || actor.isSpectator() || !actor.isAlive()
                || !admitted() || NativeVehicleTransfer.escrowed(this)) return;
        long now = level().getGameTime();
        if (mask == NativeVehicleInput.USE) {
            if (now != usedAt && actor.containerMenu == actor.inventoryMenu) interact(actor, InteractionHand.MAIN_HAND);
            usedAt = now;
            return;
        }
        keys = mask;
        inputAt = now;
    }

    @Override public void tick() {
        super.tick();
        if (!admitted()) return;
        if (level().isClientSide) {
            tickInterpolation();
            return;
        }
        if (NativeVehicleTransfer.escrowed(this)) return;
        drive();
    }

    private void drive() {
        boolean active = pilot() != null && inputAt >= 0 && level().getGameTime() - inputAt <= 10;
        int motion = active ? keys : 0;
        int forward = ((motion & NativeVehicleInput.FORWARD) != 0 ? 1 : 0) - ((motion & NativeVehicleInput.BACK) != 0 ? 1 : 0);
        int strafe = ((motion & NativeVehicleInput.RIGHT) != 0 ? 1 : 0) - ((motion & NativeVehicleInput.LEFT) != 0 ? 1 : 0);
        boolean braking = (motion & NativeVehicleInput.BRAKE) != 0;
        speed = approach(speed, braking ? 0 : forward * TAXI_SPEED, braking || !active ? .045 : forward == 0 ? .008 : .018);
        float yaw = hull.turn(this, getYRot(), (float) (strafe * speed * 11 * (1 - .45 * Math.abs(speed) / .28)));
        setYRot(Mth.wrapDegrees(yaw));
        Vec3 at = position();
        Vec3 heading = NativeVehicleHull.rotate(new Vec3(facing().getStepX(), 0, facing().getStepZ()), yaw, 0, facing());
        double dx = heading.x * speed, dz = heading.z * speed, dy = Math.max(-.5, getDeltaMovement().y - .04);
        double movedY = hull.clip(this, at, yaw, dy, Direction.Axis.Y);
        double movedX = hull.clip(this, at.add(0, movedY, 0), yaw, dx, Direction.Axis.X);
        double movedZ = hull.clip(this, at.add(movedX, movedY, 0), yaw, dz, Direction.Axis.Z);
        boolean supported = dy < 0 && movedY != dy
                && NativeVehicleHull.loadedColumns(level(), hull.bounds(at, yaw, hullPitch(), facing()).move(0, dy, 0));
        if (supported && (movedX != dx || movedZ != dz)) {
            double up = hull.clip(this, at, yaw, .5, Direction.Axis.Y);
            if (up > .001) {
                double stepX = hull.clip(this, at.add(0, up, 0), yaw, dx, Direction.Axis.X);
                double stepZ = hull.clip(this, at.add(stepX, up, 0), yaw, dz, Direction.Axis.Z);
                double down = hull.clip(this, at.add(stepX, up, stepZ), yaw, -up - .04, Direction.Axis.Y);
                if (stepX * stepX + stepZ * stepZ > movedX * movedX + movedZ * movedZ && down > -up - .04) {
                    movedX = stepX;
                    movedY = up + down;
                    movedZ = stepZ;
                }
            }
        }
        if (movedX != dx || movedZ != dz) speed = 0;
        setOnGround(supported);
        setPos(at.x + movedX, at.y + movedY, at.z + movedZ);
        setBoundingBox(makeBoundingBox());
        setDeltaMovement(movedX, supported || movedY != dy ? 0 : movedY, movedZ);
        entityData.set(SPEED, (float)speed);
        fallDistance = 0;
    }

    private static double approach(double value, double target, double amount) {
        return value + Mth.clamp(target - value, -amount, amount);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int ticks) {
        if (distanceToSqr(x, y, z) > 16384) {
            super.lerpTo(x, y, z, yaw, pitch, ticks);
            interpolation = 0;
            return;
        }
        target = new Vec3(x, y, z);
        targetYaw = yaw;
        interpolation = Math.max(1, ticks);
    }

    private void tickInterpolation() {
        if (interpolation == 0) return;
        Vec3 point = position().lerp(target, 1D / interpolation);
        setYRot(Mth.rotLerp(1F / interpolation, getYRot(), targetYaw));
        setPos(point.x, point.y, point.z);
        setBoundingBox(makeBoundingBox());
        interpolation--;
    }

    @Override public double lerpTargetX() { return interpolation > 0 ? target.x : getX(); }
    @Override public double lerpTargetY() { return interpolation > 0 ? target.y : getY(); }
    @Override public double lerpTargetZ() { return interpolation > 0 ? target.z : getZ(); }
    @Override public float lerpTargetYRot() { return interpolation > 0 ? targetYaw : getYRot(); }

    @Override public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(visualTag);
    }

    @Override public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        try {
            if (!(buffer.readNbt(NbtAccounter.create(NativeVehicleVisual.MAX_BYTES)) instanceof CompoundTag tag) || buffer.isReadable()) return;
            var appearance = NativeVehicleVisual.read(tag, buffer.registryAccess());
            var states = new LinkedHashMap<BlockPos, BlockState>();
            for (var cell : appearance.cells()) states.put(cell.pos(), cell.state());
            hull = new NativeVehicleHull(states);
            visual = appearance;
            setBoundingBox(makeBoundingBox());
        } catch (RuntimeException invalidAppearance) {
            hull = null;
            visual = null;
        }
    }

    public record SavedVehicle(NativeTransferSnapshot snapshot, BlockPos pilot, Direction facing, UUID owner,
                               int mode, double speed, double hoverTarget, float pitch) {}

    public static SavedVehicle readSavedVehicle(CompoundTag entityTag, HolderLookup.Provider registries) {
        if (!(entityTag.get(SAVE_KEY) instanceof CompoundTag data) || !data.contains("Schema", Tag.TAG_INT)
                || data.getInt("Schema") != SCHEMA || !data.contains("Blocks", Tag.TAG_COMPOUND)
                || !data.contains("Pilot", Tag.TAG_LONG) || !data.contains("Facing", Tag.TAG_INT)
                || !data.contains("Mode", Tag.TAG_BYTE) || !data.contains("Speed", Tag.TAG_DOUBLE)
                || !data.contains("HoverTarget", Tag.TAG_DOUBLE) || !data.contains("Pitch", Tag.TAG_FLOAT)
                || data.contains("Owner") && !data.hasUUID("Owner")) {
            throw new IllegalArgumentException("Unsupported native vehicle snapshot");
        }
        int face = data.getInt("Facing");
        int mode = data.getByte("Mode");
        double speed = data.getDouble("Speed"), hoverTarget = data.getDouble("HoverTarget");
        float pitch = data.getFloat("Pitch");
        if (face < 2 || face > 5 || mode != 0 || !Double.isFinite(speed) || Math.abs(speed) > TAXI_SPEED
                || !Double.isFinite(hoverTarget) || pitch != 0) throw new IllegalArgumentException("Invalid native vehicle pose");
        var blocks = NativeTransferSnapshot.read(registries, data.getCompound("Blocks"), NativeVehicleStructure.LIMITS, tag -> Map.of());
        var pilot = BlockPos.of(data.getLong("Pilot"));
        var facing = Direction.from3DDataValue(face);
        NativeVehicleStructure.validate(blocks, pilot, facing);
        return new SavedVehicle(blocks, pilot, facing, data.hasUUID("Owner") ? data.getUUID("Owner") : null, mode, speed, hoverTarget, pitch);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        retained = new CompoundTag();
        for (String key : tag.getAllKeys()) {
            if (!ENTITY_FIELDS.contains(key)) retained.put(key, tag.get(key).copy());
        }
        entityData.set(READY, false);
        snapshot = null;
        hull = null;
        visual = null;
        visualTag = null;
        try {
            var saved = readSavedVehicle(tag, level().registryAccess());
            install(saved.snapshot, saved.pilot, saved.facing);
            owner = saved.owner;
            speed = saved.speed;
            hoverTarget = saved.hoverTarget;
            entityData.set(SPEED, (float)speed);
            retained.remove(SAVE_KEY);
        } catch (RuntimeException | LinkageError unavailable) {
            // The original payload stays on the entity for repair or a later compatible loader.
            snapshot = null;
            hull = null;
            visualTag = null;
            entityData.set(READY, false);
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.merge(retained);
        if (snapshot == null) return;
        var data = new CompoundTag();
        data.putInt("Schema", SCHEMA);
        data.put("Blocks", snapshot.write());
        data.putLong("Pilot", pilotSeat().asLong());
        data.putInt("Facing", facing().get3DDataValue());
        if (owner != null) data.putUUID("Owner", owner);
        data.putByte("Mode", (byte)mode());
        data.putDouble("Speed", speed);
        data.putDouble("HoverTarget", hoverTarget);
        data.putFloat("Pitch", hullPitch());
        tag.put(SAVE_KEY, data);
    }
}
