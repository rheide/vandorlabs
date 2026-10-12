package com.vandorlabs.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Craft extents can cross many sections; vanilla entity queries only index their origin. */
public final class VehicleLookup {
    private VehicleLookup(){}
    private static final Map<World,Index> worlds=new WeakHashMap<>();
    private static final class Entry {
        final AxisAlignedBB bounds;final Set<Long> buckets;
        Entry(AxisAlignedBB bounds,Set<Long> buckets){this.bounds=bounds;this.buckets=buckets;}
    }
    private static final class Index {
        final Map<EntityGroundVehicle,Entry> entries=new IdentityHashMap<>();
        final Map<Long,Set<EntityGroundVehicle>> buckets=new HashMap<>();
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
        for(long bucket:next)index.buckets.computeIfAbsent(bucket,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(craft);
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
    public static List<EntityGroundVehicle> near(World world,AxisAlignedBB bounds) {
        Index index=worlds.get(world);if(index==null)return Collections.emptyList();
        Set<EntityGroundVehicle> found=Collections.newSetFromMap(new IdentityHashMap<>());
        for(long bucket:keys(bounds)) {
            Set<EntityGroundVehicle> candidates=index.buckets.get(bucket);if(candidates==null)continue;
            for(EntityGroundVehicle craft:candidates)if(!craft.isDead && index.entries.get(craft).bounds.intersects(bounds))found.add(craft);
        }
        return new ArrayList<>(found);
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
