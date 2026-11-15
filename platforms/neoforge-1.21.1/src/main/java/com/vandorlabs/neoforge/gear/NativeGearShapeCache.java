package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.geometry.ExactVoxelShapes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Exact unions per immutable pose, with weak owners and bounded retained voxel storage. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeGearShapeCache {
    private static final int MAX_BYTES=16*1024*1024,MAX_ENTRY=1024*1024,MAX_OWNERS=1024;
    private static long builds;
    public static synchronized long buildCount(){return builds;}
    private static final WeakHashMap<NativeGearEntity,Entry> CACHE=new WeakHashMap<>();
    private static final class Entry {
        final List<AABB> worldBoxes;
        final Map<BlockPos,ExactVoxelShapes.Built> cells=new HashMap<>();
        ExactVoxelShapes.Built selection;
        int bytes=256;
        Entry(List<AABB> boxes){worldBoxes=boxes;}
    }
    private static int retained(){int sum=0;for(var entry:CACHE.values())sum+=entry.bytes;return sum;}
    public static synchronized VoxelShape shape(NativeGearEntity root,BlockPos pos,boolean collision){
        var boxes=root.collisionBoxes();var entry=CACHE.get(root);if(entry==null||entry.worldBoxes!=boxes){entry=new Entry(boxes);CACHE.put(root,entry);}
        BlockPos offset=pos.subtract(root.getBlockPos());var cached=collision?entry.cells.get(offset):entry.selection;
        if(cached!=null)return collision?cached.shape():cached.shape().move(-offset.getX(),-offset.getY(),-offset.getZ());
        var input=new ArrayList<AABB>();var cell=new AABB(pos);
        for(var box:boxes){if(collision&&!box.intersects(cell))continue;var local=(collision?box.intersect(cell):box).move(collision?-pos.getX():-root.getBlockPos().getX(),collision?-pos.getY():-root.getBlockPos().getY(),collision?-pos.getZ():-root.getBlockPos().getZ());if(local.getXsize()>0&&local.getYsize()>0&&local.getZsize()>0)input.add(local);}
        builds++;var built=ExactVoxelShapes.build(input);int weight=built.retainedBytes()+64;
        if(entry.bytes+weight<=MAX_ENTRY&&(!collision||entry.cells.size()<64)){
            if(collision)entry.cells.put(offset.immutable(),built);else entry.selection=built;entry.bytes+=weight;
            var iterator=CACHE.entrySet().iterator();int bytes=retained();while((CACHE.size()>MAX_OWNERS||bytes>MAX_BYTES)&&iterator.hasNext()){var old=iterator.next();if(old.getKey()==root)continue;bytes-=old.getValue().bytes;iterator.remove();}
        }
        return collision?built.shape():built.shape().move(-offset.getX(),-offset.getY(),-offset.getZ());
    }
    public static synchronized int retainedBytes(){return retained();}
    @net.neoforged.bus.api.SubscribeEvent public static synchronized void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){CACHE.clear();}
    private NativeGearShapeCache(){}
}
