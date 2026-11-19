package com.vandorlabs.neoforge.ports;

import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.ports.*;
import java.util.Arrays;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Transfer scheduling belongs to the installed owner; this helper never owns resources or serializes a tile. */
public final class NativePortRuntime {
    public interface Host {
        BlockEntity tile();
        NativePortData.Configuration configuration();
        boolean installed();
        boolean inert();
        int storedUnits();
        int simulateCapacity(int side,int maximum);
        Object resolve(BlockPos pos,Direction face);
        void transferTick();
        void appearance(int lit,int connected);
        default void ticked(){}
        default void lookedUp(){}
        default void refreshed(){}
    }
    private final Host host;
    private final long[] capacities,grants,faceSpent=new long[6],fairLimits=new long[6],fairCapacities=new long[6];
    private final long[] pullRequests={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    private final long[] transferTicks={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    private final Neighbor[] neighbors=new Neighbor[6];
    private boolean loaded,channelPowered,active,fairPrepared;
    private long signalTick=Long.MIN_VALUE,budgetTick=Long.MIN_VALUE,spent;
    private int roundRobin,lit,connected,visualCooldown;
    public NativePortRuntime(Host host,long[] capacities,long[] grants){this.host=host;this.capacities=capacities;this.grants=grants;for(int i=0;i<6;i++)neighbors[i]=new Neighbor(host.tile().getBlockPos().relative(Direction.from3DDataValue(i)));}
    public ResourcePortSettings settings(){return host.configuration().settings();}
    public boolean input(int side){return settings().face(side)==ResourcePortSettings.Face.INPUT;}
    public boolean output(int side){return settings().face(side)==ResourcePortSettings.Face.OUTPUT;}
    public boolean transferAllowed(){if(!loaded||host.inert()||!host.installed())return false;var config=host.configuration();if(config.redstoneMode()==0)return true;var level=host.tile().getLevel();long tick=level.getGameTime();if(signalTick!=tick){signalTick=tick;boolean signal=config.channels().isEmpty()?NativeLoadedRedstonePower.level(level,host.tile().getBlockPos())>0:channelPowered;active=(config.redstoneMode()==1)==signal;}return active;}
    public long availableOutput(){long tick=host.tile().getLevel().getGameTime();if(tick!=budgetTick){budgetTick=tick;spent=0;fairPrepared=false;Arrays.fill(faceSpent,0);}return settings().throughput==0?Long.MAX_VALUE:Math.max(0,settings().throughput-spent);}
    public int boundedOutput(int maximum){return (int)Math.min(Math.max(0,maximum),Math.min(Integer.MAX_VALUE,availableOutput()));}
    public int boundedOutput(int side,int maximum){prepareFair(side);return (int)Math.min(boundedOutput(maximum),Math.max(0,fairLimits[side]-faceSpent[side]));}
    public void transferred(int side,long amount,boolean outgoing){if(amount<=0)return;if(outgoing){availableOutput();spent=spent>Long.MAX_VALUE-amount?Long.MAX_VALUE:spent+amount;if(side>=0)faceSpent[side]+=amount;}if(side>=0)transferTicks[side]=host.tile().getLevel().getGameTime();host.tile().setChanged();}
    private void prepareFair(int requested){long tick=host.tile().getLevel().getGameTime();availableOutput();if(requested>=0)pullRequests[requested]=tick;if(fairPrepared)return;int units=host.storedUnits(),mask=0;for(int i=0;i<6;i++){fairCapacities[i]=0;if(!output(i))continue;int count=host.simulateCapacity(i,units);if(i==requested||pullRequests[i]!=Long.MIN_VALUE&&tick-pullRequests[i]<40)count=Math.max(count,units);fairCapacities[i]=count;if(count>0)mask|=1<<i;}PortOutputBalance.allocate(Math.min(units,availableOutput()),mask,roundRobin,fairCapacities,fairLimits);for(int i=0;i<6;i++)fairLimits[i]+=faceSpent[i];fairPrepared=true;if(mask!=0){int first=roundRobin;for(int n=0;n<6;n++){int side=(roundRobin+n)%6;if((mask&(1<<side))!=0){first=side;break;}}for(int n=1;n<=6;n++){int side=(first+n)%6;if((mask&(1<<side))!=0){roundRobin=side;break;}}}}
    public void allocate(long units){prepareFair(-1);int mask=0;for(int i=0;i<6;i++){capacities[i]=Math.min(capacities[i],Math.max(0,fairLimits[i]-faceSpent[i]));if(output(i)&&capacities[i]>0)mask|=1<<i;}PortOutputBalance.allocate(Math.min(units,availableOutput()),mask,roundRobin,capacities,grants);}
    public int connectedMask(){return connected;}
    public int litMask(){return lit;}
    public void neighborChanged(){invalidateNeighbors();signalTick=Long.MIN_VALUE;}
    public void invalidateNeighbors(){for(var neighbor:neighbors)neighbor.clear();connected=0;}
    public Object neighbor(int side){var entry=neighbors[side];var level=host.tile().getLevel();if(level.getChunkSource().getChunkNow(entry.pos.getX()>>4,entry.pos.getZ()>>4)==null){entry.clear();pullRequests[side]=Long.MIN_VALUE;return null;}host.lookedUp();var tile=level.getBlockEntity(entry.pos);var state=level.getBlockState(entry.pos);long tick=level.getGameTime();if(tile!=entry.tile||state!=entry.state||tick>=entry.refreshAt||tile!=null&&tile.isRemoved()){if(tile!=entry.tile||state!=entry.state||tile!=null&&tile.isRemoved())pullRequests[side]=Long.MIN_VALUE;entry.tile=tile;entry.state=state;entry.refreshAt=tick+40;entry.handler=host.resolve(entry.pos,Direction.from3DDataValue(side).getOpposite());host.refreshed();}return entry.handler;}
    public void tick(){if(!loaded||!host.installed()||host.inert())return;host.ticked();if(transferAllowed())host.transferTick();if(visualCooldown-->0)return;visualCooldown=4;int adjacent=0,on=0;long tick=host.tile().getLevel().getGameTime();for(int i=0;i<6;i++)if(settings().face(i)!=ResourcePortSettings.Face.DISABLED){if(neighbor(i)!=null)adjacent|=1<<i;boolean show=switch(settings().onTexture){case REDSTONE->host.configuration().redstoneMode()!=0&&transferAllowed();case CONNECTED->(adjacent&(1<<i))!=0;case TRANSFERRING->transferTicks[i]!=Long.MIN_VALUE&&tick-transferTicks[i]<=5;};if(show)on|=1<<i;}connected=adjacent;if(on!=lit){lit=on;host.appearance(lit,connected);}else host.appearance(-1,connected);}
    public void onLoad(NativeChannelMember member){loaded=false;if(host.tile().getLevel() instanceof ServerLevel)NativeRedstoneChannels.queueRegistration(member);}
    public void beforeChannelRegistration(){
        // The channel lifecycle has already verified exact installed identity.
        // A newly published port can still be held until its journal retires.
        loaded=true;
        invalidateNeighbors();
    }
    public void unload(NativeChannelMember member){loaded=false;invalidateNeighbors();if(host.tile().getLevel() instanceof ServerLevel)NativeRedstoneChannels.unregister(member);}
    public void signal(int level){channelPowered=level>0;signalTick=Long.MIN_VALUE;}
    public void configurationChanged(NativeChannelMember member){signalTick=Long.MIN_VALUE;fairPrepared=false;invalidateNeighbors();NativeRedstoneChannels.channelsChanged(member);}
    public void loadedData(){signalTick=budgetTick=Long.MIN_VALUE;fairPrepared=false;spent=0;invalidateNeighbors();}
    private static final class Neighbor {final BlockPos pos;BlockEntity tile;BlockState state;Object handler;long refreshAt;Neighbor(BlockPos pos){this.pos=pos.immutable();}void clear(){tile=null;state=null;handler=null;refreshAt=Long.MIN_VALUE;}}
}
