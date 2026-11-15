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
public abstract class NativeResourcePortEntity extends BlockEntity implements NativeChannelMember {
    public record Visual(ResourcePortSettings settings,int lit,boolean inert){}
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    public final NativePortKind kind;
    protected NativePortData.Configuration config;
    private CompoundTag retained=new CompoundTag();
    protected boolean loading;
    private boolean inert,loaded,channelPowered;
    private long signalTick=Long.MIN_VALUE,budgetTick=Long.MIN_VALUE,spent;
    private boolean active,fairPrepared;
    private final long[] faceSpent=new long[6],fairLimits=new long[6],fairCapacities=new long[6];
    private final long[] pullRequests={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    private final long[] transferTicks={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    protected final long[] capacities=new long[6],grants=new long[6];
    private final Neighbor[] neighbors=new Neighbor[6];
    private int roundRobin,lit,connectedMask,visualCooldown;
    private volatile Visual visual;
    private volatile ModelData model;
    public long neighborLookups,capabilityRefreshes,serializationCount,visualPackets,serverTicks;
    protected NativeResourcePortEntity(BlockPos pos,BlockState state,NativePortKind kind){super(NativePortBindings.ENTITIES.get(kind),pos,state);this.kind=kind;config=NativePortData.placed(state.getValue(NativePortBlock.FACING).get3DDataValue());for(int i=0;i<6;i++)neighbors[i]=new Neighbor(pos.relative(Direction.from3DDataValue(i)));publish();}
    public ResourcePortSettings settings(){return config.settings();}public NativePortData.Configuration configuration(){return config;}public boolean inert(){return inert;}public Visual visual(){return visual;}public int connectedMask(){return connectedMask;}
    @Override public ModelData getModelData(){return model;}
    private void publish(){visual=new Visual(settings(),lit,inert);model=ModelData.builder().with(VISUAL,visual).build();if(level!=null&&level.isClientSide)requestModelDataUpdate();}
    protected final boolean installed(){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||isRemoved()||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition))return false;var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this&&chunk.getBlockState(worldPosition)==getBlockState();}
    protected final boolean transferAllowed(){if(!loaded||inert||!installed())return false;if(config.redstoneMode()==0)return true;long tick=level.getGameTime();if(signalTick!=tick){signalTick=tick;boolean powered=config.channels().isEmpty()?NativeLoadedRedstonePower.level(level,worldPosition)>0:channelPowered;active=(config.redstoneMode()==1)==powered;}return active;}
    protected final boolean input(int side){return settings().face(side)==ResourcePortSettings.Face.INPUT;}protected final boolean output(int side){return settings().face(side)==ResourcePortSettings.Face.OUTPUT;}
    public final boolean configure(NativePortData.Configuration next){if(next==null||inert||!installed()||next.settings().housingChoice!= -1&&!com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(next.settings().housingChoice))return false;if(next.equals(config))return true;config=next;signalTick=Long.MIN_VALUE;fairPrepared=false;invalidateNeighbors();NativeRedstoneChannels.channelsChanged(this);setChanged();sync();level.invalidateCapabilities(worldPosition);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());return true;}
    protected final long availableOutput(){long tick=level.getGameTime();if(tick!=budgetTick){budgetTick=tick;spent=0;fairPrepared=false;Arrays.fill(faceSpent,0);}return settings().throughput==0?Long.MAX_VALUE:Math.max(0,settings().throughput-spent);}
    protected final int boundedOutput(int maximum){return (int)Math.min(Math.max(0,maximum),Math.min(Integer.MAX_VALUE,availableOutput()));}
    protected final int boundedOutput(int side,int maximum){prepareFair(side);return (int)Math.min(boundedOutput(maximum),Math.max(0,fairLimits[side]-faceSpent[side]));}
    protected final void transferred(int side,long amount,boolean outgoing){if(amount<=0)return;if(outgoing){availableOutput();spent=spent>Long.MAX_VALUE-amount?Long.MAX_VALUE:spent+amount;faceSpent[side]+=amount;}transferTicks[side]=level.getGameTime();setChanged();}
    private void prepareFair(int requested){long tick=level.getGameTime();availableOutput();if(requested>=0)pullRequests[requested]=tick;if(fairPrepared)return;int units=storedUnits(),mask=0;for(int i=0;i<6;i++){fairCapacities[i]=0;if(!output(i))continue;int count=simulateCapacity(i,units);if(i==requested||pullRequests[i]!=Long.MIN_VALUE&&tick-pullRequests[i]<40)count=Math.max(count,units);fairCapacities[i]=count;if(count>0)mask|=1<<i;}PortOutputBalance.allocate(Math.min(units,availableOutput()),mask,roundRobin,fairCapacities,fairLimits);for(int i=0;i<6;i++)fairLimits[i]+=faceSpent[i];fairPrepared=true;if(mask!=0){int first=roundRobin;for(int n=0;n<6;n++){int side=(roundRobin+n)%6;if((mask&(1<<side))!=0){first=side;break;}}for(int n=1;n<=6;n++){int side=(first+n)%6;if((mask&(1<<side))!=0){roundRobin=side;break;}}}}
    protected final void allocate(long units){prepareFair(-1);int mask=0;for(int i=0;i<6;i++){capacities[i]=Math.min(capacities[i],Math.max(0,fairLimits[i]-faceSpent[i]));if(output(i)&&capacities[i]>0)mask|=1<<i;}PortOutputBalance.allocate(Math.min(units,availableOutput()),mask,roundRobin,capacities,grants);}
    public final void neighborChanged(){invalidateNeighbors();signalTick=Long.MIN_VALUE;}
    public final void invalidateNeighbors(){for(var neighbor:neighbors)neighbor.clear();connectedMask=0;}
    protected final Object neighbor(int side){var entry=neighbors[side];if(level.getChunkSource().getChunkNow(entry.pos.getX()>>4,entry.pos.getZ()>>4)==null){entry.clear();pullRequests[side]=Long.MIN_VALUE;return null;}neighborLookups++;var tile=level.getBlockEntity(entry.pos);var state=level.getBlockState(entry.pos);long tick=level.getGameTime();if(tile!=entry.tile||state!=entry.state||tick>=entry.refreshAt||tile!=null&&tile.isRemoved()){if(tile!=entry.tile||state!=entry.state||tile!=null&&tile.isRemoved())pullRequests[side]=Long.MIN_VALUE;entry.tile=tile;entry.state=state;entry.refreshAt=tick+40;entry.handler=resolve(entry.pos,Direction.from3DDataValue(side).getOpposite());capabilityRefreshes++;}return entry.handler;}
    private static final class Neighbor {final BlockPos pos;BlockEntity tile;BlockState state;Object handler;long refreshAt;Neighbor(BlockPos pos){this.pos=pos.immutable();}void clear(){tile=null;state=null;handler=null;refreshAt=Long.MIN_VALUE;}}
    protected abstract Object resolve(BlockPos pos,Direction face);protected abstract boolean compatible(Object handler,boolean receiving);protected abstract int storedUnits();protected abstract int simulateCapacity(int side,int maximum);protected abstract void transferTick();protected abstract void writeContents(CompoundTag tag,HolderLookup.Provider registries);protected abstract void readContents(CompoundTag tag,HolderLookup.Provider registries);
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,NativeResourcePortEntity owner){if(!owner.loaded||!owner.installed()||owner.inert)return;owner.serverTicks++;if(owner.transferAllowed())owner.transferTick();if(owner.visualCooldown-->0)return;owner.visualCooldown=4;int connected=0,on=0;long tick=level.getGameTime();for(int i=0;i<6;i++)if(owner.settings().face(i)!=ResourcePortSettings.Face.DISABLED){Object handler=owner.neighbor(i);if(handler!=null)connected|=1<<i;boolean lit=switch(owner.settings().onTexture){case REDSTONE->owner.config.redstoneMode()!=0&&owner.transferAllowed();case CONNECTED->(connected&(1<<i))!=0;case TRANSFERRING->owner.transferTicks[i]!=Long.MIN_VALUE&&tick-owner.transferTicks[i]<=5;};if(lit)on|=1<<i;}owner.connectedMask=connected;if(on!=owner.lit){owner.lit=on;owner.sync();}}
    private void sync(){publish();visualPackets++;if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void onLoad(){super.onLoad();loaded=false;if(level instanceof ServerLevel)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void beforeChannelRegistration(){loaded=installed();invalidateNeighbors();}
    @Override public void setRemoved(){loaded=false;invalidateNeighbors();if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){loaded=false;invalidateNeighbors();if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override public BlockEntity channelBlockEntity(){return this;}@Override public ChannelList getRedstoneChannels(){return inert?ChannelList.EMPTY:config.channels();}@Override public void setChannelLevel(int value){channelPowered=value>0;signalTick=Long.MIN_VALUE;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){serializationCount++;tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);tag.putInt("NativePortSchema",1);tag.putString("NativePortKind",kind.id);NativePortData.write(tag,config);writeContents(tag,registries);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){if(level!=null&&level.isClientSide){config=NativePortData.read(tag);lit=tag.getInt("PortLitFaces")&63;inert=tag.getBoolean("NativePortInert");publish();return;}loading=true;try{var original=tag.copy();NativePlatformComponents.requireSupported(original,registries);if(tag.contains("NativePortSchema")&&(!tag.contains("NativePortSchema",Tag.TAG_INT)||tag.getInt("NativePortSchema")!=1||!tag.getString("NativePortKind").equals(kind.id)))throw new IllegalArgumentException("Unsupported resource port schema");if(tag.contains("PortFaces"))config=NativePortData.read(tag);try(var guard=NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}for(String key:List.of("PortEnergy","PortFluid","PortItems"))if(tag.contains(key)&&!key.equals(kind==NativePortKind.POWER?"PortEnergy":kind==NativePortKind.FLUID?"PortFluid":"PortItems"))throw new IllegalArgumentException("Resource buffer belongs to another port kind");readContents(tag,registries);retained=original;for(String key:List.of("PortEnergy","PortFluid","PortItems"))retained.remove(key);if(kind==NativePortKind.MATTER)retained.remove("Items");NativePlatformComponents.forgetLoadedFields(retained);inert=false;}catch(RuntimeException failure){retained=tag.copy();inert=true;}finally{loading=false;signalTick=budgetTick=Long.MIN_VALUE;fairPrepared=false;spent=0;invalidateNeighbors();publish();}}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){config=NativePortData.read(tag);lit=tag.getInt("PortLitFaces")&63;inert=tag.getBoolean("NativePortInert");publish();if(level!=null&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();NativePortData.write(tag,config);tag.putInt("PortLitFaces",lit);tag.putBoolean("NativePortInert",inert);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
