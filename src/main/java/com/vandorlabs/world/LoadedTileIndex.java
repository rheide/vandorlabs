package com.vandorlabs.world;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Loaded tile positions in eight-block 3D buckets. Registration never reads neighbours
 * or loads chunks; candidates are identity-checked on the calling world thread. */
public final class LoadedTileIndex<T extends TileEntity> {
    private static final List<LoadedTileIndex<?>> ALL=new ArrayList<>();
    private final Class<T> type;
    private final Map<World,Entries<T>> worlds=new IdentityHashMap<>();
    private static final class Entries<T> {
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Set<T>> buckets=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        final Map<T,Long> keys=new IdentityHashMap<>();
    }
    public LoadedTileIndex(Class<T> type){this.type=type;synchronized(ALL){ALL.add(this);}}
    private static long key(int x,int y,int z){return SpatialBuckets.key(x,y,z);}
    public synchronized void add(T tile){
        World world=tile.getWorld();if(world==null||world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        Entries<T> entries=worlds.computeIfAbsent(world,w->new Entries<>());remove(entries,tile);
        BlockPos pos=tile.getPos();long key=key(pos.getX()>>3,pos.getY()>>3,pos.getZ()>>3);
        entries.keys.put(tile,key);Set<T> bucket=entries.buckets.get(key);if(bucket==null){bucket=Collections.newSetFromMap(new IdentityHashMap<>());entries.buckets.put(key,bucket);}bucket.add(tile);
    }
    private void remove(Entries<T> e,T tile){Long old=e.keys.remove(tile);if(old==null)return;Set<T> bucket=e.buckets.get(old.longValue());bucket.remove(tile);if(bucket.isEmpty())e.buckets.remove(old.longValue());}
    public synchronized void remove(T tile){Entries<T> entries=worlds.get(tile.getWorld());if(entries!=null){remove(entries,tile);if(entries.keys.isEmpty())worlds.remove(tile.getWorld());}}
    public List<T> query(World world,AxisAlignedBB bounds){return query(world,bounds,null);}
    /** Apply conservative local bounds before identity checks and loaded-chunk reads. */
    public synchronized List<T> query(World world,AxisAlignedBB bounds,java.util.function.Predicate<T> filter){
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld){List<T> out=new ArrayList<>();for(TileEntity tile:((com.vandorlabs.vehicle.VehicleWorld)world).tiles.values())if(type.isInstance(tile)&&inside(tile,bounds)&&(filter==null||filter.test(type.cast(tile))))out.add(type.cast(tile));return out;}
        Entries<T> entries=worlds.get(world);if(entries==null)return Collections.emptyList();
        int x0=MathHelper.floor(bounds.minX)>>3,x1=MathHelper.floor(bounds.maxX)>>3,y0=MathHelper.floor(bounds.minY)>>3,y1=MathHelper.floor(bounds.maxY)>>3,z0=MathHelper.floor(bounds.minZ)>>3,z1=MathHelper.floor(bounds.maxZ)>>3;
        List<T> out=null;
        // Long selection rays retain the exact same search without enumerating an
        // unbounded number of empty buckets. The loaded index is always finite.
        if(SpatialBuckets.excessive(x0,y0,z0,x1,y1,z1)){for(T tile:entries.keys.keySet())if(valid(world,tile,bounds,filter)){if(out==null)out=new ArrayList<>();out.add(tile);}}
        else for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){
            Set<T> bucket=entries.buckets.get(key(x,y,z));if(bucket==null)continue;
            for(T tile:bucket)if(valid(world,tile,bounds,filter)){if(out==null)out=new ArrayList<>();out.add(tile);}
        }
        return out==null?Collections.emptyList():out;
    }
    private static boolean inside(TileEntity tile,AxisAlignedBB b){BlockPos p=tile.getPos();return SpatialBuckets.intersectsTile(p.getX(),p.getY(),p.getZ(),b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static <T extends TileEntity> boolean valid(World world,T tile,AxisAlignedBB b,java.util.function.Predicate<T> filter){return !tile.isInvalid()&&inside(tile,b)&&(filter==null||filter.test(tile))&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile;}
    private synchronized void unload(World world){worlds.remove(world);}
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
    public static final class Lifecycle {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){synchronized(ALL){for(LoadedTileIndex<?> index:ALL)index.unload(event.getWorld());}}
    }
}
