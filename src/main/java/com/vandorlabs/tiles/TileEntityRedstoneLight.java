package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockLamp;
import com.vandorlabs.blocks.BlockLampOff;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockConnectedPropulsionLight;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RedstoneData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumSkyBlock;

/** Persistent channel and operating state for lamps and propulsion fixtures. */
public class TileEntityRedstoneLight extends TileEntity implements RedstoneChannelMember, ITickable {
    private int channel;
    private boolean channelSignal;
    private boolean manualOn;
    private boolean particleStreamSelected;
    private boolean initialized;
    private boolean join = true;

    public boolean isJoin() { return join; }
    public void setJoin(boolean value) {
        if (join == value) return;
        join = value;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockConnectedPropulsionLight)
                ((BlockConnectedPropulsionLight) state.getBlock()).refreshConnectedModels(
                        world, pos, state.getValue(BlockPropulsionLight.FACING));
        }
        sync();
    }

    @Override public boolean shouldRefresh(net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, IBlockState before, IBlockState after) {
        return !isLamp(before) || !isLamp(after);
    }

    private static boolean isLamp(IBlockState state) {
        return state.getBlock() instanceof BlockLamp || state.getBlock() instanceof BlockLampOff
                || state.getBlock() instanceof BlockPropulsionLight;
    }

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return channel; }
    public boolean isChannelSignalPowered() { return channelSignal; }
    public boolean isParticleStreamSelected() { return particleStreamSelected; }
    public int getManualMode() { return manualOn ? (particleStreamSelected ? 2 : 1) : 0; }

    public void setParticleStreamSelected(boolean selected) {
        setParticleStreamSelected(selected, true);
    }

    public void setParticleStreamSelected(boolean selected, boolean refreshConnected) {
        if (particleStreamSelected == selected) return;
        particleStreamSelected = selected;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            if (refreshConnected && state.getBlock() instanceof BlockConnectedPropulsionLight)
                ((BlockConnectedPropulsionLight) state.getBlock())
                        .refreshConnectedModels(world, pos,
                                state.getValue(BlockPropulsionLight.FACING));
        }
        sync();
    }

    @Override public void setRedstoneChannel(int value) {
        int next = Math.max(0, value);
        if (next == channel) return;
        int old = channel;
        channel = next;
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        updateVisualState();
        sync();
    }

    @Override public boolean hasLocalRedstoneSignal() {
        return world != null && pos != null && world.isBlockPowered(pos);
    }

    @Override public void setChannelSignal(boolean powered) {
        if (channelSignal == powered) return;
        channelSignal = powered;
        if (world != null && !world.isRemote) {
            updateVisualState();
            sync();
        }
    }

    public void refreshLocalInput() {
        RedstoneChannels.inputChanged(this);
        updateVisualState();
    }

    public void toggleManualState() {
        if (channel > 0) return;
        IBlockState state = world == null || pos == null ? null : world.getBlockState(pos);
        if (state != null && state.getBlock() instanceof BlockPropulsionLight) {
            setManualMode((getManualMode() + 1) % 3, true);
            return;
        } else {
            manualOn = !manualOn;
        }
        initialized = true;
        markDirty();
        updateVisualState();
        if (state != null && state.getBlock() instanceof BlockConnectedPropulsionLight)
            ((BlockConnectedPropulsionLight) state.getBlock())
                    .refreshConnectedModels(world, pos,
                            state.getValue(BlockPropulsionLight.FACING));
        sync();
    }

    public void setManualMode(int mode, boolean refreshConnected) {
        if (channel > 0) return;
        int normalized = Math.max(0, Math.min(2, mode));
        manualOn = normalized > 0;
        particleStreamSelected = normalized == 2;
        initialized = true;
        markDirty();
        IBlockState state = world == null || pos == null ? null : world.getBlockState(pos);
        updateVisualState(refreshConnected);
        if (refreshConnected && state != null
                && state.getBlock() instanceof BlockConnectedPropulsionLight)
            ((BlockConnectedPropulsionLight) state.getBlock())
                    .refreshConnectedModels(world, pos,
                            state.getValue(BlockPropulsionLight.FACING));
        sync();
    }

    private void updateVisualState() {
        updateVisualState(true);
    }

    private void updateVisualState(boolean refreshConnected) {
        if (world == null || world.isRemote || pos == null) return;
        IBlockState state = world.getBlockState(pos);
        if (!isLamp(state)) return;
        boolean powered = world.isBlockPowered(pos) || channelSignal;
        boolean shouldBeOn = channel > 0 ? powered : manualOn;
        if (state.getBlock() instanceof BlockPropulsionLight) {
            if (state.getValue(BlockPropulsionLight.POWERED) != shouldBeOn) {
                world.setBlockState(pos,
                        state.withProperty(BlockPropulsionLight.POWERED, shouldBeOn), 3);
                if (refreshConnected
                        && state.getBlock() instanceof BlockConnectedPropulsionLight)
                    ((BlockConnectedPropulsionLight) state.getBlock())
                            .refreshConnectedModels(world, pos,
                                    state.getValue(BlockPropulsionLight.FACING));
            }
            world.checkLightFor(EnumSkyBlock.BLOCK, pos);
            return;
        }
        boolean isOn = state.getBlock() instanceof BlockLamp;
        if (shouldBeOn != isOn) {
            net.minecraft.block.Block target = shouldBeOn
                    ? ((BlockLampOff) state.getBlock()).getOnBlock()
                    : BlockLampOff.byOn(state.getBlock());
            if (target != null) {
                IBlockState replacement = target.getDefaultState()
                        .withProperty(com.vandorlabs.blocks.BlockVandorConsole.FACING,
                                state.getValue(com.vandorlabs.blocks.BlockVandorConsole.FACING))
                        .withProperty(com.vandorlabs.blocks.BlockVandorConsole.VERTICAL,
                                state.getValue(com.vandorlabs.blocks.BlockVandorConsole.VERTICAL));
                world.setBlockState(pos, replacement, 3);
            }
        }
        world.checkLightFor(EnumSkyBlock.BLOCK, pos);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (!initialized && world != null) {
            IBlockState state = world.getBlockState(pos);
            manualOn = state.getBlock() instanceof BlockPropulsionLight
                    ? state.getValue(BlockPropulsionLight.POWERED)
                    : state.getBlock() instanceof BlockLamp;
            initialized = true;
            if (!world.isRemote) markDirty();
        }
        RedstoneChannels.register(this);
        updateVisualState();
    }

    @Override public void update() {
        if (world == null || !world.isRemote || pos == null || !particleStreamSelected) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockPropulsionLight)
                || !state.getValue(BlockPropulsionLight.POWERED)) return;
        ResourceLocation name = state.getBlock().getRegistryName();
        String id = name == null ? "" : name.getResourcePath();
        int style = particleStyle(id);
        int assemblySize = 1;
        BlockConnectedPropulsionLight.ConnectedPart connectedPart = null;
        if (state.getBlock() instanceof BlockConnectedPropulsionLight) {
            IBlockState actual = state.getBlock().getActualState(state, world, pos);
            connectedPart = actual.getValue(BlockConnectedPropulsionLight.PART);
            assemblySize = connectedPart.size;
            // Only the local bottom-left member owns the assembly plume. Every
            // member calculates the same geometric center, but exactly one emits.
            if (assemblySize > 1 && (connectedPart.x != 0 || connectedPart.y != 0)) return;
        }
        int baseCount = particleCount(style);
        int count = assemblySize > 1
                ? baseCount * assemblySize + assemblySize - 1 : baseCount;
        EnumFacing facing = state.getValue(BlockPropulsionLight.FACING);
        if (assemblySize > 1) {
            net.minecraft.util.math.Vec3d center = BlockConnectedPropulsionLight
                    .particleCenter(pos, facing, connectedPart);
            float spreadScale = Math.min(1.25F, .70F + assemblySize * .08F);
            for (int i = 0; i < count; i++)
                com.vandorlabs.VandorLabs.proxy.spawnThrusterParticle(world,
                        center.x, center.y, center.z, facing, particleColor(style),
                        style, particleSpeed(style), spreadScale);
        } else {
            for (int i = 0; i < count; i++)
                com.vandorlabs.VandorLabs.proxy.spawnThrusterParticle(world, pos,
                        facing, particleColor(style), style, particleSpeed(style));
        }
    }

    private static int particleStyle(String id) {
        if (id.contains("rocket")) return 0;
        if (id.contains("ion")) return 1;
        if (id.contains("plasma") || id.contains("repulsor")) return 2;
        if (id.contains("impulse")) return 3;
        return 4;
    }

    private static int particleColor(int style) {
        switch (style) {
            case 0: return 0xFF8A24;
            case 2: return 0xC36AFF;
            case 3: return 0xFF4438;
            default: return 0x4DEBFF;
        }
    }

    private static int particleCount(int style) {
        switch (style) {
            case 2: return 3;
            case 4: return 1;
            default: return 2;
        }
    }

    private static float particleSpeed(int style) {
        switch (style) {
            case 0: return .25F;
            case 1: return .17F;
            case 2: return .13F;
            case 3: return .085F;
            default: return .10F;
        }
    }
    @Override public void invalidate() { RedstoneChannels.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { RedstoneChannels.unregister(this); super.onChunkUnload(); }

    private void sync() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        new RedstoneData.Light(channel,channelSignal,manualOn,particleStreamSelected,
                initialized).write(new NbtPrimitiveData(tag));
        tag.setBoolean("PropulsionJoin", join);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        int oldChannel = channel;
        super.readFromNBT(tag);
        RedstoneData.Light data=RedstoneData.Light.read(new NbtPrimitiveData(tag));
        channel=data.channel;
        channelSignal=data.signal;
        manualOn=data.manualOn;
        particleStreamSelected=data.particleStream;
        initialized=data.initialized;
        join = !tag.hasKey("PropulsionJoin") || tag.getBoolean("PropulsionJoin");
        if (world != null && !world.isRemote && oldChannel != channel)
            RedstoneChannels.channelChanged(this, oldChannel);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) {
            world.checkLightFor(EnumSkyBlock.BLOCK, pos);
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockConnectedPropulsionLight)
                ((BlockConnectedPropulsionLight) state.getBlock())
                        .refreshConnectedModels(world, pos,
                                state.getValue(BlockPropulsionLight.FACING));
        }
    }
}
