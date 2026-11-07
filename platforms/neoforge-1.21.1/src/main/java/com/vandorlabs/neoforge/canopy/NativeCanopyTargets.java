package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.canopy.CanopyPoint;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Released loaded-only external surfaces; the block's local physical shape intentionally stays empty. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCanopyTargets {
    private static final NativeLoadedTileIndex<NativeCanopyEntity> INDEX=new NativeLoadedTileIndex<>(NativeCanopyEntity.class);
    private static final ThreadLocal<Finder> FINDERS=ThreadLocal.withInitial(Finder::new);
    private static final long BUDGET=8L*1024*1024,COST=768;
    // Individual box shapes avoid both dense unions and retaining an entire old geometry per queried surface.
    private static final LinkedHashMap<AABB,VoxelShape> SHAPES=new LinkedHashMap<>(32,.75F,true);
    public record Target(BlockPos root,BlockHitResult hit){}
    private record Source(NativeCanopyEntity owner,NativeCanopyGeometry.Snapshot geometry){}
    private NativeCanopyTargets(){}
    public static void add(NativeCanopyEntity host){INDEX.add(host);}
    public static void remove(NativeCanopyEntity host){INDEX.remove(host);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    /** Exact authored surface hit with the released joined-opening constituent selection. */
    public static BlockHitResult traceOwner(NativeCanopyEntity owner,Vec3 start,Vec3 end){
        var publication=owner.publication();var geometry=publication.geometry();if(!owner.available()||!publication.draws()||geometry==null)return null;
        var hit=geometry.trace(new CanopyPoint(start.x,start.y,start.z),new CanopyPoint(end.x,end.y,end.z));if(hit==null)return null;var selected=owner.getBlockPos();
        if((owner.kind().equals("canopy")||owner.kind().equals("long_canopy"))&&publication.visual().width()>NativeCanopyModels.builtin().dimensions(owner.kind()).width()){
            var right=owner.facing().getClockWise();double local=(hit.x-selected.getX()-.5)*right.getStepX()+(hit.z-selected.getZ()-.5)*right.getStepZ()+.5;int column=Math.max(0,Math.min(publication.visual().width()-1,(int)Math.floor(local)));selected=selected.relative(right,column);
            var member=NativeCanopyLifecycle.at(owner.getLevel(),selected);if(member==null||NativeCanopyLifecycle.constituent(member)!=member||NativeCanopyLifecycle.owner(member)!=owner)return null;
        }
        return new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.face),selected,false);
    }
    public static Target trace(Level level,Vec3 start,Vec3 end){
        if(!NativeFluidWorld.open(level)||!finite(start)||!finite(end)||start.distanceToSqr(end)>64*64||!INDEX.hasMembers(level))return null;
        var finder=FINDERS.get();if(finder.active)finder=new Finder();finder.active=true;finder.start=start;finder.end=end;finder.distance=Double.POSITIVE_INFINITY;
        try{INDEX.visit(level,new AABB(start,end).inflate(12),null,finder);return finder.best;}finally{finder.active=false;finder.start=finder.end=null;finder.best=null;}
    }
    private static final class Finder implements Consumer<NativeCanopyEntity>{boolean active;Vec3 start,end;double distance;Target best;public void accept(NativeCanopyEntity owner){var hit=traceOwner(owner,start,end);if(hit==null)return;double d=start.distanceToSqr(hit.getLocation());if(d<distance){distance=d;best=new Target(owner.getBlockPos(),hit);}}}
    private static synchronized VoxelShape shape(AABB box){var value=SHAPES.get(box);if(value!=null)return value;value=Shapes.create(box);while((SHAPES.size()+1L)*COST>BUDGET){var iterator=SHAPES.keySet().iterator();iterator.next();iterator.remove();}SHAPES.put(box,value);return value;}
    private static boolean current(Source source){var host=source.owner();return host.available()&&host.publication().draws()&&host.publication().geometry()==source.geometry()&&NativeCanopyLifecycle.installed(host);}
    public static Iterable<VoxelShape> blockCollisions(Level level,AABB query,Iterable<VoxelShape> vanilla){
        if(!INDEX.hasNearbyBuckets(level,query,12))return vanilla;
        return ()->{var sources=new ArrayList<Source>();INDEX.visit(level,query.inflate(12),null,host->{var publication=host.publication();if(host.available()&&publication.draws()&&publication.geometry()!=null&&publication.geometry().bounds().intersects(query))sources.add(new Source(host,publication.geometry()));});
            var base=vanilla.iterator();return new Iterator<VoxelShape>(){int owner,box;VoxelShape pending;
                private void advance(){if(pending!=null&&!current(sources.get(owner))){pending=null;owner++;box=0;}while(pending==null&&owner<sources.size()){var source=sources.get(owner);if(!current(source)){owner++;box=0;continue;}var boxes=source.geometry().boxes();while(box<boxes.size()){var value=boxes.get(box++);if(value.intersects(query)){pending=shape(value);return;}}owner++;box=0;}}
                public boolean hasNext(){if(base.hasNext())return true;advance();return pending!=null;}
                public VoxelShape next(){if(base.hasNext())return base.next();advance();if(pending==null)throw new NoSuchElementException();var next=pending;pending=null;return next;}
            };};
    }
    public static synchronized long retainedBytes(){return SHAPES.size()*COST;}
    @SubscribeEvent public static synchronized void unload(LevelEvent.Unload event){SHAPES.clear();}
}
