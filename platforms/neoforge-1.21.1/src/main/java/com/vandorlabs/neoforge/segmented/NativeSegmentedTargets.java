package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Loaded-only moved panels; the common Door router retains occlusion and server authorization. */
public final class NativeSegmentedTargets {
    private NativeSegmentedTargets(){}
    private static final NativeLoadedTileIndex<NativeSegmentedDoorEntity> INDEX=new NativeLoadedTileIndex<>(NativeSegmentedDoorEntity.class);
    private static final ThreadLocal<Finder> FINDER=ThreadLocal.withInitial(Finder::new);
    private static final com.google.common.cache.Cache<NativeSegmentedDoorEntity,Column> COLUMNS=
            com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumWeight(4*1024*1024)
                    .weigher((NativeSegmentedDoorEntity key,Column value)->256+value.height*640).build();
    private static volatile ToDoubleFunction<NativeSegmentedDoorEntity> clientPose=root->root.pose(0);
    /** Installed only by the physical client; authoritative server worlds always use their own timeline. */
    public static void clientPose(ToDoubleFunction<NativeSegmentedDoorEntity> provider){clientPose=java.util.Objects.requireNonNull(provider);}
    public static double pose(NativeSegmentedDoorEntity root){return root.getLevel()!=null&&root.getLevel().isClientSide?clientPose.applyAsDouble(root):root.pose(0);}
    public static void add(NativeSegmentedDoorEntity tile){INDEX.add(tile);}
    public static void remove(NativeSegmentedDoorEntity tile){INDEX.remove(tile);COLUMNS.invalidate(tile);}
    public record Target(BlockPos root,BlockHitResult hit){}
    private static final class Column {
        final BlockPos root;final Direction facing;final int position,height,column,width,direction;
        final boolean straight;final double progress;
        final BlockPos[] positions;final NativeSegmentedGeometry.Snapshot[] rows;
        Column(NativeSegmentedDoorEntity owner,double progress){
            root=owner.getBlockPos().immutable();facing=owner.facing();var settings=owner.settings();position=settings.position;
            height=owner.height();column=owner.column();width=owner.columnsCount();direction=settings.direction;straight=settings.straight;this.progress=progress;
            positions=new BlockPos[height];rows=new NativeSegmentedGeometry.Snapshot[height];for(int row=0;row<height;row++)positions[row]=root.below(row);
        }
        boolean matches(NativeSegmentedDoorEntity owner,double value){var settings=owner.settings();return root.equals(owner.getBlockPos())&&facing==owner.facing()&&height==owner.height()&&column==owner.column()&&width==owner.columnsCount()&&position==settings.position&&direction==settings.direction&&straight==settings.straight&&progress==value;}
        NativeSegmentedGeometry.Snapshot row(int index){var snapshot=rows[index];if(snapshot==null)rows[index]=snapshot=NativeSegmentedGeometry.snapshot(root,facing,position,index,height,column,width,progress,direction,straight);return snapshot;}
    }
    private static Column column(NativeSegmentedDoorEntity root,double progress){
        var found=COLUMNS.getIfPresent(root);if(found==null||!found.matches(root,progress)){found=new Column(root,progress);COLUMNS.put(root,found);}return found;
    }
    public static NativeSegmentedGeometry.Snapshot geometry(NativeSegmentedDoorEntity tile,double progress){
        var root=tile.root();int row=tile.topOffset();if(row<0||row>=root.height())return null;return column(root,progress).row(row);
    }
    private static final ThreadLocal<int[]> GATE_MASKS=ThreadLocal.withInitial(()->new int[32]);
    private static final ThreadLocal<com.vandorlabs.segmented.SegmentedGateShape.Hit> GATE_HIT=ThreadLocal.withInitial(com.vandorlabs.segmented.SegmentedGateShape.Hit::new);
    public static com.vandorlabs.render.LegacyBoxTrace.Hit hit(NativeSegmentedDoorEntity tile,double progress,Vec3 start,Vec3 end){
        var snapshot=geometry(tile,progress);if(snapshot==null)return null;var root=tile.root();int row=tile.topOffset();int kind=NativeSegmentedArtwork.gate(root.publication().visual().texture(row));
        if(kind<0)return snapshot.trace(start.x,start.y,start.z,end.x,end.y,end.z);
        int[] masks=GATE_MASKS.get();NativeSegmentedGateRays.masks(root,progress,masks);
        return snapshot.traceGate(kind,masks[row],row+1<root.height()?masks[row+1]:0,row>0?masks[row-1]:0,GATE_HIT.get(),start.x,start.y,start.z,end.x,end.y,end.z);
    }
    public static NativeSegmentedDoorEntity loaded(Level level,BlockPos pos){
        if(!NativeFluidWorld.open(level))return null;var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return null;
        var raw=chunk.getBlockEntities().get(pos);return raw instanceof NativeSegmentedDoorEntity tile&&!tile.isRemoved()&&!tile.inert()?tile:null;
    }
    private static boolean finite(Vec3 value){return value!=null&&Double.isFinite(value.x)&&Double.isFinite(value.y)&&Double.isFinite(value.z);}
    public static Target trace(Level level,Vec3 start,Vec3 end){
        if(!NativeFluidWorld.open(level)||!finite(start)||!finite(end)||start.distanceToSqr(end)>64*64||!INDEX.hasMembers(level))return null;
        var finder=FINDER.get();if(finder.active)finder=new Finder();finder.active=true;finder.level=level;finder.start=start;finder.end=end;
        finder.ray=new AABB(start,end).inflate(.001);finder.distance=Double.POSITIVE_INFINITY;
        try{INDEX.visit(level,finder.ray.inflate(34),null,finder);return finder.best;}
        finally{finder.active=false;finder.level=null;finder.start=finder.end=null;finder.ray=null;finder.best=null;}
    }
    private static final class Finder implements Consumer<NativeSegmentedDoorEntity> {
        boolean active;Level level;Vec3 start,end;AABB ray;double distance;Target best;
        @Override public void accept(NativeSegmentedDoorEntity root){
            if(root.topOffset()!=0||!root.formed()||root.inert())return;double progress=pose(root);if(!Double.isFinite(progress)||progress<=0)return;
            var column=column(root,progress);
            if(column.direction>=2){var top=column.row(0).bounds();if(top.maxX<=ray.minX||top.minX>=ray.maxX||top.maxZ<=ray.minZ||top.minZ>=ray.maxZ||top.maxY<=ray.minY||top.minY+1-column.height>=ray.maxY)return;}
            for(int row=0;row<column.height;row++){
                var part=loaded(level,column.positions[row]);if(part==null||part.topOffset()!=row||part.height()!=column.height||part.facing()!=column.facing)continue;
                var hit=hit(part,progress,start,end);if(hit==null)continue;double d=hit.distance(start.x,start.y,start.z);
                if(d<distance){distance=d;best=new Target(root.getBlockPos(),new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.face),column.positions[row],false));}
            }
        }
    }
    @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
    public static final class Lifecycle {
        @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){COLUMNS.invalidateAll();}
    }
}
