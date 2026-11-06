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
    public static Target trace(Level level,Vec3 start,Vec3 end){if(!NativeFluidWorld.open(level)||!finite(start)||!finite(end)||start.distanceToSqr(end)>64*64)return null;var finder=FINDER.get();if(finder.active)finder=new Finder();finder.active=true;finder.level=level;finder.start=start;finder.end=end;finder.distance=Double.POSITIVE_INFINITY;try{INDEX.visit(level,new AABB(start,end).inflate(8),null,finder);return finder.best;}finally{finder.active=false;finder.level=null;finder.start=finder.end=null;finder.best=null;}}
    public static Target pick(Level level,Entity viewer,Vec3 start,Vec3 end){if(viewer==null)return null;var target=trace(level,start,end);if(target==null)return null;boolean unavailable=BlockGetter.traverseBlocks(start,end,level,(world,pos)->world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null?Boolean.TRUE:null,world->Boolean.FALSE);if(unavailable)return null;var barrier=level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,viewer){@Override public VoxelShape getBlockShape(BlockState state,BlockGetter world,BlockPos pos){return state.getBlock() instanceof NativeLargeDoorBlock?Shapes.empty():super.getBlockShape(state,world,pos);}});return barrier.getType()==HitResult.Type.MISS||start.distanceToSqr(barrier.getLocation())>=start.distanceToSqr(target.hit().getLocation())?target:null;}
    /** The caller supplies its existing collision list so already-emitted boxes are not duplicated. */
    public static void appendCollisions(Level level,AABB query,java.util.List<AABB> output){if(!NativeFluidWorld.open(level))return;var visitor=COLLISIONS.get();if(visitor.active)visitor=new CollisionFinder();visitor.active=true;visitor.level=level;visitor.query=query;visitor.output=output;try{INDEX.visit(level,query.inflate(5),null,visitor);}finally{visitor.active=false;visitor.level=null;visitor.query=null;visitor.output=null;}}
    private static final class CollisionFinder implements Consumer<NativeLargeDoorEntity>{boolean active;Level level;AABB query;java.util.List<AABB> output;public void accept(NativeLargeDoorEntity root){if(NativeLargeDoorLifecycle.root(level,root.getBlockPos())==root)NativeLargeDoorGeometry.appendCollisions(root.rootGeometrySnapshot(),query,output);}}
    private static final class Finder implements Consumer<NativeLargeDoorEntity>{boolean active;Level level;Vec3 start,end;double distance;Target best;public void accept(NativeLargeDoorEntity root){if(NativeLargeDoorLifecycle.root(level,root.getBlockPos())!=root)return;var geometry=root.rootGeometrySnapshot();if(geometry==null)return;var hit=NativeLargeDoorGeometry.trace(geometry,start.x,start.y,start.z,end.x,end.y,end.z);if(hit==null)return;double d=hit.distance(start.x,start.y,start.z);if(d<distance){distance=d;best=new Target(root.getBlockPos(),new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.face),NativeLargeDoorGeometry.owner(geometry,hit.x,hit.y,hit.z),false));}}}
}
