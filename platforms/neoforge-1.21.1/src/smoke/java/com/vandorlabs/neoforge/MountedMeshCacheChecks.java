package com.vandorlabs.neoforge;

import com.google.common.cache.Cache;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Smoke-only resident cache inspection; no production counters or additional retained references. */
final class MountedMeshCacheChecks {
    static void report(Minecraft mc,BakedModel retainedOldModel)throws ReflectiveOperationException{
        if(!"true".equals(System.getenv("VANDOR_MOUNTED_CACHE_METRICS")))return;
        Field meshes=NativeMountedControlModel.class.getDeclaredField("meshes"),items=NativeMountedControlModel.class.getDeclaredField("items");meshes.setAccessible(true);items.setAccessible(true);
        Set<BakedModel> current=Collections.newSetFromMap(new IdentityHashMap<>());int listed=0,itemRoots=0;
        for(String id:new String[]{"push_button","rocker_switch","compact_power_lever","industrial_power_lever","small_power_lever","large_power_lever","thruster_lever","airliner_throttle","fighter_throttle","wall_slider"}){
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",id));for(var state:block.getStateDefinition().getPossibleStates()){var model=mc.getBlockRenderer().getBlockModel(state);if(!(model instanceof NativeMountedControlModel))throw new IllegalStateException("Cache fixture missing wrapper "+state);current.add(model);listed++;}current.add(mc.getItemRenderer().getModel(new ItemStack(block),null,null,0));itemRoots++;
        }
        int children=children(current,items);Stats now=stats(current,meshes);Set<BakedModel> retained=Collections.newSetFromMap(new IdentityHashMap<>());retained.addAll(current);if(retainedOldModel!=null)retained.add(retainedOldModel);children(retained,items);Stats all=stats(retained,meshes);
        System.out.println("[vandorlabs][mounted-cache] measured listed="+listed+" itemRoots="+itemRoots+" resolvedChildren="+children+" currentWrappers="+current.size()+" currentCaches="+now.caches+" currentEntries="+now.entries+" currentWeightedBytes="+now.bytes+" cachesIncludingOld="+all.caches+" entriesIncludingOld="+all.entries+" weightedBytesIncludingOld="+all.bytes+" keyBytes="+keyBytes());
        if("true".equals(System.getenv("VANDOR_MOUNTED_SHARED_CACHE"))){
            long budget=16L*1024*1024;if(now.caches!=1||now.bytes>budget)throw new IllegalStateException("Mounted cache is not bounded once per generation: "+now);
            if(all.caches!=(retainedOldModel!=null&&!current.contains(retainedOldModel)?2:1))throw new IllegalStateException("Reload reused or lost the old generation cache");
            if(all.bytes>budget*all.caches)throw new IllegalStateException("Retained generations exceed their combined budgets");
        }
        if(listed!=928||itemRoots!=10)throw new IllegalStateException("Incomplete mounted model cache population");
    }
    private static int children(Set<BakedModel> models,Field items)throws IllegalAccessException{int before=models.size();ArrayDeque<BakedModel> queue=new ArrayDeque<>(models);while(!queue.isEmpty()){var model=queue.removeFirst();if(model instanceof NativeMountedControlModel)for(Object child:((Cache<?,?>)items.get(model)).asMap().values())if(child instanceof BakedModel baked&&models.add(baked))queue.addLast(baked);}return models.size()-before;}
    private static int keyBytes(){String value=System.getenv("VANDOR_MOUNTED_MESH_KEY_BYTES");return value==null?160:Integer.parseInt(value);}
    private record Stats(int caches,long entries,long bytes){}
    private static Stats stats(Set<BakedModel> models,Field meshes)throws IllegalAccessException{Set<Cache<?,?>> unique=Collections.newSetFromMap(new IdentityHashMap<>());for(var model:models)if(model instanceof NativeMountedControlModel)unique.add((Cache<?,?>)meshes.get(model));long entries=0,bytes=0;for(var cache:unique){cache.cleanUp();for(Object value:cache.asMap().values()){long cost=keyBytes();for(Object quad:(List<?>)value){var q=(BakedQuad)quad;cost+=96L+q.getVertices().length*4L;}bytes+=cost;entries++;}}return new Stats(unique.size(),entries,bytes);}
    private MountedMeshCacheChecks(){}
}
