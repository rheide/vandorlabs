package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Loaded ordinary-door leaf and pad rays, including geometry outside the owner's voxel. */
public final class NativeDoorTargets {
    private static final NativeLoadedTileIndex<NativeProgrammableDoorEntity> INDEX=new NativeLoadedTileIndex<>(NativeProgrammableDoorEntity.class);
    private static final ThreadLocal<Finder> FINDER=ThreadLocal.withInitial(Finder::new);
    public record Target(BlockPos root,BlockHitResult hit){}
    private NativeDoorTargets(){}
    /** Membership is local-only; deferred gameplay initialization remains owned by the host. */
    public static void add(NativeProgrammableDoorEntity host){var state=host.getBlockState();if(!host.large()&&state.hasProperty(NativeProgrammableDoorBlock.HALF)&&state.getValue(NativeProgrammableDoorBlock.HALF)==DoubleBlockHalf.LOWER)INDEX.add(host);}
    public static void remove(NativeProgrammableDoorEntity host){INDEX.remove(host);}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    /** Geometry only: callers must separately compare non-door occluders and validate player permissions. */
    public static Target trace(Level level,Vec3 start,Vec3 end){
        if(!NativeFluidWorld.open(level)||!finite(start)||!finite(end)||start.distanceToSqr(end)>64*64||!INDEX.hasMembers(level))return null;
        var finder=FINDER.get();if(finder.active)finder=new Finder();finder.active=true;finder.level=level;finder.start=start;finder.end=end;finder.distance=Double.POSITIVE_INFINITY;
        try{INDEX.visit(level,new AABB(start,end).inflate(5),null,finder);return finder.best;}
        finally{finder.clear();}
    }
    /** Replaces ordinary-door voxel rays while retaining nearer non-door blocks and unloaded-cell barriers. */
    public static Target pick(Level level,Entity viewer,Vec3 start,Vec3 end){
        var target=trace(level,start,end);if(target==null)return null;
        // Preflight the DDA itself before Level.clip can request any chunk. Door shapes check their own neighbours.
        var visibleEnd=target.hit().getLocation();
        boolean unavailable=BlockGetter.traverseBlocks(start,visibleEnd,level,(world,pos)->world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null?Boolean.TRUE:null,world->Boolean.FALSE);
        if(unavailable)return null;
        var barrier=level.clip(new ClipContext(start,visibleEnd,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,viewer){
            @Override public VoxelShape getBlockShape(BlockState state,BlockGetter world,BlockPos pos){
                if(state.getBlock() instanceof NativeProgrammableDoorBlock){var root=NativeDoorLifecycle.root(level,pos);if(root!=null&&!root.large())return Shapes.empty();}
                return super.getBlockShape(state,world,pos);
            }
        });
        return barrier.getType()==HitResult.Type.MISS||start.distanceToSqr(barrier.getLocation())>=start.distanceToSqr(target.hit().getLocation())?target:null;
    }
    private static final class Finder implements Consumer<NativeProgrammableDoorEntity>{
        boolean active;Level level;Vec3 start,end;double distance;Target best;
        void clear(){active=false;level=null;start=end=null;best=null;}
        @Override public void accept(NativeProgrammableDoorEntity host){
            var root=host.getBlockPos();if(host.large()||NativeDoorLifecycle.root(level,root)!=host)return;
            var block=(NativeProgrammableDoorBlock)host.getBlockState().getBlock();var settings=host.settings();
            for(int half=0;half<2;half++){
                var pos=half==0?root:root.above();var state=block.resolved(level,pos,level.getBlockState(pos));
                var hit=NativeDoorGeometry.trace(settings,block.slidingModel(),state.getValue(NativeProgrammableDoorBlock.FACING),state.getValue(NativeProgrammableDoorBlock.OPEN),state.getValue(NativeProgrammableDoorBlock.HINGE)==DoorHingeSide.LEFT,state.getValue(NativeProgrammableDoorBlock.PAIRED),NativeDoorLifecycle.panelSide(host,state),half!=0,false,start.x-pos.getX(),start.y-pos.getY(),start.z-pos.getZ(),end.x-pos.getX(),end.y-pos.getY(),end.z-pos.getZ());
                if(hit==null)continue;double x=hit.x+pos.getX(),y=hit.y+pos.getY(),z=hit.z+pos.getZ(),dx=x-start.x,dy=y-start.y,dz=z-start.z,d=dx*dx+dy*dy+dz*dz;
                if(d<distance){distance=d;best=new Target(root,new BlockHitResult(new Vec3(x,y,z),Direction.from3DDataValue(hit.face),pos,false));}
            }
        }
    }
}
