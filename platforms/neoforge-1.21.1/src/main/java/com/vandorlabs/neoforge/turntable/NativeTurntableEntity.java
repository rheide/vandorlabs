package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.turntable.TurntableSettings;
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

/** Holds the native capture across saves. Data that cannot be decoded remains intact and inert. */
final class NativeTurntableEntity extends BlockEntity {
    static final String CAPTURE = "TurntableCapture";
    static final String KEY = "TurntableKey";
    private static final String SCHEMA = "TurntableSchema";

    private UUID key = UUID.randomUUID();
    private TurntableSettings settings = new TurntableSettings(4, 0, 1, 1, 0, true,
            ChannelList.EMPTY, ChannelList.EMPTY, ChannelList.EMPTY, ChannelList.EMPTY);
    private NativeTurntableCapture capture;
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
        tag.putBoolean("Captured", hasCapture());
        tag.putBoolean("Busy", busy());
        tag.putBoolean("Inert", inert);
        tag.putString("Status", status);
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
            key = tag.getUUID(KEY);
            settings = withHeight(tag.getInt("Height"));
            clientCaptured = tag.getBoolean("Captured");
            clientBusy = tag.getBoolean("Busy");
            inert = tag.getBoolean("Inert");
            status = tag.getString("Status");
            try {
                var projection = tag.getCompound("Appearance");
                if (!clientCaptured || projection.isEmpty()) clientVisual = null;
                else if (clientAppearance == null || !clientAppearance.equals(projection)) {
                    clientVisual = NativeTurntableVisual.read(projection, registries);
                    clientAppearance = projection.copy();
                }
            } catch (RuntimeException invalid) {
                clientVisual = null;
                clientAppearance = null;
            }
            if (clientVisual == null) clientAppearance = null;
        }
    }

    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(retained);
        if (inert) {
            NativePlatformComponents.clearForInertSave(this);
            return;
        }
        super.saveAdditional(tag, registries);
        tag.putInt(SCHEMA, 1);
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
            var projection = payload == null ? null : NativeTurntableVisual.project(payload, worldPosition, registries);
            NativePlatformComponents.requireSupported(original, registries);
            try (var guard = NativePlatformComponents.guardLoad(this)) {
                super.loadAdditional(tag, registries);
                guard.requireSuccessful();
            }
            settings = next;
            key = tag.getUUID(KEY);
            capture = payload;
            visualProjection = projection;
            inert = false;
            retained = original;
            for (String field : new String[]{SCHEMA, KEY, CAPTURE, "Height", "ScanMode", "RotationMode", "RPM",
                    "AutoSnapTolerance", "Clockwise", "ScanChannels", "RotationChannels", "DirectionChannels", "RestoreChannels"}) {
                retained.remove(field);
            }
            NativePlatformComponents.forgetLoadedFields(retained);
        } catch (RuntimeException failure) {
            inert = true;
            capture = null;
            visualProjection = null;
            retained = original;
        }
    }

    private static ChannelList channels(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid Turntable channel bank");
        return ChannelList.parse(tag.getString(field));
    }
}
