package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.turntable.TurntableSettings;
import com.vandorlabs.turntable.TurntableGeometry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Holds the native capture across saves. Data that cannot be decoded remains intact and inert. */
final class NativeTurntableEntity extends BlockEntity {
    static final String CAPTURE = "TurntableCapture";
    static final String KEY = "TurntableKey";
    static final String ANGLE = "TurntableAngle";
    static final String TARGET = "TurntableTarget";
    private static final String SCHEMA = "TurntableSchema";

    private UUID key = UUID.randomUUID();
    private TurntableSettings settings = new TurntableSettings(4, 0, 1, 1, 0, true,
            ChannelList.EMPTY, ChannelList.EMPTY, ChannelList.EMPTY, ChannelList.EMPTY);
    private NativeTurntableCapture capture;
    private NativeTurntableMotion motion;
    private NativeTurntableCollisions.Body publicBody;
    private boolean collisionIndexed;
    private double angle;
    private int target = -1;
    private float clientAngle;
    private long lastSequence = Long.MIN_VALUE;
    private boolean clientMoving;
    private CompoundTag retained = new CompoundTag();
    private boolean inert;
    private CompoundTag visualProjection;
    private NativeTurntableVisual clientVisual;
    private CompoundTag clientAppearance;
    private boolean clientCaptured;
    private boolean clientBusy;
    private String status = "Place a square Surface in front of the controller";

    NativeTurntableEntity(BlockPos pos, BlockState state) { super(NativeTurntableBindings.entity, pos, state); }

    UUID key() { return key; }
    TurntableSettings settings() { return settings; }
    Direction facing() { return getBlockState().getValue(NativeTurntableBlock.FACING); }
    NativeTurntableCapture capture() { return capture; }
    boolean available() { return !inert; }
    boolean protectsContents() { return inert || capture != null; }
    boolean installed(ServerLevel world) {
        if (inert || isRemoved() || level != world || !world.getServer().isSameThread()) return false;
        var chunk = world.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this;
    }

    boolean busy() {
        return level instanceof ServerLevel world ? NativeTurntableTransfers.busy(world, this) : clientBusy;
    }

    boolean hasCapture() { return level instanceof ServerLevel ? capture != null : clientCaptured; }
    String status() { return status; }
    NativeTurntableVisual visual() { return clientVisual; }
    float visualAngle() { return clientAngle; }
    double positionAngle() { return angle; }

    boolean moving() { return level instanceof ServerLevel ? target >= 0 : clientMoving; }

    boolean turn(int quarter) {
        if (!(level instanceof ServerLevel world) || !installed(world) || busy() || capture == null) return false;
        if (quarter < 0 || quarter > 3) return false;
        target = quarter;
        setChanged();
        status("Turning to " + quarter * 90 + " degrees");
        return true;
    }

    static void tick(ServerLevel world, NativeTurntableEntity owner) {
        if (owner.capture != null && !owner.collisionIndexed && owner.installed(world)) owner.indexCollision(true);
        if (owner.target < 0 || world.noSave || !owner.installed(world)) return;
        double heading = owner.target * 90;
        double distance = (heading - owner.angle + 540) % 360 - 180;
        if (Math.abs(distance) < 1e-7) {
            owner.target = -1;
            owner.setChanged();
            owner.status("Turntable stopped at " + (int) heading + " degrees");
            return;
        }
        double step = Math.copySign(Math.min(Math.abs(distance), TurntableGeometry.degreesPerTick(owner.settings.rpm)), distance);
        String blocked = owner.motion.obstruction(world, owner.angle, step);
        if (blocked != null) {
            owner.target = -1;
            owner.setChanged();
            owner.status(blocked);
            return;
        }
        owner.angle = Math.abs(distance) <= Math.abs(step) ? heading : TurntableGeometry.wrap(owner.angle + step);
        owner.setChanged();
        PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(owner.worldPosition),
                new NativeTurntableAnglePayload(owner.worldPosition, owner.key, world.getGameTime(), (float) owner.angle));
    }

    void readAngle(UUID owner, long sequence, float value) {
        if (!key.equals(owner) || !clientCaptured || sequence <= lastSequence
                || !Float.isFinite(value) || value < 0 || value > 360) return;
        lastSequence = sequence;
        clientAngle = value;
    }

    void status(String value) {
        var next = value == null ? "Turntable transfer refused" : value.substring(0, Math.min(512, value.length()));
        if (!status.equals(next)) {
            status = next;
            publish();
        }
    }

    boolean setHeight(int height) {
        if (busy() || hasCapture()) return false;
        if (settings.height == height) return true;
        settings = withHeight(height);
        setChanged();
        publish();
        return true;
    }

    boolean setSpeed(int halfSteps) {
        if (busy() || halfSteps < 1 || halfSteps > 20) return false;
        if (settings.rpm == halfSteps / 2D) return true;
        settings = withSpeed(halfSteps / 2D);
        setChanged();
        publish();
        return true;
    }

    private TurntableSettings withSpeed(double rpm) {
        return new TurntableSettings(settings.height, settings.scanMode, settings.rotationMode, rpm,
                settings.snapTolerance, settings.clockwise, settings.scanChannels, settings.rotationChannels,
                settings.directionChannels, settings.restoreChannels);
    }

    private TurntableSettings withHeight(int height) {
        return new TurntableSettings(height, settings.scanMode, settings.rotationMode, settings.rpm,
                settings.snapTolerance, settings.clockwise, settings.scanChannels, settings.rotationChannels,
                settings.directionChannels, settings.restoreChannels);
    }

    void publish() {
        if (level instanceof ServerLevel world && installed(world)) {
            world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID(KEY, key);
        tag.putInt("Height", settings.height);
        tag.putDouble("RPM", settings.rpm);
        tag.putBoolean("Captured", hasCapture());
        tag.putBoolean("Busy", busy());
        tag.putBoolean("Inert", inert);
        tag.putString("Status", status);
        tag.putFloat("Angle", (float) angle);
        tag.putBoolean("Moving", moving());
        if (level instanceof ServerLevel world) tag.putLong("Sequence", world.getGameTime());
        if (visualProjection != null) tag.put("Appearance", visualProjection.copy());
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (level != null && level.isClientSide && tag.hasUUID(KEY)
                && tag.contains("Height", Tag.TAG_INT) && tag.getInt("Height") >= TurntableSettings.MIN_HEIGHT
                && tag.getInt("Height") <= TurntableSettings.MAX_HEIGHT && tag.getString("Status").length() <= 512) {
            var nextKey = tag.getUUID(KEY);
            if (!key.equals(nextKey)) lastSequence = Long.MIN_VALUE;
            key = nextKey;
            settings = withHeight(tag.getInt("Height"));
            double speed = tag.getDouble("RPM");
            if (Double.isFinite(speed) && speed >= .5 && speed <= 10 && speed * 2 == Math.rint(speed * 2)) {
                settings = withSpeed(speed);
            }
            clientCaptured = tag.getBoolean("Captured");
            clientMoving = tag.getBoolean("Moving");
            readAngle(key, tag.getLong("Sequence"), tag.getFloat("Angle"));
            if (!clientCaptured) clientAngle = 0;
            clientBusy = tag.getBoolean("Busy");
            inert = tag.getBoolean("Inert");
            status = tag.getString("Status");
            try {
                var projection = tag.getCompound("Appearance");
                if (!clientCaptured || projection.isEmpty()) clientVisual = null;
                else if (clientAppearance == null || !clientAppearance.equals(projection)) {
                    clientVisual = NativeTurntableVisual.read(projection, registries);
                    publicBody = NativeTurntableCollisions.Body.client(clientVisual, worldPosition);
                    clientAppearance = projection.copy();
                }
            } catch (RuntimeException invalid) {
                clientVisual = null;
                clientAppearance = null;
            }
            if (clientVisual == null) {
                clientAppearance = null;
                publicBody = null;
            }
            indexCollision(publicBody != null);
        }
    }

    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    NativeTurntableCollisions.Body body() {
        return level instanceof ServerLevel ? motion == null ? null : motion.body() : publicBody;
    }

    private void indexCollision(boolean present) {
        if (collisionIndexed == present) return;
        collisionIndexed = present;
        NativeTurntableCollisions.update(this, present);
    }

    @Override public void setRemoved() {
        indexCollision(false);
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        indexCollision(false);
        super.onChunkUnloaded();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(retained);
        if (inert) {
            NativePlatformComponents.clearForInertSave(this);
            return;
        }
        super.saveAdditional(tag, registries);
        tag.putInt(SCHEMA, 1);
        tag.putDouble(ANGLE, angle);
        tag.putInt(TARGET, target);
        tag.putUUID(KEY, key);
        tag.putInt("Height", settings.height);
        tag.putInt("ScanMode", settings.scanMode);
        tag.putInt("RotationMode", settings.rotationMode);
        tag.putDouble("RPM", settings.rpm);
        tag.putDouble("AutoSnapTolerance", settings.snapTolerance);
        tag.putBoolean("Clockwise", settings.clockwise);
        tag.putString("ScanChannels", settings.scanChannels.toString());
        tag.putString("RotationChannels", settings.rotationChannels.toString());
        tag.putString("DirectionChannels", settings.directionChannels.toString());
        tag.putString("RestoreChannels", settings.restoreChannels.toString());
        if (capture != null) tag.put(CAPTURE, capture.save());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        var original = tag.copy();
        try {
            if (!tag.contains(SCHEMA, Tag.TAG_INT) || tag.getInt(SCHEMA) != 1 || !tag.hasUUID(KEY)
                    || tag.contains(CAPTURE) && !tag.contains(CAPTURE, Tag.TAG_COMPOUND)
                    || !tag.contains("Height", Tag.TAG_INT) || !tag.contains("ScanMode", Tag.TAG_INT)
                    || !tag.contains("RotationMode", Tag.TAG_INT) || !tag.contains("RPM", Tag.TAG_DOUBLE)
                    || !tag.contains("AutoSnapTolerance", Tag.TAG_DOUBLE)
                    || !tag.contains("Clockwise", Tag.TAG_BYTE) || tag.getByte("Clockwise") < 0 || tag.getByte("Clockwise") > 1) {
                throw new IllegalArgumentException("Malformed Turntable owner");
            }
            var next = new TurntableSettings(tag.getInt("Height"), tag.getInt("ScanMode"), tag.getInt("RotationMode"),
                    tag.getDouble("RPM"), tag.getDouble("AutoSnapTolerance"), tag.getBoolean("Clockwise"),
                    channels(tag, "ScanChannels"), channels(tag, "RotationChannels"),
                    channels(tag, "DirectionChannels"), channels(tag, "RestoreChannels"));
            var payload = tag.contains(CAPTURE)
                    ? NativeTurntableCapture.read(registries, tag.getCompound(CAPTURE), worldPosition) : null;
            double nextAngle = tag.getDouble(ANGLE);
            int nextTarget = tag.contains(TARGET) ? tag.getInt(TARGET) : -1;
            if (tag.contains(ANGLE) && !tag.contains(ANGLE, Tag.TAG_DOUBLE)
                    || tag.contains(TARGET) && !tag.contains(TARGET, Tag.TAG_INT)
                    || !Double.isFinite(nextAngle) || nextAngle < 0 || nextAngle >= 360
                    || nextTarget < -1 || nextTarget > 3
                    || payload == null && (nextAngle != 0 || nextTarget >= 0)) {
                throw new IllegalArgumentException("Invalid Turntable saved motion");
            }
            var projection = payload == null ? null : NativeTurntableVisual.project(payload, worldPosition, registries);
            var geometry = payload == null ? null : new NativeTurntableMotion(payload);
            NativePlatformComponents.requireSupported(original, registries);
            try (var guard = NativePlatformComponents.guardLoad(this)) {
                super.loadAdditional(tag, registries);
                guard.requireSuccessful();
            }
            settings = next;
            key = tag.getUUID(KEY);
            capture = payload;
            visualProjection = projection;
            motion = geometry;
            angle = nextAngle;
            target = nextTarget;
            inert = false;
            retained = original;
            for (String field : new String[]{SCHEMA, KEY, CAPTURE, ANGLE, TARGET, "Height", "ScanMode", "RotationMode", "RPM",
                    "AutoSnapTolerance", "Clockwise", "ScanChannels", "RotationChannels", "DirectionChannels", "RestoreChannels"}) {
                retained.remove(field);
            }
            NativePlatformComponents.forgetLoadedFields(retained);
        } catch (RuntimeException failure) {
            inert = true;
            capture = null;
            visualProjection = null;
            motion = null;
            angle = 0;
            target = -1;
            retained = original;
        }
    }

    private static ChannelList channels(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid Turntable channel bank");
        return ChannelList.parse(tag.getString(field));
    }
}
