package com.vandorlabs.neoforge.world;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.world.SpatialBuckets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Loaded owner positions only. Registration is local; queries recheck installed identity on the world thread. */
public final class NativeLoadedTileIndex<T extends BlockEntity> {
    private static final Set<NativeLoadedTileIndex<?>> ALL=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Object[] EMPTY=new Object[0];
    private static final class Bucket<T> {
        final Set<T> tiles=Collections.newSetFromMap(new IdentityHashMap<>());
        Object[] snapshot;
        Object[] snapshot(){if(snapshot==null)snapshot=tiles.toArray();return snapshot;}
    }
    private static final class WorldEntries<T> {final Long2ObjectOpenHashMap<Bucket<T>> buckets=new Long2ObjectOpenHashMap<>();}
    private record Member(Level level,long key,long generation){}
    private final Class<T> type;
    private final Map<Level,WorldEntries<T>> worlds=new IdentityHashMap<>();
    private final Map<T,Member> members=new IdentityHashMap<>();
    private Object[] allSnapshot=EMPTY;
    private long generation;
    public NativeLoadedTileIndex(Class<T> type){this.type=Objects.requireNonNull(type);synchronized(ALL){ALL.add(this);}}
    public synchronized void add(T tile){
        Objects.requireNonNull(tile);if(!type.isInstance(tile))throw new IllegalArgumentException("Wrong indexed tile type");var level=tile.getLevel();
        if(!NativeFluidWorld.open(level)){remove(tile);return;}remove(tile);
        BlockPos pos=tile.getBlockPos();long key=SpatialBuckets.key(pos.getX()>>3,pos.getY()>>3,pos.getZ()>>3);
        var entries=worlds.computeIfAbsent(level,ignored->new WorldEntries<>());var bucket=entries.buckets.get(key);
        if(bucket==null){bucket=new Bucket<>();entries.buckets.put(key,bucket);}bucket.tiles.add(tile);bucket.snapshot=null;members.put(tile,new Member(level,key,++generation));allSnapshot=null;
    }
    /** Identity membership is retained separately from mutable tile level/position, including replacement. */
    public synchronized void remove(T tile){
        var member=members.remove(tile);if(member==null)return;var entries=worlds.get(member.level);if(entries!=null){var bucket=entries.buckets.get(member.key);if(bucket!=null){bucket.tiles.remove(tile);bucket.snapshot=null;if(bucket.tiles.isEmpty())entries.buckets.remove(member.key);}if(entries.buckets.isEmpty())worlds.remove(member.level);}allSnapshot=null;
    }
    private static boolean finite(AABB bounds){return bounds!=null&&Double.isFinite(bounds.minX)&&Double.isFinite(bounds.minY)&&Double.isFinite(bounds.minZ)&&Double.isFinite(bounds.maxX)&&Double.isFinite(bounds.maxY)&&Double.isFinite(bounds.maxZ);}
    private static int coordinate(double value){double bucket=Math.floor(value/8D);return bucket<Integer.MIN_VALUE?Integer.MIN_VALUE:bucket>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)bucket;}
    /** Settled queries reuse bucket snapshots; callbacks may remove entries without invalidating traversal. */
    public synchronized int visit(Level level,AABB bounds,Predicate<? super T> filter,Consumer<? super T> visitor){
        if(!NativeFluidWorld.open(level)||!finite(bounds))return 0;Objects.requireNonNull(visitor);var entries=worlds.get(level);if(entries==null)return 0;long cutoff=generation;
        int x0=coordinate(bounds.minX),x1=coordinate(bounds.maxX),y0=coordinate(bounds.minY),y1=coordinate(bounds.maxY),z0=coordinate(bounds.minZ),z1=coordinate(bounds.maxZ),count=0;
        if(SpatialBuckets.excessive(x0,y0,z0,x1,y1,z1)){if(allSnapshot==null)allSnapshot=members.keySet().toArray();return deliver(allSnapshot,level,bounds,filter,visitor,cutoff);}
        for(long x=x0;x<=x1;x++)for(long y=y0;y<=y1;y++)for(long z=z0;z<=z1;z++){var bucket=entries.buckets.get(SpatialBuckets.key((int)x,(int)y,(int)z));if(bucket!=null){count+=deliver(bucket.snapshot(),level,bounds,filter,visitor,cutoff);if(worlds.get(level)!=entries||!NativeFluidWorld.open(level))return count;}}return count;
    }
    private int deliver(Object[] candidates,Level level,AABB bounds,Predicate<? super T> filter,Consumer<? super T> visitor,long cutoff){
        int count=0;for(Object candidate:candidates){if(!NativeFluidWorld.open(level))return count;T tile=type.cast(candidate);var member=members.get(tile);if(member==null||member.level!=level||member.generation>cutoff)continue;
            if(tile.isRemoved()||tile.getLevel()!=level){remove(tile);continue;}var pos=tile.getBlockPos();
            if(!SpatialBuckets.intersectsTile(pos.getX(),pos.getY(),pos.getZ(),bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ))continue;
            if(filter!=null&&!filter.test(tile))continue;if(members.get(tile)!=member||!NativeFluidWorld.open(level))continue;// Inspect only the installed map: queries must never load chunks or instantiate tiles.
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)continue;
            if(chunk.getBlockEntities().get(pos)!=tile){remove(tile);continue;}if(members.get(tile)!=member||!NativeFluidWorld.open(level))continue;visitor.accept(tile);count++;
        }return count;
    }
    /** Convenience collector; render/interaction hot paths should use visit with an owned reusable visitor. */
    public synchronized List<T> query(Level level,AABB bounds){if(!NativeFluidWorld.open(level)||!finite(bounds)||!worlds.containsKey(level))return List.of();var result=new ArrayList<T>();visit(level,bounds,null,result::add);return result.isEmpty()?List.of():result;}
    /** World-free spatial preflight: no expanded box, tile reads, chunks or visitor are needed. */
    public synchronized boolean hasNearbyBuckets(Level level,AABB bounds,double padding){
        if(!NativeFluidWorld.open(level)||!finite(bounds)||!Double.isFinite(padding)||padding<0)return false;var entries=worlds.get(level);if(entries==null)return false;
        int x0=coordinate(bounds.minX-padding),x1=coordinate(bounds.maxX+padding),y0=coordinate(bounds.minY-padding),y1=coordinate(bounds.maxY+padding),z0=coordinate(bounds.minZ-padding),z1=coordinate(bounds.maxZ+padding);
        if(SpatialBuckets.excessive(x0,y0,z0,x1,y1,z1))return !entries.buckets.isEmpty();
        for(long x=x0;x<=x1;x++)for(long y=y0;y<=y1;y++)for(long z=z0;z<=z1;z++)if(entries.buckets.containsKey(SpatialBuckets.key((int)x,(int)y,(int)z)))return true;return false;
    }
    public synchronized boolean hasMembers(Level level){return NativeFluidWorld.open(level)&&worlds.containsKey(level);}
    public synchronized int size(){return members.size();}
    private synchronized void forget(Level level){var entries=worlds.remove(level);if(entries==null)return;for(var bucket:entries.buckets.values())for(T tile:bucket.tiles){var member=members.get(tile);if(member!=null&&member.level==level)members.remove(tile);}allSnapshot=null;}
    @EventBusSubscriber(modid="vandorlabs")
    public static final class Lifecycle {
        @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof Level level){List<NativeLoadedTileIndex<?>> indexes;synchronized(ALL){indexes=new ArrayList<>(ALL);}for(var index:indexes)index.forget(level);}}
    }
}
