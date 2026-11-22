package com.vandorlabs.neoforge.vehicle;

import java.util.HashSet;
import com.vandorlabs.neoforge.builders.NativeDriveBindings;
import com.vandorlabs.neoforge.builders.NativeDriveDesign;
import com.vandorlabs.neoforge.builders.NativeBuilderCoreBlock;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.persistence.ChairSettings;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.screen.VehicleStatusLayout;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
    private static final Set<String> VEHICLE_FIELDS = Set.of("Schema", "Blocks", "Pilot", "Facing", "Owner", "Mode",
            "Speed", "HoverTarget", "Pitch", "FlightPhase", "LaunchTicks", "PilotAssignment", "Cruise");
    private static final Set<String> ENTITY_FIELDS = Set.of("id", "UUID", "Pos", "Motion", "Rotation", "FallDistance",
            "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown", "CustomName", "CustomNameVisible",
            "Silent", "NoGravity", "Glowing", "TicksFrozen", "HasVisualFire", "Tags", "Passengers",
            "NeoForgeData", "neoforge:attachments");
    public static final int TAXI = 0, HOVER = 1, FLIGHT = 2;
    private static final int NO_FLIGHT = 0, RUNWAY = 1, CRUISE = 2, LANDING = 3, RETURN_HOVER = 4, CRUISE_RETURN_HOVER = 5;
    private static final double TAXI_SPEED = .30, POWERED_TAXI_SPEED = .40, HOVER_SPEED = .80, BOOST_SPEED = 1.2;
    private static final EntityDataAccessor<Boolean> READY = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> ARM_POSES = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<BlockPos> PILOT = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> CUSHION = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CLEARANCE = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> FLIGHT_READOUT = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<BlockPos> CRUISE_ROUTE = SynchedEntityData.defineId(NativeGroundVehicle.class, EntityDataSerializers.BLOCK_POS);

    private final NativeVehicleCruise cruise = new NativeVehicleCruise(this);
    private NativeTransferSnapshot snapshot;
    private NativeVehicleHull hull;
    private NativeVehiclePropulsion propulsion;
    private NativeVehicleResources resources;
    private int armRevision;
    private NativeVehicleMeters meters;
    private static final EntityDataAccessor<CompoundTag> METER_LEVELS = SynchedEntityData.defineId(NativeGroundVehicle.class,EntityDataSerializers.COMPOUND_TAG);
    public int meterSurface(BlockPos pos,int slot){return meters==null?0:meters.surface(pos,slot);}
    private NativeVehicleVisual visual;
    private List<NativeVehicleStatus.Panel> status = List.of();
    private Set<BlockPos> clientEngineCells = Set.of();
    private CompoundTag visualTag;
    private NativeVehiclePropulsion.Readout clientReadout = NativeVehiclePropulsion.Readout.EMPTY;
    private final Map<Integer, Integer> clientGauges = new HashMap<>();
    private CompoundTag retained = new CompoundTag();
    private BlockPos pilot;
    private Direction heading;
    private UUID owner, savedPilot, emergencyOwner;
    private double speed, hoverTarget;
    private int flightPhase, launchTicks;
    private long inputAt = Long.MIN_VALUE, usedAt = Long.MIN_VALUE, awaitingPilotUntil = Long.MIN_VALUE;
    private long emergencyDeadline;
    private int keys;
    private Vec3 target;
    private float targetYaw, clientPitch, previousClientPitch;
    private int interpolation;

    public NativeGroundVehicle(EntityType<? extends NativeGroundVehicle> type, Level level) {
        super(type, level);
    }

    @Override public void onAddedToLevel() {
        super.onAddedToLevel();
        NativeVehicleObservers.updateCruise(this);
    }

    @Override public void onRemovedFromLevel() {
        NativeVehicleObservers.removeCruise(this);
        super.onRemovedFromLevel();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(READY, false);
        data.define(PILOT, BlockPos.ZERO);
        data.define(CUSHION, (float) ChairSettings.pilotCushion(0));
        data.define(FACING, Direction.NORTH);
        data.define(MODE, (byte) 0);
        data.define(PITCH, 0F);
        data.define(SPEED, 0F);
        data.define(METER_LEVELS,new CompoundTag());
        data.define(ARM_POSES, new CompoundTag());
        data.define(FLIGHT_READOUT, new CompoundTag());
        data.define(CLEARANCE, -1F);
        data.define(CRUISE_ROUTE, BlockPos.ZERO);
    }

    public NativeTransferSnapshot snapshot() {
        var registries = level().registryAccess();
        var saved = propulsion == null ? snapshot : propulsion.snapshot(registries);
        if (resources != null) saved = resources.snapshot(saved, registries);
        return meters == null ? saved : meters.snapshot(saved);
    }

    NativeVehicleResources resources() { return resources; }
    public NativeVehicleVisual visual() { return visual; }
    public List<NativeVehicleStatus.Panel> statusPanels() { return status; }
    public BlockPos pilotSeat() { return pilot != null ? pilot : entityData.get(PILOT); }
    public Direction facing() { return heading != null ? heading : entityData.get(FACING); }
    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public int mode() { return entityData.get(MODE); }
    public float hullPitch() { return entityData.get(PITCH); }
    public float renderPitch(float partial) { return Mth.lerp(partial, previousClientPitch, clientPitch); }
    public Vec3 rotateLocalDirection(Vec3 direction, float yaw, float pitch) {
        return NativeVehicleHull.rotate(direction, yaw, pitch, facing());
    }
    public float driveSpeed() { return entityData.get(SPEED); }
    public float groundClearance() { return highFlight() || flightAltitude() >= FlightEnvelope.WORLD_CEILING ? -1 : entityData.get(CLEARANCE); }
    public boolean highFlight() { return cruise.active(); }
    public double flightAltitude() { return cruise.altitude(); }
    public double routeX() { return cruise.x(); }
    public double routeZ() { return cruise.z(); }
    public NativeVehiclePropulsion.Readout flightReadout() {
        if (propulsion != null && !level().isClientSide) return propulsion.readout(this);
        return clientReadout;
    }

    public int armPose(BlockPos local) {
        var poses = entityData.get(ARM_POSES);
        var key = Long.toString(local.asLong());
        int value = poses.contains(key, Tag.TAG_INT) ? poses.getInt(key) : -1;
        return value >= 0 && value < (1 << 18) ? value : -1;
    }

    public int maxwellGauge(BlockPos core) {
        return clientGauges.getOrDefault(NativeVehiclePropulsion.gaugePosition(core), -1);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(level().isClientSide && key.equals(METER_LEVELS) && meters!=null) meters.receive(entityData.get(METER_LEVELS));
        if (!level().isClientSide || !key.equals(FLIGHT_READOUT)) return;
        var data = entityData.get(FLIGHT_READOUT);
        clientReadout = new NativeVehiclePropulsion.Readout(data.getDouble("Mass"), data.getDouble("EffectiveMass"),
                data.getInt("Drives"), data.getInt("Fuel"), data.getInt("Capacity"), data.getDouble("Thrust"),
                data.getDouble("TakeoffMass"), data.getDouble("RatedTakeoffMass"), data.getDouble("Ceiling"), data.getBoolean("CanHover"));
        clientGauges.clear();
        int[] gauges = data.getIntArray("Gauges");
        if (!NativeVehiclePropulsion.gaugeArrayValid(gauges)) return;
        for (int index = 0; index < gauges.length; index += 2) clientGauges.put(gauges[index], gauges[index + 1]);
    }

    private void publishFlightReadout() {
        var readout = flightReadout();
        var data = new CompoundTag();
        data.putDouble("Mass", readout.mass());
        data.putDouble("EffectiveMass", readout.effectiveMass());
        data.putInt("Drives", readout.drives());
        data.putInt("Fuel", readout.fuel());
        data.putInt("Capacity", readout.capacity());
        data.putDouble("Thrust", readout.thrust());
        data.putDouble("TakeoffMass", readout.takeoffMass());
        data.putDouble("RatedTakeoffMass", readout.ratedTakeoffMass());
        data.putDouble("Ceiling", readout.ceiling());
        data.putBoolean("CanHover", readout.canHover());
        data.putIntArray("Gauges", propulsion.gauges());
        entityData.set(FLIGHT_READOUT, data);
    }

    public boolean admitted() { return entityData.get(READY) && hull != null; }

    public void install(NativeTransferSnapshot blocks, BlockPos localPilot, Direction facing) {
        NativeVehicleStructure.validate(blocks, localPilot, facing, level().registryAccess());
        var states = new LinkedHashMap<BlockPos, BlockState>();
        CompoundTag seat = null;
        for (var cell : blocks.cells()) {
            states.put(cell.pos, cell.state);
            if (cell.pos.equals(localPilot)) seat = cell.tile();
        }
        int height = seat == null ? 0 : Mth.clamp(seat.getInt(ChairSettings.PILOT_HEIGHT), 0, 3);
        var appearance = NativeVehicleVisual.project(blocks, level().registryAccess());
        var drive = new NativeVehiclePropulsion(blocks, level().registryAccess());
        var controls = NativeVehicleMeters.server(blocks, level().getGameTime());
        var resources = new NativeVehicleResources(blocks, level(), drive, controls);
        var geometry = new NativeVehicleHull(states, resources::armCollision);
        snapshot = blocks;
        hull = geometry;
        propulsion = drive;
        this.resources = resources;
        armRevision = resources.armRevision();
        entityData.set(ARM_POSES, resources.armPoses());
        meters = controls;
        entityData.set(METER_LEVELS, controls.publicData());
        status = NativeVehicleStatus.panels(blocks);
        visualTag = appearance;
        pilot = localPilot.immutable();
        heading = facing;
        entityData.set(PILOT, pilot);
        entityData.set(FACING, heading);
        entityData.set(CUSHION, (float) ChairSettings.pilotCushion(height));
        entityData.set(READY, true);
        if (!level().isClientSide) publishFlightReadout();
        setBoundingBox(makeBoundingBox());
    }

    public boolean showsEmergencyPark(Player actor) {
        return actor != null && actor.isCreative() && !highFlight() && admitted()
                && actor.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get());
    }

    public boolean canPark() {
        return snapshot != null && admitted() && isAlive() && getPassengers().isEmpty() && mode() == TAXI
                && onGround() && Math.abs(speed) < .001 && getDeltaMovement().lengthSqr() < 1e-6;
    }

    public int statusModes(Player actor) {
        if (!admitted() || !isAlive() || emergencyOwner != null
                || !level().isClientSide && NativeVehicleTransfer.escrowed(this)) return 0;
        int selected = mode() + 1;
        int mask = 1 << selected;
        boolean parkedPose = mode() == TAXI && getPassengers().isEmpty()
                && (onGround() || level().isClientSide && groundClearance() >= 0 && groundClearance() < .002)
                && Math.abs(driveSpeed()) < .001 && Mth.degreesDifferenceAbs(getYRot(), Math.round(getYRot() / 90F) * 90F) <= 12;
        if (parkedPose) mask |= 1 << VehicleStatusLayout.PARK;
        if (actor == null || getFirstPassenger() != actor) return mask;
        var reading = flightReadout();
        if (mode() != HOVER || groundClearance() >= 0 && groundClearance() <= 3) mask |= 1 << VehicleStatusLayout.TAXI;
        if (reading.canHover()) mask |= 1 << VehicleStatusLayout.HOVER;
        if ((mode() != TAXI || onGround() || level().isClientSide && groundClearance() >= 0 && groundClearance() < .002)
                && FlightEnvelope.canFly(reading.effectiveMass(), reading.thrust())) mask |= 1 << VehicleStatusLayout.FLIGHT;
        return mask;
    }

    @Override protected AABB makeBoundingBox() {
        return hull == null ? super.makeBoundingBox() : hull.bounds(position(), getYRot(), hullPitch(), facing());
    }

    @Override public boolean isPickable() { return admitted() && isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }

    @Override protected boolean canAddPassenger(Entity rider) {
        return rider instanceof Player player && admitted() && getPassengers().isEmpty()
                && (level().isClientSide || !NativeVehicleTransfer.escrowed(this)
                && (owner == null || owner.equals(player.getUUID()) || player.isCreative()));
    }

    // Leave Entity.getControllingPassenger() at its default. Vanilla accepts arbitrary vehicle poses from that rider.
    private ServerPlayer pilot() { return getFirstPassenger() instanceof ServerPlayer rider ? rider : null; }

    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        BlockPos at = pilotSeat();
        return NativeVehicleHull.rotate(new Vec3(at.getX(), at.getY() + entityData.get(CUSHION), at.getZ()), getYRot(), hullPitch(), facing());
    }

    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (admitted() && mode()==TAXI && onGround()) {
            var seat=BlockPos.containing(toWorld(pilotSeat()));
            var heading=Direction.fromYRot(getYRot()+facing().toYRot());
            var offsets=net.minecraft.world.entity.vehicle.DismountHelper.offsetsForDirection(heading);
            for(int radius=1;radius<=4;radius++)for(var direction:offsets){
                var at=seat.offset(direction[0]*radius,0,direction[1]*radius);
                if(!level().hasChunkAt(at))continue;
                for(int dy=1;dy>=-2;dy--){
                    var feet=net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(
                        passenger.getType(),level(),at.above(dy),true);
                    if(feet!=null&&!getBoundingBox().intersects(passenger.getDimensions(passenger.getPose()).makeBoundingBox(feet)))return feet;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger).add(0, .02, 0);
    }

    @Override protected void addPassenger(Entity rider) {
        super.addPassenger(rider);
        if (rider instanceof ServerPlayer player) savedPilot = player.getUUID();
    }

    boolean resumeFlightSeat(ServerPlayer rider) {
        return highFlight() && admitted() && getPassengers().isEmpty() && rider.getUUID().equals(savedPilot)
                && !NativeVehicleTransfer.escrowed(this) && rider.startRiding(this, true);
    }

    @Override protected void removePassenger(Entity rider) {
        super.removePassenger(rider);
        if (rider instanceof ServerPlayer player && player.hasDisconnected()) {
            awaitingPilotUntil = level().getGameTime() + 60;
        } else {
            savedPilot = null;
        }
        keys = 0;
        inputAt = Long.MIN_VALUE;
    }

    @Override public InteractionResult interact(Player actor, InteractionHand hand) {
        if (!admitted() || hand != InteractionHand.MAIN_HAND || !actor.isAlive() || actor.isSpectator()
                || !level().isClientSide && NativeVehicleTransfer.escrowed(this)) return InteractionResult.PASS;
        var ray = localRay(actor);
        if (ray == null) return InteractionResult.PASS;
        BlockPos target = hit(ray, actor);
        if (target == null) return InteractionResult.PASS;
        int action = statusAction(actor, ray, target);
        if (action == VehicleStatusLayout.EMERGENCY) {
            if (actor instanceof ServerPlayer rider) requestEmergencyPark(rider);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (action == VehicleStatusLayout.PARK) {
            if (actor instanceof ServerPlayer rider) NativeVehicleTransfer.park(rider, this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isEngineCell(target)) {
            if (actor instanceof ServerPlayer server) {
                var core = resources.engineOwner(target);
                if (!canUseEngine(server, core, target)) return InteractionResult.FAIL;
                NativeDriveBindings.openCarried(server, this, target,
                    new NativeDriveBindings.Edit(0, core, propulsion.settings(core), propulsion.channels(core)));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (controllerHit(actor, target)) {
            return NativeVehicleBindings.open(actor, this) ? InteractionResult.sidedSuccess(level().isClientSide) : InteractionResult.PASS;
        }
        if (action >= 0) {
            if (actor instanceof ServerPlayer rider) requestMode(rider, action - 1);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        var row = meterHit(actor, ray, target);
        if (row != null) {
            if (actor instanceof ServerPlayer rider && level().mayInteract(rider, BlockPos.containing(toWorld(target)))) {
                meters.activate(target, row, level().getGameTime());
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
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

    Vec3 toWorld(BlockPos local) {
        return position().add(NativeVehicleHull.rotate(new Vec3(local.getX(), local.getY() + .5, local.getZ()),
                getYRot(), hullPitch(), facing()));
    }

    private boolean isEngineCell(BlockPos target) {
        return level().isClientSide ? clientEngineCells.contains(target) : resources.engineOwner(target) != null;
    }

    public boolean canUseEngine(Player actor, BlockPos core, BlockPos target) {
        if (!admitted() || !isAlive() || !actor.isAlive() || actor.isSpectator() || actor.level() != level()
                || actor.getEyePosition().distanceToSqr(toWorld(target)) > 64) return false;
        if (level().isClientSide) return clientEngineCells.contains(target);
        var physical = BlockPos.containing(toWorld(target));
        return core != null && core.equals(resources.engineOwner(target)) && actor.getUUID().equals(owner)
            && !NativeVehicleTransfer.escrowed(this) && level().mayInteract(actor, physical)
            && actor.mayUseItemAt(physical, Direction.UP, actor.getMainHandItem());
    }

    public boolean configureEngine(ServerPlayer actor, BlockPos target, NativeDriveBindings.Edit edit) {
        return canUseEngine(actor, edit.root(), target) && propulsion.configure(edit.root(), edit.config(), edit.channels());
    }

    public boolean purgeEngine(ServerPlayer actor, BlockPos core, BlockPos target) {
        if (!canUseEngine(actor, core, target)) return false;
        propulsion.tank(core).purge();
        propulsion.fuelChanged();
        publishFlightReadout();
        return true;
    }

    private static boolean controllerHit(Player actor, BlockPos target) {
        return target.equals(BlockPos.ZERO) || actor.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get());
    }

    public boolean pointsAtMountedControl(Player actor) {
        var ray = localRay(actor);
        if (ray == null) return false;
        BlockPos target = hit(ray, actor);
        return target != null && (isEngineCell(target) || controllerHit(actor, target) || statusAction(actor, ray, target) >= 0
                || meterHit(actor, ray, target) != null);
    }

    private NativeVehicleMeters.Click meterHit(Player actor, Ray ray, BlockPos target) {
        return meters == null || actor.isShiftKeyDown() ? null : meters.hit(target, ray.start, ray.end);
    }

    private record Ray(Vec3 start, Vec3 end) {}

    private Ray localRay(Player actor) {
        if (hull == null) return null;
        Vec3 start = actor.getEyePosition();
        Vec3 end = start.add(actor.getLookAngle().scale(Math.min(8, actor.entityInteractionRange())));
        if (!NativeVehicleHull.loadedColumns(level(), new AABB(start, end).inflate(1e-5))) return null;
        if (!highFlight()) {
            var terrain = level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, actor));
            if (terrain.getType() != HitResult.Type.MISS) end = terrain.getLocation();
        }
        return new Ray(NativeVehicleHull.inverse(start, position(), getYRot(), hullPitch(), facing()),
                NativeVehicleHull.inverse(end, position(), getYRot(), hullPitch(), facing()));
    }

    private BlockPos hit(Ray ray, Player actor) {
        return hull.hit(ray.start, ray.end, actor.getVehicle() == this ? pilotSeat() : null);
    }

    public BlockPos hit(Player actor) {
        var ray = localRay(actor);
        return ray == null ? null : hit(ray, actor);
    }

    private int statusAction(Player actor, Ray ray, BlockPos target) {
        boolean emergency = showsEmergencyPark(actor);
        boolean mounted = actor.getVehicle() == this;
        for (var panel : status) {
            if (!panel.pos().equals(target)) continue;
            int selected = panel.action(ray.start, ray.end);
            if (selected == VehicleStatusLayout.EMERGENCY) return emergency ? selected : -1;
            if (selected < 0 || selected >= VehicleStatusLayout.MODE_COUNT) continue;
            if ((selected == VehicleStatusLayout.PARK || mounted) && (statusModes(actor) & (1 << selected)) != 0) return selected;
        }
        return -1;
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
        if (emergencyOwner == null) {
            keys = mask;
            inputAt = now;
        }
    }

    @Override public void tick() {
        super.tick();
        if (!admitted()) return;
        if (level().isClientSide) {
            previousClientPitch = clientPitch;
            clientPitch = hullPitch();
            tickInterpolation();
            return;
        }
        if (NativeVehicleTransfer.escrowed(this)
                || highFlight() && pilot() == null && savedPilot != null && level().getGameTime() < awaitingPilotUntil) return;
        meters.expire(level().getGameTime());
        resources.tick(this);
        if (armRevision != resources.armRevision()) {
            armRevision = resources.armRevision();
            entityData.set(ARM_POSES, resources.armPoses());
            setBoundingBox(makeBoundingBox());
        }
        if (meters.sample(this, resources, tickCount % 5 == 0)) entityData.set(METER_LEVELS, meters.publicData());
        drive();
        if (emergencyOwner != null) finishEmergencyPark();
        if (tickCount % 10 == 0) {
            publishFlightReadout();
            if (!status.isEmpty()) entityData.set(CLEARANCE, hull.clearance(this));
        }
    }

    private void drive() {
        boolean active = pilot() != null && inputAt >= 0 && level().getGameTime() - inputAt <= 10;
        int motion = active ? keys : 0;
        int forward = ((motion & NativeVehicleInput.FORWARD) != 0 ? 1 : 0) - ((motion & NativeVehicleInput.BACK) != 0 ? 1 : 0);
        int strafe = ((motion & NativeVehicleInput.RIGHT) != 0 ? 1 : 0) - ((motion & NativeVehicleInput.LEFT) != 0 ? 1 : 0);
        boolean braking = (motion & NativeVehicleInput.BRAKE) != 0;
        int riseInput = (motion & NativeVehicleInput.BRAKE) != 0 ? 1 : 0;
        int descentInput = (motion & NativeVehicleInput.DOWN) != 0 ? 1 : 0;
        double thrust = propulsion.thrust(this, flightPhase == RUNWAY || flightPhase == CRUISE, true);
        double mass = propulsion.mass(this);
        if (flightPhase >= CRUISE) {
            flight(active, forward, strafe, mass, thrust);
            entityData.set(SPEED, (float)speed);
            fallDistance = 0;
            return;
        }

        boolean runway = flightPhase == RUNWAY;
        if (runway && (!active || braking || !onGround() || !FlightEnvelope.canFly(mass, thrust))) {
            setMode(TAXI, NO_FLIGHT);
            runway = false;
        }
        boolean hover = mode() == HOVER;
        boolean boost = hover && (motion & NativeVehicleInput.SPRINT) != 0;
        double normalSpeed = hover ? (boost ? BOOST_SPEED : HOVER_SPEED) : propulsion.hasThruster() ? POWERED_TAXI_SPEED : TAXI_SPEED;
        double targetSpeed = runway ? Math.max(normalSpeed, FlightEnvelope.TAKEOFF_SPEED)
                : !hover && braking ? 0 : forward * normalSpeed;
        speed = approach(speed, targetSpeed, runway ? FlightEnvelope.acceleration(mass, thrust)
                : !active || !hover && braking ? .045 : forward == 0 ? .008 : boost ? .028 : .018);
        float turn = hover ? 2.4F * strafe : (float)(strafe * speed * 11 * (1 - .45 * Math.abs(speed) / .28));
        float yaw = hull.turn(this, getYRot(), turn);
        setYRot(Mth.wrapDegrees(yaw));
        if (hover) hoverTarget = Mth.clamp(hoverTarget + (riseInput - descentInput) * .18,
                level().getMinBuildHeight() + 1, hull.ceiling(this, yaw, 0));
        Vec3 at = position();
        Vec3 heading = NativeVehicleHull.rotate(new Vec3(facing().getStepX(), 0, facing().getStepZ()), yaw, 0, facing());
        double dx = heading.x * speed, dz = heading.z * speed;
        double dy = hover ? Mth.clamp((hoverTarget - getY()) * .3, -.18, .18) : Math.max(-.5, getDeltaMovement().y - .04);
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
        if (hover && movedY != dy) hoverTarget = at.y + movedY;
        setOnGround(supported);
        setPos(at.x + movedX, at.y + movedY, at.z + movedZ);
        setBoundingBox(makeBoundingBox());
        setDeltaMovement(movedX, supported || movedY != dy ? 0 : movedY, movedZ);
        if (runway) {
            if (!supported || speed == 0) {
                setMode(TAXI, NO_FLIGHT);
            } else if (speed >= FlightEnvelope.TAKEOFF_SPEED && forward < 0) {
                setMode(FLIGHT, CRUISE);
                launchTicks = hull.launchTicks();
            }
        }
        entityData.set(SPEED, (float)speed);
        fallDistance = 0;
    }

    private void setMode(int mode, int phase) {
        flightPhase = phase;
        entityData.set(MODE, (byte)mode);
    }

    private void requestMode(ServerPlayer rider, int mode) {
        if (rider != pilot() || mode == mode() || emergencyOwner != null) return;
        if (mode == FLIGHT) {
            double available = propulsion.thrust(this, true, false);
            if (!propulsion.hasMaxwell()) { notifyPilot(rider, "Flight requires a Maxwell Drive."); return; }
            if (available == 0) { notifyPilot(rider, "Fuel and enable the Maxwell Drive before selecting Flight."); return; }
            if (!FlightEnvelope.canFly(propulsion.mass(this), available)) {
                notifyPilot(rider, "Effective weight exceeds the available Maxwell takeoff limit.");
                return;
            }
            if (mode() == TAXI && !onGround()) { notifyPilot(rider, "Takeoff requires a runway."); return; }
            speed = Math.max(0, speed);
            launchTicks = 0;
            keys = 0;
            setMode(FLIGHT, mode() == HOVER ? CRUISE : RUNWAY);
            return;
        }
        if (mode == HOVER && !propulsion.canHover()) {
            notifyPilot(rider, "Hover requires three lift blocks and a Vehicle Thruster.");
            return;
        }
        if (flightPhase >= CRUISE) {
            flightPhase = mode == HOVER ? hoverRecoveryPhase() : LANDING;
            return;
        }
        if (mode == TAXI && mode() == HOVER && !hull.groundWithin(this, 3)) {
            notifyPilot(rider, "Descend within three blocks of solid ground before selecting Taxi.");
            return;
        }
        finishFlight(mode);
    }

    boolean mayEmergencyPark(ServerPlayer actor) {
        return showsEmergencyPark(actor) && actor.getUUID().equals(owner) && NativeVehicleMenu.near(actor, this)
                && !NativeVehicleTransfer.escrowed(this) && (getFirstPassenger() == null || getFirstPassenger() == actor);
    }

    boolean requestEmergencyPark(ServerPlayer actor) {
        if (!mayEmergencyPark(actor)) {
            notifyPilot(actor, "Emergency Park requires the vehicle owner, Creative Mode and the Configurizer.");
            return false;
        }
        emergencyOwner = actor.getUUID();
        emergencyDeadline = level().getGameTime() + 1200;
        keys = 0;
        inputAt = Long.MIN_VALUE;
        speed = 0;
        if (mode() != TAXI) setMode(FLIGHT, LANDING);
        notifyPilot(actor, "Preparing Emergency Park.");
        return true;
    }

    private void finishEmergencyPark() {
        var world = (ServerLevel)level();
        var actor = world.getServer().getPlayerList().getPlayer(emergencyOwner);
        if (actor == null || !actor.isAlive() || actor.level() != world || !showsEmergencyPark(actor)
                || world.getGameTime() >= emergencyDeadline || getBoundingBox().distanceToSqr(actor.getEyePosition()) > 64) {
            emergencyOwner = null;
            if (actor != null) notifyPilot(actor, "Emergency Park canceled. The vehicle remains assembled.");
            return;
        }
        if (mode() != TAXI || !onGround() || Math.abs(speed) > .001) return;
        emergencyOwner = null;
        float yaw = getYRot();
        float turn = Mth.wrapDegrees(Math.round(yaw / 90F) * 90 - yaw);
        float accepted = hull.turn(this, yaw, turn);
        if (Math.abs(Mth.wrapDegrees(accepted - yaw - turn)) > .001) {
            notifyPilot(actor, "Emergency Park needs room to align the hull with the block grid.");
            return;
        }
        setYRot(Mth.wrapDegrees(accepted));
        setBoundingBox(makeBoundingBox());
        if (actor.getVehicle() == this) actor.stopRiding();
        NativeVehicleTransfer.park(actor, this);
    }

    private static void notifyPilot(ServerPlayer rider, String message) {
        rider.displayClientMessage(Component.literal(message), true);
    }

    private void flight(boolean active, int forward, int strafe, double mass, double thrust) {
        if (flightPhase == CRUISE && (!FlightEnvelope.canFly(mass, thrust) || pilot() == null))
            flightPhase = propulsion.canHover() ? hoverRecoveryPhase() : LANDING;
        double altitude = flightAltitude();
        double ceiling = FlightEnvelope.maximumAltitude(mass, thrust);
        if (!cruise.allowsTransit()) ceiling = Math.min(ceiling, hull.ceiling(this, getYRot(), hullPitch()));
        if (flightPhase >= LANDING || altitude > ceiling + .02) {
            flightDescent(ceiling);
            return;
        }
        double response = FlightEnvelope.response(mass, thrust);
        setOnGround(false);
        double maximumSpeed = FlightEnvelope.cruiseSpeed(altitude);
        speed = approach(speed, maximumSpeed, FlightEnvelope.acceleration(mass, thrust) * (1 + maximumSpeed / 10));
        float yaw = getYRot() + (active ? strafe * (float)(.9 + .55 * response) : 0);
        float pitch = Mth.clamp(hullPitch() - (active ? forward * (float)(.6 + .38 * response) : 0),
                -FlightEnvelope.MAX_PITCH, FlightEnvelope.MAX_PITCH);
        boolean launching = launchTicks > 0;
        if (launching) {
            pitch = launchTicks <= 2 ? 3 : 0;
            launchTicks--;
        }
        var movement = NativeVehicleHull.rotate(new Vec3(facing().getStepX() * speed, 0, facing().getStepZ() * speed),
                yaw, pitch, facing());
        if (!cruise.allowsTransit()) ceiling = Math.min(ceiling, hull.ceiling(this, yaw, pitch));
        double rise = Math.min(launching ? .17 : movement.y, Math.max(0, ceiling - altitude));
        if (!flightMove(new Vec3(movement.x, rise, movement.z), yaw, pitch)) {
            flightPhase = propulsion.canHover() ? hoverRecoveryPhase() : LANDING;
            speed = 0;
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean flightMove(Vec3 travel, float yaw, float pitch) {
        boolean highFlight = cruise.active();
        if (!highFlight) {
            var contact = hull.flightContact(this, yaw, pitch, travel);
            if (contact == NativeVehicleHull.FlightContact.OBSTRUCTION) return false;
            if (contact == NativeVehicleHull.FlightContact.WHEEL) {
                // Let the wheels skim the runway without penetrating the surface or dropping Flight.
                // A wheel that meets a vertical obstacle must wait for a clear heading.
                var tangent = new Vec3(travel.x, Math.max(0, travel.y), travel.z);
                pitch = hullPitch();
                if (hull.flightContact(this, yaw, pitch, tangent) != NativeVehicleHull.FlightContact.CLEAR) {
                    setDeltaMovement(Vec3.ZERO);
                    return true;
                }
                travel = tangent;
            }
        }
        setYRot(Mth.wrapDegrees(yaw));
        entityData.set(PITCH, pitch);
        if (highFlight) {
            setBoundingBox(makeBoundingBox());
            cruise.move(travel);
        } else {
            setPos(getX() + travel.x, getY() + travel.y, getZ() + travel.z);
            setBoundingBox(makeBoundingBox());
            if (travel.y > 0 && getY() > FlightEnvelope.WORLD_CEILING) cruise.enter();
        }
        setDeltaMovement(travel);
        return true;
    }

    private int hoverRecoveryPhase() {
        return cruise.active() ? CRUISE_RETURN_HOVER : RETURN_HOVER;
    }

    private void flightDescent(double ceiling) {
        speed = approach(speed, 0, Math.max(.09, speed / 15));
        boolean landing = flightPhase == LANDING;
        boolean cruiseRecovery = flightPhase == CRUISE_RETURN_HOVER;
        boolean resumeHover = cruiseRecovery || flightPhase == RETURN_HOVER;
        if (cruise.active()) {
            double target = landing || resumeHover ? FlightEnvelope.REENTRY_ALTITUDE : ceiling;
            double descent = Math.max(-Math.min(3, .26 + flightAltitude() / 3500), target - flightAltitude());
            float pitch = (float)approach(hullPitch(), 0, 1.5);
            flightMove(new Vec3(0, Math.min(0, descent), 0), getYRot(), pitch);
            return;
        }
        if (hullPitch() != 0) {
            float level = (float)approach(hullPitch(), 0, 1.5);
            if (flightMove(Vec3.ZERO, getYRot(), level) || flightMove(new Vec3(0, .12, 0), getYRot(), level)) return;
        }
        double target = cruiseRecovery ? FlightEnvelope.REENTRY_ALTITUDE : resumeHover ? getY() : ceiling;
        if (resumeHover && hullPitch() == 0 && getY() <= target + .01) { finishFlight(HOVER); return; }
        double descent = landing || resumeHover && hullPitch() != 0 ? -.24 : Mth.clamp(target - getY(), -.24, 0);
        double accepted = hull.clip(this, position(), getYRot(), descent, Direction.Axis.Y);
        if (accepted != 0) {
            setPos(getX(), getY() + accepted, getZ());
            setDeltaMovement(0, accepted, 0);
            return;
        }
        boolean grounded = descent < 0 && hull.groundWithin(this, .002);
        setOnGround(grounded);
        setDeltaMovement(Vec3.ZERO);
        if (grounded && (landing || resumeHover)) finishFlight(resumeHover ? HOVER : TAXI);
    }

    private void finishFlight(int nextMode) {
        setMode(nextMode, NO_FLIGHT);
        entityData.set(PITCH, 0F);
        hoverTarget = getY();
        speed = 0;
        launchTicks = 0;
        keys = 0;
        setDeltaMovement(Vec3.ZERO);
    }

    private static double approach(double value, double target, double amount) {
        return value + Mth.clamp(target - value, -amount, amount);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int ticks) {
        if (distanceToSqr(x, y, z) > 16384) {
            super.lerpTo(x, y, z, yaw, pitch, ticks);
            setOldPosAndRot();
            previousClientPitch = clientPitch = hullPitch();
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
            var engines = new HashSet<BlockPos>();
            for (var cell : appearance.cells()) {
                states.put(cell.pos(), cell.state());
                var block = cell.state().getBlock();
                if (block instanceof com.vandorlabs.neoforge.builders.NativeBuilderPartBlock
                    || block instanceof NativeBuilderCoreBlock core
                    && core.design.drive() == NativeDriveDesign.LONGBURN) engines.add(cell.pos());
            }
            clientEngineCells = Set.copyOf(engines);
            hull = new NativeVehicleHull(states, null);
            var panels = new java.util.ArrayList<NativeVehicleStatus.Panel>();
            for (var cell : appearance.cells()) panels.addAll(NativeVehicleStatus.panels(cell.pos(), cell.state(), cell.appearance()));
            status = List.copyOf(panels);
            visual = appearance;
            meters = NativeVehicleMeters.client(appearance);
            meters.receive(entityData.get(METER_LEVELS));
            setBoundingBox(makeBoundingBox());
        } catch (RuntimeException invalidAppearance) {
            hull = null;
            visual = null;
        }
    }

    public record SavedVehicle(NativeTransferSnapshot snapshot, BlockPos pilot, Direction facing, UUID owner,
                               int mode, double speed, double hoverTarget, float pitch, int flightPhase, int launchTicks,
                               NativeVehicleCruise.Route route, UUID pilotAssignment) {}

    public static SavedVehicle readSavedVehicle(CompoundTag entityTag, HolderLookup.Provider registries) {
        if (!(entityTag.get(SAVE_KEY) instanceof CompoundTag data) || !VEHICLE_FIELDS.containsAll(data.getAllKeys())
                || !data.contains("Schema", Tag.TAG_INT)
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
        int phase = data.getByte("FlightPhase"), launch = data.getByte("LaunchTicks");
        var route = NativeVehicleCruise.Route.read(data);
        if (face < 2 || face > 5 || mode < TAXI || mode > FLIGHT || !Double.isFinite(speed)
                || Math.abs(speed) > FlightEnvelope.cruiseSpeed(FlightEnvelope.MAX_ALTITUDE)
                || !Double.isFinite(hoverTarget) || !Float.isFinite(pitch) || Math.abs(pitch) > FlightEnvelope.MAX_PITCH
                || mode != FLIGHT && (phase != NO_FLIGHT || pitch != 0 || launch != 0 || route != null)
                || data.contains("PilotAssignment") && !data.hasUUID("PilotAssignment")
                || mode == FLIGHT && (phase < RUNWAY || phase > CRUISE_RETURN_HOVER || speed < 0 || launch < 0 || launch > 64)
                || route != null && phase == RUNWAY
                || data.contains("FlightPhase") && !data.contains("FlightPhase", Tag.TAG_BYTE)
                || data.contains("LaunchTicks") && !data.contains("LaunchTicks", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Invalid native vehicle pose");
        }
        var blocks = NativeTransferSnapshot.read(registries, data.getCompound("Blocks"), NativeVehicleStructure.LIMITS, tag -> Map.of());
        var pilot = BlockPos.of(data.getLong("Pilot"));
        var facing = Direction.from3DDataValue(face);
        NativeVehicleStructure.validate(blocks, pilot, facing, registries);
        return new SavedVehicle(blocks, pilot, facing, data.hasUUID("Owner") ? data.getUUID("Owner") : null, mode, speed, hoverTarget, pitch, phase, launch, route,
                data.hasUUID("PilotAssignment") ? data.getUUID("PilotAssignment") : null);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        NativeVehicleObservers.removeCruise(this);
        retained = new CompoundTag();
        for (String key : tag.getAllKeys()) {
            if (!ENTITY_FIELDS.contains(key)) retained.put(key, tag.get(key).copy());
        }
        entityData.set(READY, false);
        snapshot = null;
        hull = null;
        propulsion = null;
        resources = null;
        visual = null;
        visualTag = null;
        status = List.of();
        try {
            var saved = readSavedVehicle(tag, level().registryAccess());
            install(saved.snapshot, saved.pilot, saved.facing);
            owner = saved.owner;
            speed = saved.speed;
            hoverTarget = saved.hoverTarget;
            launchTicks = saved.launchTicks;
            setMode(saved.mode, saved.route != null && saved.flightPhase == RETURN_HOVER ? CRUISE_RETURN_HOVER : saved.flightPhase);
            savedPilot = saved.pilotAssignment;
            cruise.restore(saved.route);
            if (savedPilot != null && saved.route != null) awaitingPilotUntil = level().getGameTime() + 100;
            entityData.set(PITCH, saved.pitch);
            entityData.set(SPEED, (float)speed);
            setBoundingBox(makeBoundingBox());
            retained.remove(SAVE_KEY);
        } catch (RuntimeException | LinkageError unavailable) {
            // The original payload stays on the entity for repair or a later compatible loader.
            snapshot = null;
            hull = null;
            propulsion = null;
            resources = null;
            visualTag = null;
            entityData.set(READY, false);
        }
        NativeVehicleObservers.updateCruise(this);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.merge(retained);
        if (snapshot == null) return;
        var data = new CompoundTag();
        data.putInt("Schema", SCHEMA);
        data.put("Blocks", snapshot().write());
        data.putLong("Pilot", pilotSeat().asLong());
        data.putInt("Facing", facing().get3DDataValue());
        if (owner != null) data.putUUID("Owner", owner);
        data.putByte("Mode", (byte)mode());
        data.putDouble("Speed", speed);
        data.putDouble("HoverTarget", hoverTarget);
        data.putFloat("Pitch", hullPitch());
        if (flightPhase != NO_FLIGHT) data.putByte("FlightPhase", (byte)flightPhase);
        if (launchTicks != 0) data.putByte("LaunchTicks", (byte)launchTicks);
        if (savedPilot != null) data.putUUID("PilotAssignment", savedPilot);
        cruise.write(data);
        tag.put(SAVE_KEY, data);
    }
}
