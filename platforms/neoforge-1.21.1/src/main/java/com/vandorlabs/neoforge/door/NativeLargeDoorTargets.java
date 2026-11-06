package com.vandorlabs.neoforge.door;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
/** Inactive loaded-root ray adapter. Activation must route player actions and compare permission checks separately. */
public final class NativeLargeDoorTargets {
    private static final NativeLoadedTileIndex<NativeLargeDoorEntity> INDEX=new NativeLoadedTileIndex<>(NativeLargeDoorEntity.class);private static final ThreadLocal<Finder> FINDER=ThreadLocal.withInitial(Finder::new);private static final ThreadLocal<CollisionFinder> COLLISIONS=ThreadLocal.withInitial(CollisionFinder::new);
    public record Target(BlockPos root,BlockHitResult hit){}
    private NativeLargeDoorTargets(){}
    public static void add(NativeLargeDoorEntity part){if(part.isAnchor())INDEX.add(part);else INDEX.remove(part);}
    public static void remove(NativeLargeDoorEntity part){INDEX.remove(part);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static Target trace(Level level,Vec3 start,Vec3 end){if(!NativeFluidWorld.open(level)||!finite(start)||!finite(end)||start.distanceToSqr(end)>64*64||!INDEX.hasMembers(level))return null;var finder=FINDER.get();if(finder.active)finder=new Finder();finder.active=true;finder.level=level;finder.start=start;finder.end=end;finder.distance=Double.POSITIVE_INFINITY;try{INDEX.visit(level,new AABB(start,end).inflate(8),null,finder);return finder.best;}finally{finder.active=false;finder.level=null;finder.start=finder.end=null;finder.best=null;}}
    public static Target pick(Level level,Entity viewer,Vec3 start,Vec3 end){if(viewer==null)return null;var picked=NativeDoorTargets.pick(level,viewer,start,end);return picked==null?null:new Target(picked.root(),picked.hit());}
    /** The caller supplies its existing collision list so already-emitted boxes are not duplicated. */
    public static void appendCollisions(Level level,AABB query,java.util.List<AABB> output){if(!INDEX.hasNearbyBuckets(level,query,5))return;var visitor=COLLISIONS.get();if(visitor.active)visitor=new CollisionFinder();visitor.active=true;visitor.level=level;visitor.query=query;visitor.output=output;try{INDEX.visit(level,query.inflate(5),null,visitor);}finally{visitor.active=false;visitor.level=null;visitor.query=null;visitor.output=null;}}
    /** Preserve vanilla's lazy iterable and identity when no nearby Large owner exists. */
    public static Iterable<VoxelShape> blockCollisions(Level level,AABB query,Iterable<VoxelShape> vanilla){
        if(!INDEX.hasNearbyBuckets(level,query,5))return vanilla;
        return ()->{var sources=new java.util.ArrayList<CollisionSource>();INDEX.visit(level,query.inflate(5),null,root->{if(NativeLargeDoorLifecycle.root(level,root.getBlockPos())!=root)return;var geometry=root.rootGeometrySnapshot();if(geometry!=null&&geometry.bounds().intersects(query))sources.add(new CollisionSource(root,geometry));});var base=vanilla.iterator();return new java.util.Iterator<VoxelShape>(){
            int owner,box;VoxelShape pending;
            private void advance(){if(pending!=null){var source=sources.get(owner);if(NativeLargeDoorLifecycle.root(level,source.root().getBlockPos())!=source.root()||source.root().rootGeometrySnapshot()!=source.geometry()){pending=null;owner++;box=0;}}while(pending==null&&owner<sources.size()){var source=sources.get(owner);if(NativeLargeDoorLifecycle.root(level,source.root().getBlockPos())!=source.root()||source.root().rootGeometrySnapshot()!=source.geometry()){owner++;box=0;continue;}var geometry=source.geometry();while(box<geometry.boxes().size()){int index=box++;if(geometry.boxes().get(index).intersects(query)){pending=geometry.worldShapes().get(index);return;}}owner++;box=0;}}
            public boolean hasNext(){if(base.hasNext())return true;advance();return pending!=null;}
            public VoxelShape next(){if(base.hasNext())return base.next();advance();if(pending==null)throw new java.util.NoSuchElementException();var result=pending;pending=null;return result;}
        };};
    }
    private record CollisionSource(NativeLargeDoorEntity root,NativeLargeDoorGeometry.Snapshot geometry){}
    private static final class CollisionFinder implements Consumer<NativeLargeDoorEntity>{boolean active;Level level;AABB query;java.util.List<AABB> output;public void accept(NativeLargeDoorEntity root){if(NativeLargeDoorLifecycle.root(level,root.getBlockPos())==root)NativeLargeDoorGeometry.appendCollisions(root.rootGeometrySnapshot(),query,output);}}
    private static final class Finder implements Consumer<NativeLargeDoorEntity>{boolean active;Level level;Vec3 start,end;double distance;Target best;public void accept(NativeLargeDoorEntity root){if(NativeLargeDoorLifecycle.root(level,root.getBlockPos())!=root)return;var geometry=root.rootGeometrySnapshot();if(geometry==null)return;var hit=NativeLargeDoorGeometry.trace(geometry,start.x,start.y,start.z,end.x,end.y,end.z);if(hit==null)return;double d=hit.distance(start.x,start.y,start.z);if(d<distance){distance=d;best=new Target(root.getBlockPos(),new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.face),NativeLargeDoorGeometry.owner(geometry,hit.x,hit.y,hit.z),false));}}}
}
