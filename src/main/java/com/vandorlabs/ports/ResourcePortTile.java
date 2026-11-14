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
    public final ResourcePortSettings settings(){return settings;}
    public final int redstoneMode(){return redstoneMode;}
    public final int appearanceMask(){return appearanceMask;}
    public final int connectedMask(){return connectedMask;}
    public final void place(EnumFacing towardPlayer){configure(ResourcePortSettings.placed(towardPlayer.getIndex()),0,ChannelList.EMPTY);}
    public final void configure(ResourcePortSettings next,int mode,ChannelList nextChannels){
        if(next==null||mode<0||mode>2||nextChannels==null)throw new IllegalArgumentException("Invalid port settings");
        if(world!=null&&!physical())throw new IllegalArgumentException("Configure the installed physical port on the server");
        if(next.equals(settings)&&mode==redstoneMode&&channels.equals(nextChannels))return;
        ChannelList old=channels;settings=next;redstoneMode=mode;channels=nextChannels;signalTick=Long.MIN_VALUE;invalidateNeighbors();fairPrepared=false;
        if(world!=null){RedstoneChannels.channelChanged(this,old);markDirty();sync();world.notifyNeighborsOfStateChange(pos,getBlockType(),false);}
    }
    public final NBTTagCompound configuration(){NBTTagCompound tag=new NBTTagCompound();ResourcePortData.write(tag,settings,redstoneMode,channels);return tag;}
    public final void configure(NBTTagCompound tag){ResourcePortSettings next=ResourcePortData.read(tag);int mode=ResourcePortData.mode(tag);ChannelList nextChannels=ResourcePortData.channels(tag);configure(next,mode,nextChannels);}
    protected final boolean physical(){return world instanceof WorldServer&&!world.isRemote&&!isInvalid()&&world.getMinecraftServer()!=null&&world.getMinecraftServer().isCallingFromMinecraftThread()&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==this;}
    protected final boolean transferAllowed(){
        if(!loaded||!physical())return false;if(redstoneMode==0)return true;long tick=world.getTotalWorldTime();
        if(signalTick!=tick){signalTick=tick;boolean signal=channels.isEmpty()?LoadedRedstonePower.isPowered(world,pos):channelPowered;active=redstoneMode==0||(redstoneMode==1)==signal;}
        return active;
    }
    protected final boolean input(int side){return settings.face(side)==ResourcePortSettings.Face.INPUT;}
    protected final boolean output(int side){return settings.face(side)==ResourcePortSettings.Face.OUTPUT;}
    protected final long availableOutput(){
        long tick=world.getTotalWorldTime();if(tick!=budgetTick){budgetTick=tick;spent=0;fairPrepared=false;java.util.Arrays.fill(faceSpent,0);}
        return settings.throughput==0?Long.MAX_VALUE:Math.max(0,settings.throughput-spent);
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
        int connected=0,on=0;long tick=world.getTotalWorldTime();
        for(int i=0;i<6;i++)if(settings.face(i)!=ResourcePortSettings.Face.DISABLED){neighbor(i);if(neighbors[i].connected)connected|=1<<i;
            boolean lit=settings.onTexture==ResourcePortSettings.OnTexture.REDSTONE?redstoneMode!=0&&transferAllowed():settings.onTexture==ResourcePortSettings.OnTexture.CONNECTED?(connected&(1<<i))!=0:transferTicks[i]!=Long.MIN_VALUE&&tick-transferTicks[i]<=5;
            if(lit)on|=1<<i;
        }
        connectedMask=connected;if(on!=appearanceMask){appearanceMask=on;sync();}
    }
    @Override public void onLoad(){super.onLoad();loaded=false;DeferredTileLoad.schedule(this,()->{loaded=true;invalidateNeighbors();RedstoneChannels.register(this);});}
    @Override public void invalidate(){loaded=false;invalidateNeighbors();RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){loaded=false;invalidateNeighbors();RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override protected void setWorldCreate(net.minecraft.world.World world){setWorld(world);}
    @Override public TileEntity channelTile(){return this;}
    @Override public int getRedstoneChannel(){return channels.first();}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public void setRedstoneChannel(int value){setRedstoneChannels(ChannelList.of(value));}
    @Override public void setRedstoneChannels(ChannelList value){configure(settings,redstoneMode,value);}
    @Override public boolean hasLocalRedstoneSignal(){return LoadedRedstonePower.isPowered(world,pos);}
    @Override public void setChannelSignal(boolean powered){channelPowered=powered;signalTick=Long.MIN_VALUE;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){serializationCalls++;super.writeToNBT(tag);ResourcePortData.write(tag,settings,redstoneMode,channels);writeContents(tag);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);try{settings=ResourcePortData.read(tag);redstoneMode=ResourcePortData.mode(tag);}catch(IllegalArgumentException malformed){settings=ResourcePortSettings.placed(EnumFacing.NORTH.getIndex());redstoneMode=0;}
        channels=ChannelData.read(tag,0);signalTick=budgetTick=Long.MIN_VALUE;spent=0;invalidateNeighbors();readContents(tag);
    }
    @Override public final NBTTagCompound getUpdateTag(){NBTTagCompound tag=new NBTTagCompound();net.minecraft.util.ResourceLocation id=TileEntity.getKey(getClass());if(id!=null)tag.setString("id",id.toString());tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());ResourcePortData.write(tag,settings,redstoneMode,channels);tag.setInteger("PortLitFaces",appearanceMask);return tag;}
    @Override public final SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public final void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){handleUpdateTag(packet.getNbtCompound());}
    @Override public final void handleUpdateTag(NBTTagCompound tag){
        // Client packets deliberately exclude inventories, fluid and stored power.
        try{settings=ResourcePortData.read(tag);redstoneMode=ResourcePortData.mode(tag);channels=ChannelData.read(tag,0);appearanceMask=tag.getInteger("PortLitFaces")&63;}catch(IllegalArgumentException ignored){}
        if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);
    }
    private void sync(){if(physical()){IBlockState state=world.getBlockState(pos);visualPackets++;world.notifyBlockUpdate(pos,state,state,2);}}
    private static final class Neighbor{BlockPos origin,position;TileEntity tile;IBlockState state;Object handler;boolean connected;long refreshAt;void clear(){tile=null;state=null;handler=null;connected=false;refreshAt=Long.MIN_VALUE;}}
}
