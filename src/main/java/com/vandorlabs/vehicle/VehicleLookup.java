package com.vandorlabs.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Craft extents can cross many sections; vanilla entity queries only index their origin. */
public final class VehicleLookup {
    private VehicleLookup(){}
    private static final Map<World,Index> worlds=Collections.synchronizedMap(new WeakHashMap<World,Index>());
    private static final class Entry {
        final AxisAlignedBB bounds;final Set<Long> buckets;
        Entry(AxisAlignedBB bounds,Set<Long> buckets){this.bounds=bounds;this.buckets=buckets;}
    }
    private static final class Index {
        final Map<EntityGroundVehicle,Entry> entries=new IdentityHashMap<>();
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Set<EntityGroundVehicle>> buckets=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static Set<Long> keys(AxisAlignedBB bounds) {
        Set<Long> keys=new HashSet<>();
        for(int x=MathHelper.floor(bounds.minX)>>4;x<=MathHelper.floor(bounds.maxX)>>4;x++)
            for(int z=MathHelper.floor(bounds.minZ)>>4;z<=MathHelper.floor(bounds.maxZ)>>4;z++)keys.add(key(x,z));
        return keys;
    }
    public static void update(EntityGroundVehicle craft) {
        if(!craft.isAddedToWorld() || craft.structure==null || craft.isDead)return;
        Index index=worlds.computeIfAbsent(craft.world,w->new Index());
        AxisAlignedBB bounds=craft.getRenderBoundingBox().union(craft.getEntityBoundingBox());
        Entry before=index.entries.get(craft);
        if(before!=null && before.bounds.equals(bounds))return;
        Set<Long> next=keys(bounds);
        if(before!=null && before.buckets.equals(next)){index.entries.put(craft,new Entry(bounds,next));return;}
        remove(craft);index=worlds.computeIfAbsent(craft.world,w->new Index());
        index.entries.put(craft,new Entry(bounds,next));
        for(long bucket:next){Set<EntityGroundVehicle> members=index.buckets.get(bucket);if(members==null){members=Collections.newSetFromMap(new IdentityHashMap<>());index.buckets.put(bucket,members);}members.add(craft);}
    }
    public static void remove(EntityGroundVehicle craft) {
        Index index=worlds.get(craft.world);if(index==null)return;
        Entry entry=index.entries.remove(craft);if(entry==null)return;
        for(long bucket:entry.buckets){Set<EntityGroundVehicle> values=index.buckets.get(bucket);values.remove(craft);if(values.isEmpty())index.buckets.remove(bucket);}
    }
    public static void unload(World world){worlds.remove(world);}
    public static List<EntityGroundVehicle> loaded(World world) {
        Index index=worlds.get(world);return index==null?Collections.emptyList():new ArrayList<>(index.entries.keySet());
    }
    public static List<EntityGroundVehicle> near(World world,AxisAlignedBB bounds){return near(world,bounds,null);}
    /** Exclude the querying craft before allocating candidates; a lone craft
     * cannot collide with itself. Bucket lookups use unboxed section keys. */
    public static List<EntityGroundVehicle> near(World world,AxisAlignedBB bounds,EntityGroundVehicle excluded){
        Index index=worlds.get(world);if(index==null||index.entries.isEmpty()||excluded!=null&&index.entries.size()==1&&index.entries.containsKey(excluded))return Collections.emptyList();
        Set<EntityGroundVehicle> found=null;
        for(int x=MathHelper.floor(bounds.minX)>>4;x<=MathHelper.floor(bounds.maxX)>>4;x++)
            for(int z=MathHelper.floor(bounds.minZ)>>4;z<=MathHelper.floor(bounds.maxZ)>>4;z++){
                Set<EntityGroundVehicle> candidates=index.buckets.get(key(x,z));if(candidates==null)continue;
                for(EntityGroundVehicle craft:candidates){Entry entry=index.entries.get(craft);if(craft!=excluded&&!craft.isDead&&entry!=null&&entry.bounds.intersects(bounds)){if(found==null)found=Collections.newSetFromMap(new IdentityHashMap<>());found.add(craft);}}
            }
        return found==null?Collections.emptyList():new ArrayList<>(found);
    }
    public static EntityGroundVehicle pointed(EntityPlayer player) {
        Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(6));
        RayTraceResult terrain=player.world.rayTraceBlocks(start,end,false,true,false);
        double nearest=terrain==null?36:start.squareDistanceTo(terrain.hitVec);
        EntityGroundVehicle selected=null;
        for(EntityGroundVehicle craft:near(player.world,new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(.001))) {
            Vec3d a=craft.toLocal(start),b=craft.toLocal(end);
            for(VehicleStructure.Cell cell:craft.structure.cells) {
                RayTraceResult hit=new AxisAlignedBB(cell.pos).calculateIntercept(a,b);
                if(hit==null)continue;
                double distance=start.squareDistanceTo(craft.toWorld(hit.hitVec));
                if(distance<=nearest){nearest=distance;selected=craft;}
            }
        }
        return selected;
    }
}
