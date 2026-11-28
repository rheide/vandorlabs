package com.vandorlabs.vehicle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Prepare local terrain and watches before moving the client away from its current anchor. */
final class CruiseArrival {
    private static final int CHUNKS_PER_TICK=4;
    private static final int NEW_WATCHES_PER_TICK=2;
    private static final long LOAD_BUDGET_NANOS=4_000_000;
    private static final Map<WorldServer,Budget> BUDGETS=new WeakHashMap<>();
    private static final Method WATCH_ENTRY=ReflectionHelper.findMethod(PlayerChunkMap.class,
        "getOrCreateEntry","func_187302_c",int.class,int.class);
    private int left,right,back,front,leadX,leadZ,corridor;
    private final java.util.Set<ChunkPos> requested=new java.util.HashSet<>();
    private final java.util.Set<ChunkPos> reading=new java.util.HashSet<>();
    private final WorldServer world;
    private final PlayerChunkMap watches;
    private final List<ChunkPos> footprint=new ArrayList<>(), prepare=new ArrayList<>();
    private final Map<EntityPlayerMP,List<PlayerChunkMapEntry>> added=new IdentityHashMap<>();


    CruiseArrival(WorldServer world) {
        this.world=world;
        watches=world.getPlayerChunkMap();
    }

    private void plan(VehiclePose pose,AxisAlignedBB hull,double dx,double dz,int lead) {
        int cx=section(pose.x),cz=section(pose.z);
        int minX=Math.min(cx-1,section(hull.minX)),maxX=Math.max(cx+1,section(hull.maxX));
        int minZ=Math.min(cz-1,section(hull.minZ)),maxZ=Math.max(cz+1,section(hull.maxZ));
        double speed=Math.hypot(dx,dz);
        int aheadX=speed<.01?0:(int)Math.round(2*dx/speed);
        int aheadZ=speed<.01?0:(int)Math.round(2*dz/speed);
        // A longer lead follows the heading as a three-chunk corridor; quantize it so small turns keep the plan.
        int corridorX=lead<=2||speed<.01?0:(int)Math.round(lead*dx/speed),corridorZ=lead<=2||speed<.01?0:(int)Math.round(lead*dz/speed);
        int corridorKey=lead<=2?0:(corridorX*4099+corridorZ);
        if(!prepare.isEmpty()&&left==minX&&right==maxX&&back==minZ&&front==maxZ&&aheadX==leadX&&aheadZ==leadZ&&corridorKey==corridor)return;
        left=minX;right=maxX;back=minZ;front=maxZ;leadX=aheadX;leadZ=aheadZ;corridor=corridorKey;
        prepare.clear();footprint.clear();
        if(lead>2){
            java.util.Set<ChunkPos> watched=new java.util.LinkedHashSet<>(),loaded=new java.util.LinkedHashSet<>();
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)watched.add(new ChunkPos(x,z));
            for(int step=1;step<=lead;step++){
                int px=cx+(int)Math.round(step*dx/speed),pz=cz+(int)Math.round(step*dz/speed);
                for(int ox=-1;ox<=1;ox++)for(int oz=-1;oz<=1;oz++)watched.add(new ChunkPos(px+ox,pz+oz));
            }
            // The population border keeps every corridor chunk sendable when the riders reach it.
            for(ChunkPos column:watched)for(int ox=-1;ox<=1;ox++)for(int oz=-1;oz<=1;oz++)loaded.add(new ChunkPos(column.x+ox,column.z+oz));
            footprint.addAll(watched);prepare.addAll(loaded);
        }else{
        // Ship the hull and its approach, including the vanilla population border.
        minX=Math.min(minX,cx+aheadX);maxX=Math.max(maxX,cx+aheadX);
        minZ=Math.min(minZ,cz+aheadZ);maxZ=Math.max(maxZ,cz+aheadZ);
        for(int x=minX-1;x<=maxX+1;x++)for(int z=minZ-1;z<=maxZ+1;z++){
            ChunkPos column=new ChunkPos(x,z);
            prepare.add(column);
            if(x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ)footprint.add(column);
        }
        }
        Comparator<ChunkPos> closestFirst=lead>2?Comparator.comparingDouble(c->Math.pow(c.x-cx,2)+Math.pow(c.z-cz,2))
                :Comparator.comparingDouble(c->Math.pow(c.x-cx-aheadX*.5,2)+Math.pow(c.z-cz-aheadZ*.5,2));
        prepare.sort(closestFirst);footprint.sort(closestFirst);
        for(Map.Entry<EntityPlayerMP,List<PlayerChunkMapEntry>> entry:added.entrySet()){
            entry.getValue().removeIf(watch->{
                if(footprint.contains(watch.getPos()))return false;
                release(entry.getKey(),watch);
                return true;
            });
        }
        requested.removeIf(column->{
            if(prepare.contains(column))return false;
            retire(column);
            return true;
        });
    }

    boolean ready(EntityGroundVehicle craft,VehiclePose pose,AxisAlignedBB hull,double dx,double dz) {
        return ready(craft,pose,hull,dx,dz,2);
    }

    /** @param lead chunks to prepare along the heading; more than two follows a corridor rather than a box */
    boolean ready(EntityGroundVehicle craft,VehiclePose pose,AxisAlignedBB hull,double dx,double dz,int lead) {
        plan(pose,hull,dx,dz,lead);
        Budget budget=BUDGETS.computeIfAbsent(world,ignored->new Budget());
        budget.reset(world.getTotalWorldTime());
        net.minecraft.world.gen.ChunkProviderServer provider=world.getChunkProvider();
        for(ChunkPos column:prepare){
            if(provider.getLoadedChunk(column.x,column.z)!=null||reading.contains(column))continue;
            if(!budget.take())break;
            if(provider.isChunkGeneratedAt(column.x,column.z)){
                reading.add(column);
                provider.loadChunk(column.x,column.z,()->{
                    reading.remove(column);
                    if(!requested.contains(column)&&!prepare.contains(column))retire(column);
                });
            }else{
                if(!budget.generate())break;
                provider.provideChunk(column.x,column.z);
            }
            requested.add(column);
        }
        boolean ready=true;
        List<Entity> riders=craft.getPassengers();
        added.entrySet().removeIf(entry->{
            if(riders.contains(entry.getKey()))return false;
            release(entry.getKey(),entry.getValue());
            return true;
        });
        for(ChunkPos column:footprint) {
            Chunk chunk=world.getChunkProvider().getLoadedChunk(column.x,column.z);
            if(chunk==null) {
                ready=false;
                continue;
            }
            if(!chunk.isTerrainPopulated()){ready=false;continue;}
            for(Entity rider:riders)if(rider instanceof EntityPlayerMP) {
                EntityPlayerMP player=(EntityPlayerMP)rider;
                if(player.isDead||player.world!=world||player.connection==null)continue;
                net.minecraft.util.math.Vec3d seat=craft.seatPosition(pose,player);
                if(!withinView(column,seat.x,seat.z))continue;
                if(watches.isPlayerWatchingChunk(player,column.x,column.z))continue;
                PlayerChunkMapEntry watch=watches.getEntry(column.x,column.z);
                // Adding a rider to an existing, sent entry immediately serializes the
                // whole column. Spread those initial packets even at a warm destination.
                if((watch==null||!watch.containsPlayer(player))&&!budget.addWatch()){
                    ready=false;
                    continue;
                }
                if(watch==null)watch=watch(column);
                if(!watch.containsPlayer(player)) {
                    watch.addPlayer(player);
                    added.computeIfAbsent(player,ignored->new ArrayList<>()).add(watch);
                }
                if(!watch.sendToPlayers())ready=false;
            }
        }
        return ready;
    }

    boolean requested(ChunkPos column){return requested.contains(column)||prepare.contains(column);}

    void close() {
        for(Map.Entry<EntityPlayerMP,List<PlayerChunkMapEntry>> entry:added.entrySet())
            release(entry.getKey(),entry.getValue());
        added.clear();
        prepare.clear();
        for(ChunkPos column:requested)retire(column);
        requested.clear();
    }

    private void retire(ChunkPos column){
        if(watches.getEntry(column.x,column.z)==null){
            Chunk chunk=world.getChunkProvider().getLoadedChunk(column.x,column.z);
            if(chunk!=null)world.getChunkProvider().queueUnload(chunk);
        }
    }

    private void release(EntityPlayerMP player,List<PlayerChunkMapEntry> entries) {
        for(PlayerChunkMapEntry watch:entries)release(player,watch);
    }

    private void release(EntityPlayerMP player,PlayerChunkMapEntry watch) {
        ChunkPos column=watch.getPos();
        if(watches.getEntry(column.x,column.z)!=watch)return;
        // Vanilla adopts the destination watches when the player changes tracking origin.
        if(!player.isDead&&player.world==world&&withinView(column,player.managedPosX,player.managedPosZ))return;
        watch.removePlayer(player);
    }

    private boolean withinView(ChunkPos column,double x,double z) {
        int radius=world.getMinecraftServer().getPlayerList().getViewDistance();
        // Vanilla's PlayerChunkMap uses an int cast before shifting, including near zero.
        return Math.abs(column.x-((int)x>>4))<=radius&&Math.abs(column.z-((int)z>>4))<=radius;
    }

    private PlayerChunkMapEntry watch(ChunkPos column) {
        try {return (PlayerChunkMapEntry)WATCH_ENTRY.invoke(watches,column.x,column.z);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot prepare the flight arrival watch",failure);}
    }

    private static int section(double coordinate) {return MathHelper.floor(coordinate)>>4;}

    private static final class Budget {
        private long tick=Long.MIN_VALUE, started;
        private int used,newWatches;
        boolean addWatch(){
            if(newWatches>=NEW_WATCHES_PER_TICK)return false;
            newWatches++;
            return true;
        }
        private boolean generated;
        boolean generate(){
            if(generated)return false;
            generated=true;
            return true;
        }
        void reset(long now) {
            if(tick==now)return;
            tick=now;
            started=System.nanoTime();
            used=0;newWatches=0;generated=false;
        }
        boolean take() {
            if(used>=CHUNKS_PER_TICK || used>0&&System.nanoTime()-started>=LOAD_BUDGET_NANOS)return false;
            used++;
            return true;
        }
    }
}
