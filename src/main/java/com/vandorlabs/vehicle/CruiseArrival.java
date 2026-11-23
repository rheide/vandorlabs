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
    private static final long LOAD_BUDGET_NANOS=4_000_000;
    private static final Map<WorldServer,Budget> BUDGETS=new WeakHashMap<>();
    private static final Method WATCH_ENTRY=ReflectionHelper.findMethod(PlayerChunkMap.class,
        "getOrCreateEntry","func_187302_c",int.class,int.class);
    final double x,z;
    private final WorldServer world;
    private final boolean generate;
    private final PlayerChunkMap watches;
    private final List<ChunkPos> footprint=new ArrayList<>(), prepare=new ArrayList<>();
    private final Map<EntityPlayerMP,List<PlayerChunkMapEntry>> added=new IdentityHashMap<>();
    private int next;

    CruiseArrival(WorldServer world,double x,double z,AxisAlignedBB hull,boolean generate) {
        this.world=world;
        this.x=x;this.z=z;this.generate=generate;
        watches=world.getPlayerChunkMap();
        int cx=section(x),cz=section(z);
        int minX=Math.min(cx-1,section(hull.minX)),maxX=Math.max(cx+1,section(hull.maxX));
        int minZ=Math.min(cz-1,section(hull.minZ)),maxZ=Math.max(cz+1,section(hull.maxZ));
        // The perimeter allows vanilla to populate and light the inner footprint.
        for(int xx=minX-1;xx<=maxX+1;xx++)for(int zz=minZ-1;zz<=maxZ+1;zz++) {
            ChunkPos column=new ChunkPos(xx,zz);
            if(generate)prepare.add(column);
            if(xx>=minX&&xx<=maxX&&zz>=minZ&&zz<=maxZ)footprint.add(column);
        }
        prepare.sort(Comparator.comparingInt(column->Math.max(Math.abs(column.x-cx),Math.abs(column.z-cz))));
    }

    boolean ready(EntityGroundVehicle craft,VehiclePose pose) {
        Budget budget=BUDGETS.computeIfAbsent(world,ignored->new Budget());
        budget.reset(world.getTotalWorldTime());
        while(next<prepare.size()) {
            ChunkPos column=prepare.get(next);
            if(world.getChunkProvider().getLoadedChunk(column.x,column.z)==null) {
                if(!budget.take())break;
                world.getChunkProvider().provideChunk(column.x,column.z);
            }
            next++;
        }
        // Start sending populated columns while the outer lighting perimeter is still loading.
        // A large footprint or a slow generator can span many server ticks.
        boolean ready=next==prepare.size();
        List<Entity> riders=craft.getPassengers();
        added.entrySet().removeIf(entry->{
            if(riders.contains(entry.getKey()))return false;
            release(entry.getKey(),entry.getValue());
            return true;
        });
        for(ChunkPos column:footprint) {
            Chunk chunk=generate?world.getChunkProvider().getLoadedChunk(column.x,column.z):
                CruiseResidentReanchor.loaded(world,column.x,column.z);
            if(chunk==null) {
                if(generate && next==prepare.size())next=0;
                ready=false;
                continue;
            }
            if(!chunk.isTerrainPopulated()){ready=false;continue;}
            for(Entity rider:riders)if(rider instanceof EntityPlayerMP) {
                EntityPlayerMP player=(EntityPlayerMP)rider;
                if(player.isDead||player.world!=world||player.connection==null)continue;
                net.minecraft.util.math.Vec3d seat=craft.seatPosition(pose,player);
                if(!withinView(column,seat.x,seat.z))continue;
                PlayerChunkMapEntry watch=watch(column);
                if(!watch.containsPlayer(player)) {
                    watch.addPlayer(player);
                    added.computeIfAbsent(player,ignored->new ArrayList<>()).add(watch);
                }
                if(!watch.sendToPlayers())ready=false;
            }
        }
        return ready;
    }

    void close() {
        for(Map.Entry<EntityPlayerMP,List<PlayerChunkMapEntry>> entry:added.entrySet())
            release(entry.getKey(),entry.getValue());
        added.clear();
    }

    private void release(EntityPlayerMP player,List<PlayerChunkMapEntry> entries) {
        for(PlayerChunkMapEntry watch:entries) {
            ChunkPos column=watch.getPos();
            if(watches.getEntry(column.x,column.z)!=watch)continue;
            // The normal movement update adopts temporary destination watches that are now in range.
            if(!player.isDead&&player.world==world&&withinView(column,player.managedPosX,player.managedPosZ))continue;
            watch.removePlayer(player);
        }
    }

    private boolean withinView(ChunkPos column,double x,double z) {
        int radius=world.getMinecraftServer().getPlayerList().getViewDistance();
        // Vanilla's PlayerChunkMap uses an int cast before shifting, including near zero.
        return Math.abs(column.x-((int)x>>4))<=radius&&Math.abs(column.z-((int)z>>4))<=radius;
    }

    private PlayerChunkMapEntry watch(ChunkPos column) {
        PlayerChunkMapEntry existing=watches.getEntry(column.x,column.z);
        if(existing!=null)return existing;
        try {return (PlayerChunkMapEntry)WATCH_ENTRY.invoke(watches,column.x,column.z);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot prepare the flight arrival watch",failure);}
    }

    private static int section(double coordinate) {return MathHelper.floor(coordinate)>>4;}

    private static final class Budget {
        private long tick=Long.MIN_VALUE, started;
        private int used;
        void reset(long now) {
            if(tick==now)return;
            tick=now;
            started=System.nanoTime();
            used=0;
        }
        boolean take() {
            if(used>=CHUNKS_PER_TICK || used>0&&System.nanoTime()-started>=LOAD_BUDGET_NANOS)return false;
            used++;
            return true;
        }
    }
}
