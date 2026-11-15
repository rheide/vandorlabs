package com.vandorlabs.ports;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.DeferredTileLoad;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Physical transfer owner. Carried simulation tiles can expose appearance but cannot transfer. */
public abstract class ResourcePortTile extends TileEntity implements ITickable,RedstoneChannelMember {
    private boolean inert;
    private NBTTagCompound retained=new NBTTagCompound(),failed;
    public final boolean inert(){return inert;}
    private ResourcePortSettings settings=ResourcePortSettings.placed(EnumFacing.NORTH.getIndex());
    private ChannelList channels=ChannelList.EMPTY;
    /** 0 = always active; 1 = ON activates; 2 = OFF activates. */
    private int redstoneMode;
    private boolean channelPowered,loaded;
    private long budgetTick=Long.MIN_VALUE,spent;
    private boolean fairPrepared;
    private final long[] faceSpent=new long[6],fairLimits=new long[6],fairCapacities=new long[6];
    private final long[] pullRequests={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    private long signalTick=Long.MIN_VALUE;
    private boolean active;
    private final long[] transferTicks={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    private final Neighbor[] neighbors=new Neighbor[6];
    protected final long[] capacities=new long[6],grants=new long[6];
    private int roundRobin,appearanceMask,connectedMask;
    private int visualCooldown;
    public long neighborLookups,capabilityRefreshes,visualPackets,serializationCalls;
    protected ResourcePortTile(){for(int i=0;i<6;i++)neighbors[i]=new Neighbor();}
    public ResourcePortSettings settings(){return settings;}
    public final int redstoneMode(){return redstoneMode;}
    public final int appearanceMask(){return appearanceMask;}
    public final int connectedMask(){return connectedMask;}
    public final void place(EnumFacing towardPlayer){configure(ResourcePortSettings.placed(towardPlayer.getIndex()),0,ChannelList.EMPTY);}
    public final void configure(ResourcePortSettings next,int mode,ChannelList nextChannels){
        if(inert||next==null||mode<0||mode>2||nextChannels==null||next.housingChoice!= -1&&!com.vandorlabs.tiles.ScreenHousingTextures.validChoice(next.housingChoice))throw new IllegalArgumentException("Invalid port settings");
        if(world!=null&&!physical())throw new IllegalArgumentException("Configure the installed physical port on the server");
        if(next.equals(settings())&&mode==redstoneMode&&channels.equals(nextChannels))return;
        ChannelList old=channels;ResourcePortSettings previous=settings();int previousMode=redstoneMode;ChannelList previousChannels=channels;settings=next;configurationChanged(previous,next,previousMode!=mode||!previousChannels.equals(nextChannels));redstoneMode=mode;channels=nextChannels;signalTick=Long.MIN_VALUE;invalidateNeighbors();fairPrepared=false;
        if(world!=null){RedstoneChannels.channelChanged(this,old);markDirty();sync();world.notifyNeighborsOfStateChange(pos,getBlockType(),false);}
    }
    public final NBTTagCompound configuration(){NBTTagCompound tag=new NBTTagCompound();ResourcePortData.write(tag,settings(),redstoneMode,channels);return tag;}
    public final void configure(NBTTagCompound tag){ResourcePortSettings next=ResourcePortData.read(tag);int mode=ResourcePortData.mode(tag);ChannelList nextChannels=ResourcePortData.channels(tag);configure(next,mode,nextChannels);}
    protected final boolean physical(){return !inert&&world instanceof WorldServer&&!world.isRemote&&!isInvalid()&&world.getMinecraftServer()!=null&&world.getMinecraftServer().isCallingFromMinecraftThread()&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==this;}
    protected final boolean transferAllowed(){
        if(!loaded||!physical())return false;if(redstoneMode==0)return true;long tick=world.getTotalWorldTime();
        if(signalTick!=tick){signalTick=tick;boolean signal=channels.isEmpty()?LoadedRedstonePower.isPowered(world,pos):channelPowered;active=redstoneMode==0||(redstoneMode==1)==signal;}
        return active;
    }
    protected final boolean input(int side){return !inert&&settings().face(side)==ResourcePortSettings.Face.INPUT;}
    protected final boolean output(int side){return !inert&&settings().face(side)==ResourcePortSettings.Face.OUTPUT;}
    protected final long availableOutput(){
        long tick=world.getTotalWorldTime();if(tick!=budgetTick){budgetTick=tick;spent=0;fairPrepared=false;java.util.Arrays.fill(faceSpent,0);}
        return settings().throughput==0?Long.MAX_VALUE:Math.max(0,settings().throughput-spent);
    }
    protected final void transferred(int side,long amount,boolean outgoing){
        if(amount<=0)return;if(outgoing){availableOutput();spent=spent>Long.MAX_VALUE-amount?Long.MAX_VALUE:spent+amount;faceSpent[side]+=amount;}
        transferTicks[side]=world.getTotalWorldTime();markDirty();
    }
    protected final int boundedOutput(int maximum){return (int)Math.min(Math.max(0,maximum),Math.min(Integer.MAX_VALUE,availableOutput()));}
    /** Passive pipe extraction and active pushes consume the same per-face fair allocation. */
    protected final int boundedOutput(int side,int maximum){prepareFairOutput(side);return (int)Math.min(boundedOutput(maximum),Math.max(0,fairLimits[side]-faceSpent[side]));}
    private void prepareFairOutput(int requestedSide){
        long tick=world.getTotalWorldTime();availableOutput();if(requestedSide>=0)pullRequests[requestedSide]=tick;if(fairPrepared)return;
        int content=storedUnits(),mask=0;for(int i=0;i<6;i++){
            fairCapacities[i]=0;if(!output(i))continue;int capacity=simulateOutputCapacity(i,content);
            // Some pipes expose no receiving capability but actively extract from us.
            if(i==requestedSide||pullRequests[i]!=Long.MIN_VALUE&&tick-pullRequests[i]<40)capacity=Math.max(capacity,content);
            fairCapacities[i]=capacity;if(capacity>0)mask|=1<<i;
        }
        PortOutputBalance.allocate(Math.min(content,availableOutput()),mask,roundRobin,fairCapacities,fairLimits);for(int i=0;i<6;i++)fairLimits[i]+=faceSpent[i];fairPrepared=true;
        // Advance between eligible faces, rather than biasing sparse masks by raw ordinal distance.
        if(mask!=0){int first=-1;for(int offset=0;offset<6;offset++){int side=(roundRobin+offset)%6;if((mask&(1<<side))!=0){first=side;break;}}for(int offset=1;offset<=6;offset++){int next=(first+offset)%6;if((mask&(1<<next))!=0){roundRobin=next;break;}}}
    }
    protected final int allocate(long content){prepareFairOutput(-1);int mask=0;for(int i=0;i<6;i++){capacities[i]=Math.min(capacities[i],Math.max(0,fairLimits[i]-faceSpent[i]));if(output(i)&&capacities[i]>0)mask|=1<<i;}PortOutputBalance.allocate(Math.min(content,availableOutput()),mask,roundRobin,capacities,grants);return mask;}
    public final void neighborChanged(){invalidateNeighbors();signalTick=Long.MIN_VALUE;RedstoneChannels.inputChanged(this);}
    public final void invalidateNeighbors(){for(Neighbor neighbor:neighbors)neighbor.clear();connectedMask=0;}
    /** Never asks an unloaded chunk for either its block or its tile. Identity is rechecked on every use. */
    protected final Object neighbor(int side){
        Neighbor cached=neighbors[side];if(cached.origin!=pos){cached.origin=pos;cached.position=pos.offset(EnumFacing.getFront(side));cached.clear();}BlockPos at=cached.position;
        if(!world.isBlockLoaded(at)){cached.clear();pullRequests[side]=Long.MIN_VALUE;return null;}
        neighborLookups++;TileEntity tile=world.getTileEntity(at);IBlockState state=world.getBlockState(at);long tick=world.getTotalWorldTime();
        if(tile!=cached.tile||state!=cached.state||tick>=cached.refreshAt||tile!=null&&tile.isInvalid()){
            if(tile!=cached.tile||state!=cached.state||tile!=null&&tile.isInvalid())pullRequests[side]=Long.MIN_VALUE;
            cached.tile=tile;cached.state=state;cached.refreshAt=tick+40;capabilityRefreshes++;
            cached.handler=tile==null||tile.isInvalid()?null:findHandler(tile,EnumFacing.getFront(side).getOpposite());
            cached.connected=cached.handler!=null&&ResourcePortConnections.connected(tile,EnumFacing.getFront(side).getOpposite());
        }
        return cached.handler;
    }
    protected void configurationChanged(ResourcePortSettings previous,ResourcePortSettings next,boolean signalChanged){}
    /** Return a comparison/read copy only; failure recovery always retains the original tag. */
    protected NBTTagCompound normalizedReadData(NBTTagCompound original){return original;}
    protected boolean storedAppearanceTick(){return false;}
    protected void writePublicContents(NBTTagCompound tag){}
    protected void writeConfiguration(NBTTagCompound tag){ResourcePortData.write(tag,settings(),redstoneMode,channels);}
    protected void readPublicContents(NBTTagCompound tag){}
    protected final void installReadSettings(ResourcePortSettings next){settings=next;signalTick=budgetTick=Long.MIN_VALUE;spent=0;fairPrepared=false;invalidateNeighbors();}
    protected abstract Object findHandler(TileEntity tile,EnumFacing side);
    protected abstract boolean compatible(Object handler,boolean receiving);
    protected abstract int storedUnits();
    protected abstract int simulateOutputCapacity(int side,int maximum);
    protected abstract void transferTick();
    protected abstract void writeContents(NBTTagCompound tag);
    protected abstract void readContents(NBTTagCompound tag);
    @Override public final void update(){
        if(!loaded||!physical())return;
        if(transferAllowed())transferTick();
        if(visualCooldown>0){visualCooldown--;return;}visualCooldown=4;
        ResourcePortSettings visual=settings();int connected=0,on=0;long tick=world.getTotalWorldTime();
        for(int i=0;i<6;i++)if(visual.face(i)!=ResourcePortSettings.Face.DISABLED){neighbor(i);if(neighbors[i].connected)connected|=1<<i;
            boolean lit=visual.onTexture==ResourcePortSettings.OnTexture.REDSTONE?redstoneMode!=0&&transferAllowed():visual.onTexture==ResourcePortSettings.OnTexture.CONNECTED?(connected&(1<<i))!=0:transferTicks[i]!=Long.MIN_VALUE&&tick-transferTicks[i]<=5;
            if(lit)on|=1<<i;
        }
        connectedMask=connected;boolean storedChanged=storedAppearanceTick();if(on!=appearanceMask||storedChanged){appearanceMask=on;sync();}
    }
    @Override public void onLoad(){super.onLoad();loaded=false;DeferredTileLoad.schedule(this,()->{if(!inert){loaded=true;invalidateNeighbors();RedstoneChannels.register(this);}});}
    @Override public void invalidate(){loaded=false;invalidateNeighbors();RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){loaded=false;invalidateNeighbors();RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override protected void setWorldCreate(net.minecraft.world.World world){setWorld(world);}
    @Override public TileEntity channelTile(){return this;}
    @Override public int getRedstoneChannel(){return getRedstoneChannels().first();}
    @Override public ChannelList getRedstoneChannels(){return inert?ChannelList.EMPTY:channels;}
    @Override public void setRedstoneChannel(int value){setRedstoneChannels(ChannelList.of(value));}
    @Override public void setRedstoneChannels(ChannelList value){configure(settings(),redstoneMode,value);}
    @Override public boolean hasLocalRedstoneSignal(){return LoadedRedstonePower.isPowered(world,pos);}
    @Override public void setChannelSignal(boolean powered){channelPowered=powered;signalTick=Long.MIN_VALUE;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        serializationCalls++;
        if(inert&&failed!=null){net.minecraft.util.ResourceLocation id=TileEntity.getKey(getClass());if(id!=null)tag.setString("id",id.toString());tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());tag.merge(failed.copy());return tag;}
        tag.merge(retained.copy());super.writeToNBT(tag);
        // Retain unavailable foreign providers as opaque compounds; live providers remain authoritative.
        if(retained.hasKey("ForgeCaps",10)){NBTTagCompound saved=retained.getCompoundTag("ForgeCaps"),current=tag.getCompoundTag("ForgeCaps");for(String key:saved.getKeySet())if(!current.hasKey(key))current.setTag(key,saved.getTag(key).copy());if(!current.hasNoTags())tag.setTag("ForgeCaps",current);}
        for(String key:new String[]{"PortEnergy","PortFluid","Items"})tag.removeTag(key);writeConfiguration(tag);writeContents(tag);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        NBTTagCompound original=tag.copy();inert=true;failed=original;
        try{super.readFromNBT(tag);NBTTagCompound normalized=normalizedReadData(tag);settings=ResourcePortData.read(normalized);redstoneMode=ResourcePortData.mode(normalized);channels=ResourcePortData.channels(normalized);readContents(tag);retained=original.copy();for(String key:new String[]{"id","x","y","z"})retained.removeTag(key);inert=false;failed=null;}catch(RuntimeException malformed){inert=true;}
        signalTick=budgetTick=Long.MIN_VALUE;spent=0;fairPrepared=false;invalidateNeighbors();
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability,EnumFacing side){return !inert&&super.hasCapability(capability,side);}
    @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability,EnumFacing side){return inert?null:super.getCapability(capability,side);}
    @Override public final NBTTagCompound getUpdateTag(){NBTTagCompound tag=new NBTTagCompound();net.minecraft.util.ResourceLocation id=TileEntity.getKey(getClass());if(id!=null)tag.setString("id",id.toString());tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());ResourcePortData.write(tag,settings(),redstoneMode,channels);tag.setInteger("PortLitFaces",appearanceMask);tag.setBoolean("PortInert",inert);if(!inert)writePublicContents(tag);return tag;}
    @Override public final SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public final void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){handleUpdateTag(packet.getNbtCompound());}
    @Override public final void handleUpdateTag(NBTTagCompound tag){
        // Client packets deliberately exclude inventories, fluid and stored power.
        try{settings=ResourcePortData.read(tag);redstoneMode=ResourcePortData.mode(tag);channels=ChannelData.read(tag,0);appearanceMask=tag.getInteger("PortLitFaces")&63;inert=tag.getBoolean("PortInert");if(!inert)readPublicContents(tag);}catch(IllegalArgumentException ignored){}
        if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);
    }
    protected final void sync(){if(physical()){IBlockState state=world.getBlockState(pos);visualPackets++;world.notifyBlockUpdate(pos,state,state,2);}}
    private static final class Neighbor{BlockPos origin,position;TileEntity tile;IBlockState state;Object handler;boolean connected;long refreshAt;void clear(){tile=null;state=null;handler=null;connected=false;refreshAt=Long.MIN_VALUE;}}
}
