package com.vandorlabs.client;

import com.vandorlabs.blocks.LoadedPlaneConnections;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/** Connected light artwork is topology, not animation: retain it until its inputs change. */
public final class LightGroupCache {
    static final int MAX_POSITIONS = 8192;
    private static final Map<World, Cache> WORLDS = new WeakHashMap<>();

    static final class Dependencies {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        void include(BlockPos pos) {
            minX=Math.min(minX,pos.getX());minY=Math.min(minY,pos.getY());minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX());maxY=Math.max(maxY,pos.getY());maxZ=Math.max(maxZ,pos.getZ());
        }
        void include(Dependencies other) {
            minX=Math.min(minX,other.minX);minY=Math.min(minY,other.minY);minZ=Math.min(minZ,other.minZ);
            maxX=Math.max(maxX,other.maxX);maxY=Math.max(maxY,other.maxY);maxZ=Math.max(maxZ,other.maxZ);
        }
        boolean intersects(int x0,int y0,int z0,int x1,int y1,int z1) {
            return minX<=Math.max(x0,x1)&&maxX>=Math.min(x0,x1)
                    &&minY<=Math.max(y0,y1)&&maxY>=Math.min(y0,y1)
                    &&minZ<=Math.max(z0,z1)&&maxZ>=Math.min(z0,z1);
        }
    }
    private static final class Component {
        final BlockPos[] positions;
        final Dependencies dependencies;
        Component(Map<BlockPos,TEAnimatedScreenSelector.LightGroup> groups,Dependencies dependencies) {
            positions=groups.keySet().toArray(new BlockPos[0]);this.dependencies=dependencies;
        }
    }
    private static final class Member {
        final TEAnimatedScreenSelector.LightGroup group;
        final Component component;
        Member(TEAnimatedScreenSelector.LightGroup group,Component component) { this.group=group;this.component=component; }
    }
    private static final class Faces {
        final WeakReference<TileEntityAnimatedScreenSelector> owner;
        final EnumFacing facing;
        final int mask;
        Faces(TileEntityAnimatedScreenSelector tile,EnumFacing facing,int mask) {
            owner=new WeakReference<>(tile);this.facing=facing;this.mask=mask;
        }
    }
    private static final class Cache extends WorldRenderListener {
        final Map<BlockPos,Member> members=new HashMap<>();
        final Set<Component> components=new HashSet<>();
        Map<BlockPos,TEAnimatedScreenSelector.LightGroup> overflow=Collections.emptyMap();
        Component overflowComponent;
        Dependencies bounds=new Dependencies();
        final Map<BlockPos,Faces> faces=new HashMap<>();
        final BlockPos.MutableBlockPos lookup=new BlockPos.MutableBlockPos();
        int tileRevision=Integer.MIN_VALUE;
        WeakReference<Object> structure;

        void clear() {
            members.clear();components.clear();overflow=Collections.emptyMap();overflowComponent=null;bounds=new Dependencies();faces.clear();
        }
        void prepare(World world) {
            int revision=-1;Object snapshot=null;
            if(world instanceof VehicleWorld) { VehicleWorld vehicle=(VehicleWorld)world;revision=vehicle.tileRevision;snapshot=vehicle.structure; }
            if(tileRevision!=revision||(structure==null?null:structure.get())!=snapshot) {
                clear();tileRevision=revision;structure=snapshot==null?null:new WeakReference<>(snapshot);
            }
        }
        TEAnimatedScreenSelector.LightGroup get(BlockPos pos) {
            Member member=members.get(pos);return member==null?overflow.get(pos):member.group;
        }
        private void remove(Component component) {
            for(BlockPos pos:component.positions) { Member member=members.get(pos);if(member!=null&&member.component==component)members.remove(pos); }
        }
        void install(Map<BlockPos,TEAnimatedScreenSelector.LightGroup> groups,Dependencies dependencies) {
            if(groups.size()>LoadedPlaneConnections.LIMIT)throw new IllegalArgumentException("Light discovery exceeded its scan limit");
            // A capped traversal can overlap a previous discovery. Retire that
            // entire component so no surviving member retains a stale layout.
            Set<Component> replaced=null;boolean overlapsOverflow=false;
            for(BlockPos pos:groups.keySet()) {
                Member old=members.get(pos);
                if(old!=null) { if(replaced==null)replaced=new HashSet<>();replaced.add(old.component); }
                overlapsOverflow|=overflow.containsKey(pos);
            }
            if(replaced!=null)for(Component component:replaced) { remove(component);components.remove(component); }
            if(overlapsOverflow) { overflow=Collections.emptyMap();overflowComponent=null; }
            Component component=new Component(groups,dependencies);bounds.include(dependencies);
            // Retain a stable admitted set plus one whole overflow component.
            // A scene above the budget must not evict every earlier panel per draw.
            if(members.size()+groups.size()<=MAX_POSITIONS) {
                components.add(component);
                for(Map.Entry<BlockPos,TEAnimatedScreenSelector.LightGroup> entry:groups.entrySet())
                    members.put(entry.getKey(),new Member(entry.getValue(),component));
            } else { overflow=groups;overflowComponent=component; }
        }
        void changed(int x0,int y0,int z0,int x1,int y1,int z1) {
            facesChanged(x0,y0,z0,x1,y1,z1);
            if(!bounds.intersects(x0,y0,z0,x1,y1,z1))return;
            Iterator<Component> iterator=components.iterator();
            while(iterator.hasNext()) {
                Component component=iterator.next();
                if(component.dependencies.intersects(x0,y0,z0,x1,y1,z1)) { remove(component);iterator.remove(); }
            }
            if(overflowComponent!=null&&overflowComponent.dependencies.intersects(x0,y0,z0,x1,y1,z1)) {
                overflow=Collections.emptyMap();overflowComponent=null;
            }
            if(members.isEmpty()&&overflow.isEmpty())bounds=new Dependencies();
        }
        private void facesChanged(int x0,int y0,int z0,int x1,int y1,int z1) {
            if(faces.isEmpty())return;
            if(x0==x1&&y0==y1&&z0==z1) {
                faces.remove(lookup.setPos(x0,y0,z0));
                // Visibility reads exactly six adjacent cells. A local edit
                // needs no component scan or temporary BlockPos allocation.
                if(x0>Integer.MIN_VALUE)faces.remove(lookup.setPos(x0-1,y0,z0));
                if(x0<Integer.MAX_VALUE)faces.remove(lookup.setPos(x0+1,y0,z0));
                if(y0>Integer.MIN_VALUE)faces.remove(lookup.setPos(x0,y0-1,z0));
                if(y0<Integer.MAX_VALUE)faces.remove(lookup.setPos(x0,y0+1,z0));
                if(z0>Integer.MIN_VALUE)faces.remove(lookup.setPos(x0,y0,z0-1));
                if(z0<Integer.MAX_VALUE)faces.remove(lookup.setPos(x0,y0,z0+1));
            } else {
                long minX=(long)Math.min(x0,x1)-1,maxX=(long)Math.max(x0,x1)+1;
                long minY=(long)Math.min(y0,y1)-1,maxY=(long)Math.max(y0,y1)+1;
                long minZ=(long)Math.min(z0,z1)-1,maxZ=(long)Math.max(z0,z1)+1;
                for(Iterator<BlockPos> it=faces.keySet().iterator();it.hasNext();) {
                    BlockPos pos=it.next();
                    if(pos.getX()>=minX&&pos.getX()<=maxX&&pos.getY()>=minY&&pos.getY()<=maxY&&pos.getZ()>=minZ&&pos.getZ()<=maxZ)it.remove();
                }
            }
        }
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags) {
            synchronized(LightGroupCache.class) { changed(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()); }
        }
        @Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1) {
            synchronized(LightGroupCache.class) { changed(x0,y0,z0,x1,y1,z1); }
        }
    }

    static synchronized TEAnimatedScreenSelector.LightGroup get(TileEntityProgrammableLight tile,IBlockState state) {
        Cache cache=cache(tile.getWorld());
        TEAnimatedScreenSelector.LightGroup group=cache.get(tile.getPos());
        if(group!=null)return group;
        Dependencies dependencies=new Dependencies();
        Map<BlockPos,TEAnimatedScreenSelector.LightGroup> groups=TEAnimatedScreenSelector.discoverLightGroups(tile,state,dependencies::include);
        cache.install(groups,dependencies);return groups.get(tile.getPos());
    }
    private static Cache cache(World world) {
        Cache cache=WORLDS.get(world);
        if(cache==null) { cache=new Cache();WORLDS.put(world,cache);world.addEventListener(cache); }
        cache.prepare(world);return cache;
    }
    static synchronized int visibleFaces(TileEntityAnimatedScreenSelector tile,EnumFacing facing) {
        Cache cache=cache(tile.getWorld());Faces found=cache.faces.get(tile.getPos());
        if(found!=null&&found.owner.get()==tile&&found.facing==facing)return found.mask;
        int mask=TEAnimatedScreenSelector.discoverLightVisibleFaces(tile,facing);
        if(found!=null||cache.faces.size()<MAX_POSITIONS)cache.faces.put(tile.getPos().toImmutable(),new Faces(tile,facing,mask));
        return mask;
    }
    /** Local-only lifecycle/settings notification, including read-only carried worlds. */
    public static void changed(TileEntityProgrammableLight tile) {
        World world=tile.getWorld(),physical=world;
        while(physical instanceof VehicleWorld)physical=((VehicleWorld)physical).parent;
        if(physical==null||!physical.isRemote)return;
        BlockPos pos=tile.getPos();invalidate(world,pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
    }
    static synchronized void invalidate(World world,int x0,int y0,int z0,int x1,int y1,int z1) {
        Cache cache=WORLDS.get(world);if(cache!=null)cache.changed(x0,y0,z0,x1,y1,z1);
    }
    static synchronized void unload(World world) { Cache cache=WORLDS.remove(world);if(cache!=null)world.removeEventListener(cache); }
    static synchronized void clear() { for(Map.Entry<World,Cache> entry:WORLDS.entrySet())entry.getKey().removeEventListener(entry.getValue());WORLDS.clear(); }
    static synchronized int entries(World world) { Cache cache=WORLDS.get(world);return cache==null?0:cache.members.size()+cache.overflow.size(); }
    static synchronized int faceEntries(World world) { Cache cache=WORLDS.get(world);return cache==null?0:cache.faces.size(); }
    static synchronized boolean contains(World world) { return WORLDS.containsKey(world); }
    private LightGroupCache() { }
}
