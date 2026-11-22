package com.vandorlabs.neoforge.coupler;

import com.vandorlabs.coupler.CouplerInput;
import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.redstone.NativeChannelMember;
import com.vandorlabs.neoforge.redstone.NativeLoadedRedstonePower;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.redstone.NativeSignalUpdateBatch;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.redstone.ChannelList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class NativeCouplerEntity extends BlockEntity implements NativeChannelMember {
    private static final String CHANNELS = "RedstoneChannels";
    private static final String INVERTED = "CouplerInverted";
    private static final String OVERRIDE = "CouplerManualOverride";
    private static final String MANUAL = "CouplerManualRequested";
    private final CouplerInput input = new CouplerInput();
    private ChannelList channels = ChannelList.EMPTY;
    private boolean inverted, received, inert, inputReady;
    private CompoundTag retained = new CompoundTag();

    NativeCouplerEntity(BlockPos pos, BlockState state) {
        super(NativeCouplerBindings.ENTITY, pos, state);
    }

    public boolean inert() { return inert; }
    public boolean inverted() { return inverted; }
    public boolean engaged() { return !inert && getBlockState().getValue(NativeCouplerBlock.LOCKED); }
    public boolean paired() {
        if (!engaged() || level == null) return false;
        var direction = NativeCouplerBlock.facing(getBlockState());
        var neighbor = worldPosition.relative(direction);
        return level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4) != null
                && level.getBlockEntity(neighbor) instanceof NativeCouplerEntity mate && !mate.inert()
                && NativeCouplerBlock.pair(getBlockState(), mate.getBlockState(), direction);
    }
    private boolean available() {
        return level instanceof ServerLevel server && server.getServer().isSameThread()
                && NativeRedstoneChannels.open(server) && isChannelMemberActive();
    }
    @Override public boolean isChannelMemberActive() {
        return !inert && NativeChannelMember.super.isChannelMemberActive()
                && !NativeTransferHolds.reserved(level, worldPosition);
    }
    @Override public BlockEntity channelBlockEntity() { return this; }
    @Override public ChannelList getRedstoneChannels() { return channels; }
    @Override public void setChannelLevel(int value) {
        received = value > 0;
        requestSample();
    }
    @Override public void onLoad() {
        super.onLoad();
        inputReady = false;
        if (level instanceof ServerLevel) NativeRedstoneChannels.queueRegistration(this);
    }
    @Override public void beforeChannelRegistration() {
        // Preserve saved overrides until both the physical input and the registered bank have settled.
        NativeSignalUpdateBatch.afterSignals(this, () -> {
            inputReady = true;
            sample();
        });
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel) NativeRedstoneChannels.unregister(this);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel) NativeRedstoneChannels.unregister(this);
        super.onChunkUnloaded();
    }
    void requestSample() {
        if (!inputReady || !available()) return;
        if (NativeSignalUpdateBatch.isActive()) NativeSignalUpdateBatch.afterSignals(this, this::sample);
        else sample();
    }
    private void sample() {
        if (!inputReady || !available()) return;
        boolean previousOverride = input.overridden();
        boolean desired = input.sample(received || NativeLoadedRedstonePower.level(level, worldPosition) != 0, inverted);
        var state = getBlockState();
        if (state.getValue(NativeCouplerBlock.LOCKED) != desired) {
            if (level.setBlock(worldPosition, state.setValue(NativeCouplerBlock.LOCKED, desired), Block.UPDATE_CLIENTS))
                setChanged();
        } else if (previousOverride != input.overridden()) {
            setChanged();
        }
    }
    boolean configure(ChannelList bank, boolean invert) {
        if (!inputReady || !available()) return false;
        if (channels.equals(bank) && inverted == invert) return true;
        channels = bank;
        inverted = invert;
        input.clearOverride();
        NativeRedstoneChannels.channelsChanged(this);
        sample();
        publish();
        return true;
    }
    boolean toggle() {
        if (!inputReady || !available()) return false;
        sample();
        input.manual(!engaged());
        sample();
        publish();
        return true;
    }
    private void publish() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void read(CompoundTag tag) {
        if ((tag.contains(CHANNELS) && !tag.contains(CHANNELS, Tag.TAG_INT_ARRAY))
                || (tag.contains("RedstoneChannel") && !tag.contains("RedstoneChannel", Tag.TAG_INT)))
            throw new IllegalArgumentException("Coupler channel data");
        for (var key : List.of(INVERTED, OVERRIDE, MANUAL))
            if (tag.contains(key) && (!tag.contains(key, Tag.TAG_BYTE) || tag.getByte(key) < 0 || tag.getByte(key) > 1))
                throw new IllegalArgumentException("Coupler control data");
        var bank = ChannelList.of(tag.getIntArray(CHANNELS));
        if (bank.isEmpty() && tag.contains("RedstoneChannel", Tag.TAG_INT)) bank = ChannelList.of(tag.getInt("RedstoneChannel"));
        channels = bank;
        inverted = tag.getBoolean(INVERTED);
        input.read(tag.getBoolean(OVERRIDE), tag.getBoolean(MANUAL));
    }

    private void write(CompoundTag tag) {
        tag.putIntArray(CHANNELS, channels.toArray());
        tag.putBoolean(INVERTED, inverted);
        tag.putBoolean(OVERRIDE, input.overridden());
        tag.putBoolean(MANUAL, input.manualRequested());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        retained = tag.copy();
        try {
            NativePlatformComponents.requireSupported(tag, registries);
            if (tag.contains("ForgeCaps") && (!tag.contains("ForgeCaps", Tag.TAG_COMPOUND)
                    || !tag.getCompound("ForgeCaps").isEmpty()))
                throw new IllegalArgumentException("Unsupported legacy Coupler capabilities");
            if (tag.getBoolean("CouplerArticulation") || tag.contains("CouplerFrameUp")
                    || tag.contains("VehicleArmCouplerAssociation") || tag.getBoolean("VehicleArmCouplerReleased"))
                throw new IllegalArgumentException("Coupler belongs to an unsupported moving assembly");
            read(tag);
            try (var guard = NativePlatformComponents.guardLoad(this)) {
                super.loadAdditional(tag, registries);
                guard.requireSuccessful();
            }
            for (var key : List.of("id", "x", "y", "z", CHANNELS, "RedstoneChannel", INVERTED, OVERRIDE, MANUAL, "ForgeCaps"))
                retained.remove(key);
            NativePlatformComponents.forgetLoadedFields(retained);
            inert = false;
        } catch (RuntimeException invalid) {
            channels = ChannelList.EMPTY;
            inert = true;
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(retained.copy());
        if (inert) {
            NativePlatformComponents.clearForInertSave(this);
            return;
        }
        super.saveAdditional(tag, registries);
        write(tag);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        write(tag);
        tag.putBoolean("CouplerInert", inert);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
        inert = tag.getBoolean("CouplerInert");
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
