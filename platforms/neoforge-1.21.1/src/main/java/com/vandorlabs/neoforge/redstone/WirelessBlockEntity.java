package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.WirelessSignalPolicy;
import com.vandorlabs.persistence.WirelessData;
import com.vandorlabs.persistence.ChannelMembershipData;
import com.vandorlabs.neoforge.persistence.NbtPrimitiveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Event-driven native endpoints of the shared dimension channel network. */
public final class WirelessBlockEntity extends BlockEntity implements ModernChannelMember {
    private ChannelList channels = ChannelList.EMPTY;
    private boolean analog = true;
    private int onLevel = 15, offLevel, inputLevel, receivedLevel;
    public WirelessBlockEntity(BlockPos pos, BlockState state) { super(WirelessContent.ENTITY.get(), pos, state); }
    public boolean transmitter() { return getBlockState().getBlock() instanceof WirelessBlock block && block.transmitter; }
    public boolean analog() { return analog; }
    public int onLevel() { return onLevel; }
    public int offLevel() { return offLevel; }
    public Direction port() { return getBlockState().getValue(WirelessBlock.FACING); }
    @Override public BlockEntity channelBlockEntity() { return this; }
    @Override public ChannelList getRedstoneChannels() { return channels; }
    @Override public int localSignalLevel(int channel) { return WirelessSignalPolicy.sourceLevel(transmitter(), analog, inputLevel); }
    public int outputLevel() { return WirelessSignalPolicy.outputLevel(transmitter(), analog, receivedLevel, onLevel, offLevel); }
    @Override public void setChannelLevel(int value) {
        int before = outputLevel(); receivedLevel = clamp(value);
        if (before != outputLevel()) changed(true);
    }
    public void configure(ChannelList next, boolean analog, int on, int off, Direction face) {
        if (level == null || level.isClientSide || !isChannelMemberActive()) return;
        int before = outputLevel();
        boolean channelsChanged = !channels.equals(next);
        channels = next; this.analog = analog; onLevel = clamp(on); offLevel = clamp(off);
        if (channelsChanged) ModernRedstoneChannels.channelsChanged(this);
        Direction previous = port();
        if (previous != face) level.setBlock(worldPosition, getBlockState().setValue(WirelessBlock.FACING, face), 3);
        if (transmitter()) { sampleInput(); ModernRedstoneChannels.inputChanged(this); }
        changed(previous != face || before != outputLevel());
        if (previous != face && level.hasChunkAt(worldPosition.relative(previous))) level.updateNeighborsAt(worldPosition.relative(previous), getBlockState().getBlock());
    }
    public void sampleInput() {
        if (level == null || level.isClientSide || !transmitter() || !isChannelMemberActive()) return;
        Direction side = port(); BlockPos source = worldPosition.relative(side); int value = 0;
        if (level.hasChunkAt(source)) {
            BlockState state = level.getBlockState(source);
            if (state.shouldCheckWeakPower(level, source, side)) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = source.relative(direction);
                    if (level.hasChunkAt(neighbor)) value = Math.max(value, level.getBlockState(neighbor).getDirectSignal(level, neighbor, direction));
                }
            } else value = state.getSignal(level, source, side);
        }
        value = clamp(value);
        if (value == inputLevel) return;
        inputLevel = value; changed(false); ModernRedstoneChannels.inputChanged(this);
    }
    private void changed(boolean output) {
        setChanged(); sync();
        if (output && level != null && !level.isClientSide) ModernSignalBatch.afterSignals(this, () -> {
            if (!isChannelMemberActive()) return;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            if (level.hasChunkAt(worldPosition.relative(port()))) level.updateNeighborsAt(worldPosition.relative(port()), getBlockState().getBlock());
        });
    }
    public void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }
    @Override public void onLoad() { super.onLoad(); ModernRedstoneChannels.queueRegistration(this); }
    @Override public void channelLoaded() { sampleInput(); changed(true); }
    @Override public void setRemoved() { ModernRedstoneChannels.unregister(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { ModernRedstoneChannels.unregister(this); super.onChunkUnloaded(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ChannelList fallback = ChannelList.of(Math.max(0, tag.getInt("RedstoneChannel")));
        channels = ChannelMembershipData.read(tag.contains(ChannelMembershipData.CHANNELS, 11) ? tag.getIntArray(ChannelMembershipData.CHANNELS) : null, fallback);
        WirelessData data = WirelessData.read(new NbtPrimitiveData(tag));
        analog = data.analog; onLevel = data.onLevel; offLevel = data.offLevel; inputLevel = data.inputLevel; receivedLevel = data.receivedLevel;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray(ChannelMembershipData.CHANNELS, channels.toArray()); tag.putInt("RedstoneChannel", channels.first());
        new WirelessData(analog, onLevel, offLevel, inputLevel, receivedLevel).write(new NbtPrimitiveData(tag));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    private static int clamp(int value) { return WirelessSignalPolicy.clampLevel(value); }
}
