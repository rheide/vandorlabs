package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.canopy.*;
import com.vandorlabs.neoforge.ramp.NativeRampReservations;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Lazy close preparation. Workers receive immutable scalar inputs, never a world or block entity. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCanopySweeps {
    private static final long BUDGET=8L*1024*1024;
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),task->{var thread=new Thread(task,"Vandor native canopy closure");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    public record Input(NativeCanopyRenderSnapshot visual,int x,int y,int z){}
    private static final class Pending {
        final Input input;final NativeCanopyEntity owner;Future<CanopyBoundsIndex> future;boolean failed;
        Pending(Input input,NativeCanopyEntity owner){this.input=input;this.owner=owner;}
    }
    private static final class State {
        final Map<BlockPos,Pending> pending=new HashMap<>();
        final LinkedHashMap<Input,CanopyBoundsIndex> ready=new LinkedHashMap<>(16,.75f,true);long retained;
        void put(Input input,CanopyBoundsIndex index){long cost=index.retainedBytes()+4096;if(cost>BUDGET)return;while(retained+cost>BUDGET){var iterator=ready.entrySet().iterator();var old=iterator.next();retained-=old.getValue().retainedBytes()+4096;iterator.remove();}var old=ready.put(input,index);if(old!=null)retained-=old.retainedBytes()+4096;retained+=cost;}
    }
    private static final Map<ServerLevel,State> WORLDS=new WeakHashMap<>();
    private NativeCanopySweeps(){}
    private static Input input(NativeCanopyEntity owner){var p=owner.getBlockPos();return new Input(owner.publication().visual(),p.getX(),p.getY(),p.getZ());}
    private static boolean installed(NativeCanopyEntity owner){return owner.getLevel() instanceof ServerLevel world&&world.getServer().isSameThread()&&owner.available()&&NativeCanopyLifecycle.installed(owner);}
    /** Pure CPU operation; full-open to closed is the released conservative close interval. */
    public static CanopyBoundsIndex build(Input input){
        var v=input.visual();var context=new CanopySweepBounds.Context(v.kind(),v.width(),v.settings().data(),1,0);
        var faces=NativeCanopyModels.builtin().geometry(v.kind(),v.width(),v.mask(),v.roof(),v.centered(),v.settings().data());
        var boxes=new ArrayList<CanopyPhysics.Box>(faces.size());int count=0;
        for(var face:faces){if((count++&63)==0&&Thread.currentThread().isInterrupted())throw new CancellationException("Canopy sweep retired");var local=context.bounds(face);boxes.add(CanopySweepBounds.conservativeWorld(context,local,v.facing(),input.x(),input.y(),input.z()));}
        return new CanopyBoundsIndex(boxes);
    }
    private static void cancel(Pending pending){if(pending.future!=null){pending.future.cancel(true);if(pending.future instanceof Runnable task)WORKER.remove(task);}}
    private static void submit(Pending pending){if(pending.future!=null||pending.failed)return;var immutable=pending.input;try{pending.future=WORKER.submit(()->build(immutable));}catch(RejectedExecutionException busy){/* Deferred game-thread submission; pending is not refusal. */}}
    private static CanopyBoundsIndex ready(NativeCanopyEntity owner){if(!installed(owner))return null;return WORLDS.computeIfAbsent((ServerLevel)owner.getLevel(),world->new State()).ready.get(input(owner));}
    private static NativeRampReservations.Status request(NativeCanopyEntity owner){
        if(!installed(owner))return NativeRampReservations.Status.REFUSED;var state=WORLDS.computeIfAbsent((ServerLevel)owner.getLevel(),world->new State());var input=input(owner);if(state.ready.containsKey(input))return NativeRampReservations.Status.READY;
        var pos=owner.getBlockPos();var pending=state.pending.get(pos);if(pending!=null&&(pending.owner!=owner||!pending.input.equals(input))){state.pending.remove(pos);cancel(pending);pending=null;}
        if(pending==null){if(state.pending.size()>=1024)return NativeRampReservations.Status.REFUSED;pending=new Pending(input,owner);state.pending.put(pos,pending);}submit(pending);return pending.failed?NativeRampReservations.Status.REFUSED:NativeRampReservations.Status.PENDING;
    }
    /** Construction is cheap: readiness is requested only if the loaded Ramp coordinator needs geometry. */
    public static NativeRampReservations.SweepQuery query(NativeCanopyEntity owner){return new NativeRampReservations.SweepQuery(){
        private CanopyBoundsIndex index;
        public NativeRampReservations.Status readiness(){var status=request(owner);index=status==NativeRampReservations.Status.READY?ready(owner):null;return status;}
        public boolean intersects(AABB box){if(index==null)throw new IllegalStateException("Canopy sweep not ready");return index.intersects(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    };}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;var state=WORLDS.get(world);if(state==null)return;int submitted=0;
        for(var iterator=state.pending.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();var pending=entry.getValue();if(!installed(pending.owner)||!pending.input.equals(input(pending.owner))){cancel(pending);iterator.remove();continue;}
            if(pending.failed)continue;if(pending.future==null){if(submitted++<4)submit(pending);continue;}if(!pending.future.isDone())continue;
            try{var index=pending.future.get();if(index.retainedBytes()+4096>BUDGET){pending.failed=true;NativeCanopyControl.retryClose(pending.owner);continue;}state.put(pending.input,index);iterator.remove();NativeCanopyControl.retryClose(pending.owner);}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
            catch(ExecutionException|CancellationException failure){pending.failed=true;System.getLogger(NativeCanopySweeps.class.getName()).log(System.Logger.Level.ERROR,"Canopy close geometry failed; retaining safe pose",failure);NativeCanopyControl.retryClose(pending.owner);}
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world){var state=WORLDS.remove(world);if(state!=null)for(var pending:state.pending.values())cancel(pending);}}
}
