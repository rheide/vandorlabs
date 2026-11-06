package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Loaded-leaf discovery, exact endpoint rays and bounded outside-cell collision publications. */
public final class NativeTrapdoorTargets {
    private static final NativeLoadedTileIndex<NativeTrapdoorEntity> INDEX=new NativeLoadedTileIndex<>(NativeTrapdoorEntity.class);
    private static final ThreadLocal<Finder> FINDERS=ThreadLocal.withInitial(Finder::new);
    private static final int BUDGET=4*1024*1024;
    private static final LinkedHashMap<Key,Collision> COLLISIONS=new LinkedHashMap<>(32,.75F,true);
    private static int retained;
    public record Target(BlockPos owner,BlockHitResult hit) { }
    private record Key(NativeTrapdoorGeometry.Snapshot geometry,BlockPos owner,boolean broad) { }
    private record Collision(AABB[] boxes,VoxelShape[] shapes,int bytes) { }
    private record Source(NativeTrapdoorEntity tile,NativeTrapdoorGeometry.Snapshot geometry,Collision collision) { }
    private NativeTrapdoorTargets() { }
    public static void add(NativeTrapdoorEntity tile){if(tile.inert()||tile.geometry()==null)INDEX.remove(tile);else INDEX.add(tile);}
    public static void remove(NativeTrapdoorEntity tile){INDEX.remove(tile);}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    public static Target trace(Level level,Vec3 start,Vec3 end){
        if(!NativeFluidWorld.open(level)||!finite(start)||!finite(end)||start.distanceToSqr(end)>64*64||!INDEX.hasMembers(level))return null;
        var finder=FINDERS.get();if(finder.active)finder=new Finder();finder.active=true;finder.start=start;finder.end=end;finder.distance=Double.POSITIVE_INFINITY;
        try{INDEX.visit(level,new AABB(start,end).inflate(2,1,2),null,finder);return finder.best;}
        finally{finder.active=false;finder.start=finder.end=null;finder.best=null;}
    }
    private static final class Finder implements Consumer<NativeTrapdoorEntity> {
        boolean active;Vec3 start,end;double distance;Target best;final double[] hit=new double[4];
        public void accept(NativeTrapdoorEntity tile){
            var c=tile.settings().choices();if(tile.inert())return;
            boolean offset=tile.diagonal()||c.sliding||c.cover||c.overSurface;
            var geometry=tile.geometry();if(geometry==null||!geometry.trace(offset,tile.getBlockPos().getX(),tile.getBlockPos().getY(),tile.getBlockPos().getZ(),start.x,start.y,start.z,end.x,end.y,end.z,hit))return;
            double dx=hit[0]-start.x,dy=hit[1]-start.y,dz=hit[2]-start.z,d=dx*dx+dy*dy+dz*dz;
            if(d<distance){distance=d;best=new Target(tile.getBlockPos(),new BlockHitResult(new Vec3(hit[0],hit[1],hit[2]),Direction.from3DDataValue((int)hit[3]),tile.getBlockPos(),false));}
        }
    }
    private static boolean inside(AABB box){return box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1;}
    private static synchronized Collision collision(NativeTrapdoorEntity tile,NativeTrapdoorGeometry.Snapshot geometry){
        var c=tile.settings().choices();boolean broad=c.sliding||c.cover||c.overSurface;
        var key=new Key(geometry,tile.getBlockPos(),broad);var found=COLLISIONS.get(key);if(found!=null)return found;
        var boxes=new ArrayList<AABB>();
        // The released offset event adds the entire bounding box for these modes, also for diagonal slides.
        // Other diagonal endpoints retain their tight collision slices outside the clipped owner cell.
        if(broad){if(tile.diagonal()||!inside(geometry.bounds()))boxes.add(geometry.bounds().move(tile.getBlockPos()));}
        else for(int i=0;i<geometry.collisionCount();i++){var box=geometry.collision(i);if(!inside(box))boxes.add(box.move(tile.getBlockPos()));}
        var all=boxes.toArray(AABB[]::new);var shapes=new VoxelShape[all.length];for(int i=0;i<all.length;i++)shapes[i]=Shapes.create(all[i]);
        int cost=256+geometry.retainedBytes()+all.length*512;var result=new Collision(all,shapes,cost);
        if(cost<=BUDGET){COLLISIONS.put(key,result);retained+=cost;var old=COLLISIONS.values().iterator();while(retained>BUDGET&&old.hasNext()){retained-=old.next().bytes();old.remove();}}
        return result;
    }
    private static boolean current(Source source){var tile=source.tile();return !tile.inert()&&!tile.isRemoved()&&tile.geometry()==source.geometry()
            &&NativeTrapdoorEntity.at(tile.getLevel(),tile.getBlockPos())==tile;}
    /** Return vanilla's exact iterable when no loaded owner bucket is near the query. Never union diagonal slices. */
    public static Iterable<VoxelShape> blockCollisions(Level level,AABB query,Iterable<VoxelShape> vanilla){
        if(!INDEX.hasNearbyBuckets(level,query,2))return vanilla;
        return ()->{var sources=new ArrayList<Source>();INDEX.visit(level,query.inflate(2,1,2),null,tile->{
            var geometry=tile.geometry();if(!tile.inert()&&geometry!=null&&geometry.bounds().move(tile.getBlockPos()).intersects(query)){
                var collision=collision(tile,geometry);if(collision.boxes().length!=0)sources.add(new Source(tile,geometry,collision));
            }
        });
            var base=vanilla.iterator();return new Iterator<VoxelShape>(){
                int owner,box;VoxelShape pending;
                private void advance(){if(pending!=null&&!current(sources.get(owner))){pending=null;owner++;box=0;}
                    while(pending==null&&owner<sources.size()){var source=sources.get(owner);if(!current(source)){owner++;box=0;continue;}
                        while(box<source.collision().boxes().length){int index=box++;if(source.collision().boxes()[index].intersects(query)){pending=source.collision().shapes()[index];return;}}
                        owner++;box=0;
                    }
                }
                public boolean hasNext(){if(base.hasNext())return true;advance();return pending!=null;}
                public VoxelShape next(){if(base.hasNext())return base.next();advance();if(pending==null)throw new NoSuchElementException();var result=pending;pending=null;return result;}
            };
        };
    }
    public static synchronized int retainedBytes(){return retained;}
}
