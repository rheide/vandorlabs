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
            NativePlatformComponents.requireSupported(original, registries);
            try (var guard = NativePlatformComponents.guardLoad(this)) {
                super.loadAdditional(tag, registries);
                guard.requireSuccessful();
            }
            settings = next;
            key = tag.getUUID(KEY);
            capture = payload;
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
            retained = original;
        }
    }

    private static ChannelList channels(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid Turntable channel bank");
        return ChannelList.parse(tag.getString(field));
    }
}
