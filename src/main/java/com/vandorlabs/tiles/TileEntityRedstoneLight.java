package com.vandorlabs.tiles;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.blocks.BlockLamp;
import com.vandorlabs.blocks.BlockLampOff;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockConnectedPropulsionLight;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RedstoneData;
import com.vandorlabs.tiles.ScreenHousingTextures;
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
    private ChannelList channels=ChannelList.EMPTY;
    @Override public ChannelList getRedstoneChannels(){return channels;}

    private int channel;
    private boolean channelSignal;
    private int channelLevel;
    private boolean signalBrightness;
    private int particleThreshold=8;
    public boolean isSignalBrightness(){return signalBrightness;}
    public int getParticleThreshold(){return particleThreshold;}
    public int getSignalLevel(){return Math.max(channelLevel,com.vandorlabs.redstone.LoadedRedstonePower.level(world,pos));}
    public int getBrightness(){return signalBrightness?getSignalLevel():15;}
    public void configureSignalBrightness(boolean enabled,int threshold){
        signalBrightness=enabled;particleThreshold=Math.max(0,Math.min(15,threshold));markDirty();updateVisualState();sync();
    }
    @Override public int localSignalLevel(int channel){return com.vandorlabs.redstone.LoadedRedstonePower.level(world,pos);}
    @Override public void setChannelLevel(int level){
        if(channelLevel==level)return;
        channelLevel=level;channelSignal=level>0;updateVisualState();sync();
    }

    private boolean manualOn;
    private int particleLevel;
    public static final String[] PARTICLE_LEVELS={"Off","Light","Medium","Heavy"};
    public int getParticleLevel(){return particleLevel;}
    private boolean initialized;
    private boolean loadPending, initialStateDirty;
    private boolean join = true;
    private int sideTexture = ScreenHousingTextures.INDUSTRIAL_BLOCK;

    public int getSideTexture() { return sideTexture; }
    public void setSideTexture(int choice) {
        int next = ScreenHousingTextures.clamp(choice);
        if (sideTexture == next) return;
        sideTexture = next;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
        sync();
    }

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
    public boolean isParticleStreamSelected() { return particleLevel>0 && (!signalBrightness || getSignalLevel()>=particleThreshold); }
    public int getManualMode() { return manualOn ? (particleLevel>0 ? 2 : 1) : 0; }

    public void setParticleStreamSelected(boolean selected) {
        setParticleStreamSelected(selected, true);
    }

    public void setParticleStreamSelected(boolean selected, boolean refreshConnected) {
        setParticleLevel(selected?Math.max(1,particleLevel):0,refreshConnected);
    }
    public void setParticleLevel(int level){setParticleLevel(level,true);}
    public void setParticleLevel(int level,boolean refreshConnected){
        int next=Math.max(0,Math.min(3,level));if(particleLevel==next)return;
        boolean visibilityChanged=(particleLevel==0)!=(next==0);particleLevel=next;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            if (refreshConnected && visibilityChanged && state.getBlock() instanceof BlockConnectedPropulsionLight)
                ((BlockConnectedPropulsionLight) state.getBlock())
                        .refreshConnectedModels(world, pos,
                                state.getValue(BlockPropulsionLight.FACING));
        }
        sync();
    }

    @Override public void setRedstoneChannel(int value) {setRedstoneChannels(ChannelList.of(Math.max(0,value)));}
    @Override public void setRedstoneChannels(ChannelList next) {
        initializeManualState();
        if (channels.equals(next)) return;
        ChannelList old = channels;
        channels=next;channel=next.first();
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        updateVisualState();
        sync();
    }

    @Override public boolean hasLocalRedstoneSignal() {
        return world != null && pos != null && com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
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
        initializeManualState();
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
        particleLevel = normalized==2?Math.max(1,particleLevel):0;
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
        boolean powered = com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos) || channelSignal;
        boolean shouldBeOn = signalBrightness?getSignalLevel()>0:channel > 0 ? powered : manualOn;
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
            world.notifyBlockUpdate(pos,state,world.getBlockState(pos),3);
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
        // Chunk.onLoad iterates its tile map here. Power/visual callbacks can
        // load neighbours or alter that map, so initialize on the first tick.
        initializeManualState(); // Own chunk is already installed; this only captures its block state.
        loadPending = true;
    }

    private void initializeManualState() {
        if (!initialized && world != null) {
            IBlockState state = world.getBlockState(pos);
            manualOn = state.getBlock() instanceof BlockPropulsionLight
                    ? state.getValue(BlockPropulsionLight.POWERED)
                    : state.getBlock() instanceof BlockLamp;
            initialized = true;
            initialStateDirty = true;
        }
    }

    private void finishLoading() {
        initializeManualState();
        if (initialStateDirty && !world.isRemote) markDirty();
        initialStateDirty = false;
        RedstoneChannels.register(this);
        updateVisualState();
    }

    @Override public void update() {
        if (loadPending && world != null && pos != null) {
            loadPending = false;
            finishLoading();
        }
        if (world == null || !world.isRemote || pos == null || !isParticleStreamSelected()) return;
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
            connectedPart = actual.getValue(
                    ((BlockConnectedPropulsionLight) state.getBlock()).partProperty());
            assemblySize = connectedPart.size;
            // Only the local bottom-left member owns the assembly plume. Every
            // member calculates the same geometric center, but exactly one emits.
            if (assemblySize > 1 && (connectedPart.x != 0 || connectedPart.y != 0)) return;
        }
        int baseCount = particleCount(style);
        int count = assemblySize > 1
                ? baseCount * assemblySize + assemblySize - 1 : baseCount;
        int coreCount=count;count*=particleLevel==2?3:particleLevel==3?6:1;
        float widerSpread=particleLevel==2?1.75F:2.75F;
        EnumFacing facing = state.getValue(BlockPropulsionLight.FACING);
        if (assemblySize > 1) {
            net.minecraft.util.math.Vec3d center = BlockConnectedPropulsionLight
                    .particleCenter(pos, facing, connectedPart);
            float spreadScale = Math.min(1.25F, .70F + assemblySize * .08F);
            for (int i = 0; i < count; i++)
                com.vandorlabs.VandorLabs.proxy.spawnThrusterParticle(world,
                        center.x, center.y, center.z, facing, particleColor(style),
                        style, particleSpeed(style), i<coreCount?spreadScale:spreadScale*widerSpread);
        } else {
            for (int i = 0; i < count; i++){
                if(i<coreCount)com.vandorlabs.VandorLabs.proxy.spawnThrusterParticle(world,pos,facing,particleColor(style),style,particleSpeed(style));
                else com.vandorlabs.VandorLabs.proxy.spawnThrusterParticle(world,pos.getX()+.5D,pos.getY()+.5D,pos.getZ()+.5D,facing,particleColor(style),style,particleSpeed(style),widerSpread);
            }
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
        super.writeToNBT(tag);ChannelData.write(tag,channels);
        new RedstoneData.Light(channel,channelSignal,manualOn,isParticleStreamSelected(),
                initialized).write(new NbtPrimitiveData(tag));
        tag.setInteger("ParticleLevel",particleLevel);
        tag.setInteger("ChannelLevel",channelLevel);tag.setBoolean("SignalBrightness",signalBrightness);tag.setInteger("ParticleThreshold",particleThreshold);
        tag.setBoolean("PropulsionJoin", join);
        tag.setInteger("PropulsionSideTexture", sideTexture);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        ChannelList previousChannels=channels;
        int oldChannel = channel;
        super.readFromNBT(tag);
        RedstoneData.Light data=RedstoneData.Light.read(new NbtPrimitiveData(tag));
        channel=data.channel;
        channels=ChannelData.read(tag,channel);channel=channels.first();
        channelSignal=data.signal;
        channelLevel=tag.hasKey("ChannelLevel",3)?Math.max(0,Math.min(15,tag.getInteger("ChannelLevel"))):channelSignal?15:0;
        signalBrightness=tag.getBoolean("SignalBrightness");particleThreshold=tag.hasKey("ParticleThreshold",3)?Math.max(0,Math.min(15,tag.getInteger("ParticleThreshold"))):8;
        manualOn=data.manualOn;
        particleLevel=tag.hasKey("ParticleLevel",3)?Math.max(0,Math.min(3,tag.getInteger("ParticleLevel"))):data.particleStream?1:0;
        initialized=data.initialized;
        join = !tag.hasKey("PropulsionJoin") || tag.getBoolean("PropulsionJoin");
        sideTexture = tag.hasKey("PropulsionSideTexture", 3)
                ? ScreenHousingTextures.clamp(tag.getInteger("PropulsionSideTexture"))
                : ScreenHousingTextures.INDUSTRIAL_BLOCK;
        if (world != null && !world.isRemote && !previousChannels.equals(channels))
            DeferredTileLoad.schedule(this, () -> RedstoneChannels.channelChanged(this, oldChannel));
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
            else if (state.getBlock() instanceof BlockPropulsionLight)
                world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
}
