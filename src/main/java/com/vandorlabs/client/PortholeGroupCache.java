package com.vandorlabs.client;

import com.vandorlabs.blocks.LoadedPlaneConnections;
import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bounded aperture topology with conservative per-discovery dependency regions. */
final class PortholeGroupCache {
    static final int MAX_POSITIONS = 8192;
    private static final Map<World, Cache> WORLDS = new WeakHashMap<>();

    static final class Dependencies {
        boolean cacheable=true;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        void include(BlockPos pos){
            // Native diagonal corner tests consult only the owner and its four
            // horizontal neighbours. Include those even for rejected/unloaded
            // candidates; their later appearance can change the component.
            minX=Math.min(minX,lower(pos.getX()));maxX=Math.max(maxX,upper(pos.getX()));
            minY=Math.min(minY,pos.getY());maxY=Math.max(maxY,pos.getY());
            minZ=Math.min(minZ,lower(pos.getZ()));maxZ=Math.max(maxZ,upper(pos.getZ()));
        }
        private static int lower(int n){return n==Integer.MIN_VALUE?n:n-1;}
        private static int upper(int n){return n==Integer.MAX_VALUE?n:n+1;}
        void include(Dependencies other){minX=Math.min(minX,other.minX);minY=Math.min(minY,other.minY);minZ=Math.min(minZ,other.minZ);maxX=Math.max(maxX,other.maxX);maxY=Math.max(maxY,other.maxY);maxZ=Math.max(maxZ,other.maxZ);}
        boolean intersects(int x0,int y0,int z0,int x1,int y1,int z1){return minX<=Math.max(x0,x1)&&maxX>=Math.min(x0,x1)&&minY<=Math.max(y0,y1)&&maxY>=Math.min(y0,y1)&&minZ<=Math.max(z0,z1)&&maxZ>=Math.min(z0,z1);}
    }
    private static final class Component {
        final BlockPos[] positions;
        final Dependencies dependencies;
        Component(Map<BlockPos,TEAnimatedScreenSelector.PortholeGroup> groups,Dependencies dependencies){positions=groups.keySet().toArray(new BlockPos[0]);this.dependencies=dependencies;}
    }
    private static final class Member {
        final TEAnimatedScreenSelector.PortholeGroup group;
        final Component component;
        Member(TEAnimatedScreenSelector.PortholeGroup group,Component component){this.group=group;this.component=component;}
    }

    /** All operations, including discovery, hold PortholeGroupCache.class. */
    static final class Cache extends WorldRenderListener {
        final Map<BlockPos,Member> admitted=new HashMap<>();
        final Set<Component> components=new HashSet<>();
        Map<BlockPos,TEAnimatedScreenSelector.PortholeGroup> overflow=Collections.emptyMap();
        Component overflowComponent;
        Dependencies bounds=new Dependencies();
        long origin;
        int tileRevision=Integer.MIN_VALUE;
        WeakReference<Object> structure;

        void clear(){admitted.clear();components.clear();overflow=Collections.emptyMap();overflowComponent=null;bounds=new Dependencies();}

        void prepare(World world){
            int nextTileRevision=-1;Object snapshot=null;long nextOrigin=0;
            if(world instanceof VehicleWorld){
                VehicleWorld view=(VehicleWorld)world;
                nextTileRevision=view.tileRevision;snapshot=view.structure;nextOrigin=view.origin.toLong();
            }
            if(tileRevision!=nextTileRevision||origin!=nextOrigin||(structure==null?null:structure.get())!=snapshot){
                clear();tileRevision=nextTileRevision;origin=nextOrigin;
                structure=snapshot==null?null:new WeakReference<>(snapshot);
            }
        }

        TEAnimatedScreenSelector.PortholeGroup get(BlockPos pos){Member member=admitted.get(pos);return member==null?overflow.get(pos):member.group;}

        private void remove(Component component){
            for(BlockPos pos:component.positions){Member member=admitted.get(pos);if(member!=null&&member.component==component)admitted.remove(pos);}
        }

        void install(Map<BlockPos,TEAnimatedScreenSelector.PortholeGroup> groups,Dependencies dependencies){
            if(groups.size()>LoadedPlaneConnections.LIMIT)throw new IllegalArgumentException("Porthole discovery exceeded its scan limit");
            // A capped discovery from another root can overlap an earlier scan.
            // Replace whole overlapping discoveries, never retain orphan members.
            Set<Component> replaced=null;boolean overlapsOverflow=false;
            for(BlockPos pos:groups.keySet()){
                Member member=admitted.get(pos);
                if(member!=null){if(replaced==null)replaced=new HashSet<>();replaced.add(member.component);}
                overlapsOverflow|=overflow.containsKey(pos);
            }
            if(replaced!=null)for(Component component:replaced){remove(component);components.remove(component);}
            if(overlapsOverflow){overflow=Collections.emptyMap();overflowComponent=null;}
            Component component=new Component(groups,dependencies);bounds.include(dependencies);
            // Keep the admitted set stable when an independent window exceeds
            // the budget; retain that entire latest component in one overflow.
            if(admitted.size()+groups.size()<=MAX_POSITIONS){
                components.add(component);
                for(Map.Entry<BlockPos,TEAnimatedScreenSelector.PortholeGroup> entry:groups.entrySet())admitted.put(entry.getKey(),new Member(entry.getValue(),component));
            }else{overflow=groups;overflowComponent=component;}
        }

        void changed(int x0,int y0,int z0,int x1,int y1,int z1){
            if(!bounds.intersects(x0,y0,z0,x1,y1,z1))return;
            Iterator<Component> iterator=components.iterator();
            while(iterator.hasNext()){
                Component component=iterator.next();
                if(component.dependencies.intersects(x0,y0,z0,x1,y1,z1)){remove(component);iterator.remove();}
            }
            if(overflowComponent!=null&&overflowComponent.dependencies.intersects(x0,y0,z0,x1,y1,z1)){overflow=Collections.emptyMap();overflowComponent=null;}
            // Eviction may leave a wider union. It costs a conservative scan,
            // never an omitted dependency; empty caches can start afresh.
            if(admitted.isEmpty()&&overflow.isEmpty())bounds=new Dependencies();
        }
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){
            synchronized(PortholeGroupCache.class){changed(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());}
        }
        @Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){synchronized(PortholeGroupCache.class){changed(x0,y0,z0,x1,y1,z1);}}
    }

    static synchronized Cache cache(World world){
        Cache cache=WORLDS.get(world);
        if(cache==null){cache=new Cache();WORLDS.put(world,cache);world.addEventListener(cache);}
        cache.prepare(world);return cache;
    }
    static synchronized void invalidate(World world){Cache cache=WORLDS.get(world);if(cache!=null)cache.clear();}
    static synchronized void invalidate(World world,int x0,int y0,int z0,int x1,int y1,int z1){Cache cache=WORLDS.get(world);if(cache!=null)cache.changed(x0,y0,z0,x1,y1,z1);}
    static synchronized void unload(World world){Cache cache=WORLDS.remove(world);if(cache!=null)world.removeEventListener(cache);}
    static synchronized void clear(){for(Map.Entry<World,Cache> entry:WORLDS.entrySet())entry.getKey().removeEventListener(entry.getValue());WORLDS.clear();}
    static synchronized int entries(World world){Cache cache=WORLDS.get(world);return cache==null?0:cache.admitted.size()+cache.overflow.size();}
    static synchronized boolean contains(World world){return WORLDS.containsKey(world);}
    private PortholeGroupCache(){}
}
