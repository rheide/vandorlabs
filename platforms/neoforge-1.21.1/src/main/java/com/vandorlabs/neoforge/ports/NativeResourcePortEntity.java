package com.vandorlabs.neoforge.ports;

import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Loaded-only server transfer engine. Public state has configuration and six activity bits only. */
public abstract class NativeResourcePortEntity extends BlockEntity implements NativeChannelMember,NativePortOwner {
    public record Visual(ResourcePortSettings settings,int lit,boolean inert){}
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    public final NativePortKind kind;
    protected NativePortData.Configuration config;
    private CompoundTag retained=new CompoundTag();
    protected boolean loading;
    private boolean inert;
    protected final long[] capacities=new long[6],grants=new long[6];
    private final NativePortRuntime runtime;
    private int lit,connectedMask;
    private volatile Visual visual;
    private volatile ModelData model;
    public long neighborLookups,capabilityRefreshes,serializationCount,visualPackets,serverTicks;
    protected NativeResourcePortEntity(BlockPos pos,BlockState state,NativePortKind kind){super(NativePortBindings.ENTITIES.get(kind),pos,state);this.kind=kind;config=NativePortData.placed(state.getValue(NativePortBlock.FACING).get3DDataValue());runtime=new NativePortRuntime(new NativePortRuntime.Host(){
        public BlockEntity tile(){return NativeResourcePortEntity.this;}
        public NativePortData.Configuration configuration(){return config;}
        public boolean installed(){return NativeResourcePortEntity.this.installed();}
        public boolean inert(){return NativeResourcePortEntity.this.inert;}
        public int storedUnits(){return NativeResourcePortEntity.this.storedUnits();}
        public int simulateCapacity(int side,int maximum){return NativeResourcePortEntity.this.simulateCapacity(side,maximum);}
        public Object resolve(BlockPos pos,Direction face){return NativeResourcePortEntity.this.resolve(pos,face);}
        public void transferTick(){NativeResourcePortEntity.this.transferTick();}
        public void appearance(int next,int adjacent){connectedMask=adjacent;if(next>=0){lit=next;sync();}}
        public void ticked(){serverTicks++;}public void lookedUp(){neighborLookups++;}public void refreshed(){capabilityRefreshes++;}
    },capacities,grants);publish();}
    public ResourcePortSettings settings(){return config.settings();}public NativePortData.Configuration configuration(){return config;}public boolean inert(){return inert;}public Visual visual(){return visual;}public int connectedMask(){return connectedMask;}
    @Override public ModelData getModelData(){return model;}
    private void publish(){visual=new Visual(settings(),lit,inert);model=ModelData.builder().with(VISUAL,visual).build();if(level!=null&&level.isClientSide)requestModelDataUpdate();}
    protected final boolean installed(){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||isRemoved()||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition))return false;var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this&&chunk.getBlockState(worldPosition)==getBlockState();}
    protected final boolean transferAllowed(){return runtime.transferAllowed();}
    protected final boolean input(int side){return runtime.input(side);}protected final boolean output(int side){return runtime.output(side);}
    public final boolean configure(NativePortData.Configuration next){if(next==null||inert||!installed()||next.settings().housingChoice!= -1&&!com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(next.settings().housingChoice))return false;if(next.equals(config))return true;config=next;runtime.configurationChanged(this);setChanged();sync();level.invalidateCapabilities(worldPosition);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());return true;}
    protected final NativePortRuntime runtime(){return runtime;}
    protected final long availableOutput(){return runtime.availableOutput();}
    protected final int boundedOutput(int max){return runtime.boundedOutput(max);}protected final int boundedOutput(int side,int max){return runtime.boundedOutput(side,max);}
    protected final void transferred(int side,long amount,boolean outgoing){runtime.transferred(side,amount,outgoing);}
    protected final void allocate(long units){runtime.allocate(units);}
    public final void neighborChanged(){runtime.neighborChanged();}public final void invalidateNeighbors(){runtime.invalidateNeighbors();}
    protected final Object neighbor(int side){return runtime.neighbor(side);}
    @Override public BlockEntity portBlockEntity(){return this;}@Override public NativePortKind portKind(){return kind;}
    protected abstract Object resolve(BlockPos pos,Direction face);protected abstract boolean compatible(Object handler,boolean receiving);protected abstract int storedUnits();protected abstract int simulateCapacity(int side,int maximum);protected abstract void transferTick();protected abstract void writeContents(CompoundTag tag,HolderLookup.Provider registries);protected abstract void readContents(CompoundTag tag,HolderLookup.Provider registries);
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,NativeResourcePortEntity owner){owner.runtime.tick();}
    private void sync(){publish();visualPackets++;if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void onLoad(){super.onLoad();runtime.onLoad(this);}
    @Override public void beforeChannelRegistration(){runtime.beforeChannelRegistration();}
    @Override public void setRemoved(){runtime.unload(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){runtime.unload(this);super.onChunkUnloaded();}
    @Override public BlockEntity channelBlockEntity(){return this;}@Override public ChannelList getRedstoneChannels(){return inert?ChannelList.EMPTY:config.channels();}@Override public void setChannelLevel(int value){runtime.signal(value);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){serializationCount++;tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);tag.putInt("NativePortSchema",1);tag.putString("NativePortKind",kind.id);NativePortData.write(tag,config);writeContents(tag,registries);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){if(level!=null&&level.isClientSide){config=NativePortData.read(tag);lit=tag.getInt("PortLitFaces")&63;inert=tag.getBoolean("NativePortInert");publish();return;}loading=true;try{var original=tag.copy();NativePlatformComponents.requireSupported(original,registries);if(tag.contains("NativePortSchema")&&(!tag.contains("NativePortSchema",Tag.TAG_INT)||tag.getInt("NativePortSchema")!=1||!tag.getString("NativePortKind").equals(kind.id)))throw new IllegalArgumentException("Unsupported resource port schema");if(tag.contains("PortFaces"))config=NativePortData.read(tag);try(var guard=NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}for(String key:List.of("PortEnergy","PortFluid","PortItems"))if(tag.contains(key)&&!key.equals(kind==NativePortKind.POWER?"PortEnergy":kind==NativePortKind.FLUID?"PortFluid":"PortItems"))throw new IllegalArgumentException("Resource buffer belongs to another port kind");readContents(tag,registries);retained=original;for(String key:List.of("PortEnergy","PortFluid","PortItems"))retained.remove(key);if(kind==NativePortKind.MATTER)retained.remove("Items");NativePlatformComponents.forgetLoadedFields(retained);inert=false;}catch(RuntimeException failure){retained=tag.copy();inert=true;}finally{loading=false;runtime.loadedData();publish();}}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){config=NativePortData.read(tag);lit=tag.getInt("PortLitFaces")&63;inert=tag.getBoolean("NativePortInert");publish();if(level!=null&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();NativePortData.write(tag,config);tag.putInt("PortLitFaces",lit);tag.putBoolean("NativePortInert",inert);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
