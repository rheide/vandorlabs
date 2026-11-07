package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;

/** Operation-local, nonpersistent belt transport. Preparation never mutates actors. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeRampBeltRiders {
    @net.neoforged.bus.api.SubscribeEvent public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof Level level)clearWorld(level);}
    private static final Map<State,Boolean> STATES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class History {long generation;Vec3 position;History(long generation,Vec3 position){this.generation=generation;this.position=position;}}
    public static final class State {
        private java.lang.ref.WeakReference<NativeRampFootprint.Spec> profile;private long[] chunks;private java.lang.ref.WeakReference<Level> world;private final UUID operation;private NativeEntityMemo<History> history=new NativeEntityMemo<>(8192);private long generation,epoch,lastTick=Long.MIN_VALUE;
        public State(UUID operation){this.operation=Objects.requireNonNull(operation);STATES.put(this,Boolean.TRUE);}
        public UUID operation(){return operation;}
        public void reset(){var owner=world==null?null:world.get();if(owner!=null)NativeRampRiders.requireThread(owner);history=new NativeEntityMemo<>(8192);generation=0;epoch++;lastTick=Long.MIN_VALUE;profile=null;chunks=null;}
        public int retainedEntries(){return history.size();}
        private void loaded(ServerLevel level,NativeRampFootprint.Spec spec){if(profile==null||profile.get()!=spec){var unique=new LinkedHashSet<Long>();for(var p:NativeRampBeltGeometry.cells(spec))unique.add(net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));if(unique.size()>64)throw new IllegalArgumentException("Belt reserved chunk budget");chunks=new long[unique.size()];int i=0;for(long chunk:unique)chunks[i++]=chunk;profile=new java.lang.ref.WeakReference<>(spec);epoch++;}for(long chunk:chunks)if(level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk))==null)throw new IllegalArgumentException("Belt waits for reserved chunks");}
        private Vec3 previous(Entity entity){var row=history.get(entity);return row!=null&&row.generation==generation?row.position:null;}
    }
    /** No world/entity is retained by the state registry or history values. */
    public static void clearWorld(Level level){synchronized(STATES){for(var state:STATES.keySet()){if(state.world!=null&&state.world.get()==level){state.reset();state.world=null;}}}}
    private record Entry(Entity entity,NativeRampBeltTargets.Candidate observed,NativeRampBeltTargets.Move move){}
    public static final class Plan {
        private final ServerLevel level;private final NativeRampFootprint.Spec spec;private final boolean shaped,reset;private final State state;private final long epoch,generation,tick;private final double before,next;private final int mode,speed;private final List<Entity> actors;private final List<NativeRampBeltTargets.Candidate> observations;private final List<Entry> entries;private final boolean duplicate;private boolean consumed;private int correctionFailures;
        private Plan(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,State state,double before,int mode,int speed,List<Entity> actors,List<NativeRampBeltTargets.Candidate> observations,NativeRampBeltTargets.Result result,boolean duplicate,boolean reset){this.level=level;this.spec=spec;this.shaped=shaped;this.state=state;this.before=before;this.mode=mode;this.speed=speed;this.actors=List.copyOf(actors);this.observations=List.copyOf(observations);this.next=result.nextDistance();this.duplicate=duplicate;this.reset=reset;epoch=state.epoch;generation=state.generation;tick=level.getGameTime();var rows=new ArrayList<Entry>();for(var move:result.moves())rows.add(new Entry(actors.get(move.actor().key()),move.actor(),move));entries=List.copyOf(rows);}
        public double nextDistance(){return next;}
        public int correctionFailures(){return correctionFailures;}
        public int size(){return entries.size();}
        public boolean ready(){NativeRampRiders.requireThread(level);if(consumed||state.epoch!=epoch||state.generation!=generation||level.getGameTime()!=tick)return false;for(int i=0;i<actors.size();i++)if(actors.get(i).level()!=level||!candidate(actors.get(i),i,state).equals(observations.get(i)))return false;return true;}
        /** Must be called after all physical publication preflight, before commit. */
        public boolean recheck(){if(!ready())return false;if(duplicate)return true;state.loaded(level,spec);loaded(level,bounds(spec,shaped,before,reset));var current=new ArrayList<Entity>();level.getEntities(EntityTypeTest.forClass(Entity.class),bounds(spec,shaped,before,reset),entity->!entity.isSpectator(),current,8193);if(!sameActors(current,actors))return false;var result=select(level,spec,shaped,before,mode,speed,reset,actors,observations);if(result==null||result.moves().size()!=entries.size())return false;for(int i=0;i<entries.size();i++)if(!entries.get(i).move.equals(result.moves().get(i)))return false;return true;}
        public boolean commit(NativeRampRiders.Sync sync){if(!ready())return false;for(var entry:entries)if(entry.entity instanceof ServerPlayer)Objects.requireNonNull(sync,"Belt player correction sender");consumed=true;if(duplicate)return true;
            var batch=new ArrayList<NativeRampRiderBatch.Step>(entries.size());for(var entry:entries){var move=entry.move;var delta=move.anchor()==null?move.delta():move.target().subtract(entry.observed.position());batch.add(new NativeRampRiderBatch.Step(entry.entity,delta,!reset&&!spec.verticalLayout()&&entry.entity instanceof ItemEntity,move.delta().y));}correctionFailures=NativeRampRiderBatch.commit(level,batch,sync);
            if(reset){state.reset();return true;}if(spec.verticalLayout()){state.lastTick=tick;return true;}long nextGeneration=++state.generation;for(var entry:entries){var row=state.history.get(entry.entity);if(row==null)state.history.put(entry.entity,new History(nextGeneration,entry.entity.position()));else{row.generation=nextGeneration;row.position=entry.entity.position();}}state.lastTick=tick;return true;
        }
    }
    /** Null means an obstructed actor; cold/unsupported queries fail explicitly. No elapsed-time catchup. */
    public static Plan prepare(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,State state,double distance,int mode,int speed){return prepare(level,spec,shaped,state,distance,mode,speed,false);}
    public static Plan prepareReset(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,State state,double distance,int mode,int speed){return prepare(level,spec,shaped,state,distance,mode,speed,true);}
    private static Plan prepare(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,State state,double distance,int mode,int speed,boolean reset){NativeRampRiders.requireThread(level);Objects.requireNonNull(state);if(state.world==null||state.world.get()!=level){state.reset();state.world=new java.lang.ref.WeakReference<>(level);}NativeRampBeltTargets.profile(spec);NativeRampBeltTargets.delta(spec,mode,speed);state.loaded(level,spec);if(!Double.isFinite(distance)||Math.abs(distance)>1e12)throw new IllegalArgumentException("Belt distance");
        if(!reset&&state.lastTick==level.getGameTime())return new Plan(level,spec,shaped,state,distance,mode,speed,List.of(),List.of(),new NativeRampBeltTargets.Result(distance,List.of()),true,reset);
        var bounds=bounds(spec,shaped,distance,reset);loaded(level,bounds);var actors=new ArrayList<Entity>();level.getEntities(EntityTypeTest.forClass(Entity.class),bounds,entity->!entity.isSpectator(),actors,8193);if(actors.size()>8192)throw new IllegalArgumentException("Belt actor budget");var candidates=new ArrayList<NativeRampBeltTargets.Candidate>(actors.size());for(int i=0;i<actors.size();i++)candidates.add(candidate(actors.get(i),i,state));var result=select(level,spec,shaped,distance,mode,speed,reset,actors,candidates);return result==null?null:new Plan(level,spec,shaped,state,distance,mode,speed,actors,candidates,result,false,reset);
    }
    static boolean sameActors(List<Entity> current,List<Entity> expected){if(current.size()!=expected.size())return false;var identities=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());identities.addAll(expected);for(var actor:current)if(!identities.remove(actor))return false;return identities.isEmpty();}
    private static NativeRampBeltTargets.Result select(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,double distance,int mode,int speed,boolean reset,List<Entity> actors,List<NativeRampBeltTargets.Candidate> candidates){long[] work={0};NativeRampBeltTargets.Obstacles obstacles=(actor,bounds)->{var boxes=NativeRampRiders.obstacles(level,spec.root(),actors.get(actor.key()),bounds);if((work[0]+=boxes.size())>16777216)throw new IllegalArgumentException("Belt obstacle budget");return boxes;};return reset||spec.verticalLayout()?NativeRampBeltTargets.transition(spec,shaped,distance,mode,speed,reset,candidates,obstacles):NativeRampBeltTargets.select(spec,shaped,distance,mode,speed,candidates,obstacles);}
    private static NativeRampBeltTargets.Candidate candidate(Entity entity,int key,State state){return new NativeRampBeltTargets.Candidate(key,entity.position(),entity.getBoundingBox(),entity instanceof Player,entity instanceof ItemEntity,entity.isRemoved(),entity.noPhysics,entity.isPassenger(),entity instanceof Player player&&player.getAbilities().flying,entity.isSpectator(),entity.getDeltaMovement(),entity.maxUpStep(),state.previous(entity));}
    private static AABB bounds(NativeRampFootprint.Spec spec,boolean shaped,double distance,boolean reset){if(!reset&&!spec.verticalLayout())return NativeRampBeltTargets.bounds(spec);var surfaces=NativeRampBeltGeometry.surfaces(spec,shaped,distance);AABB bounds=surfaces.getFirst();for(var b:surfaces)bounds=bounds.minmax(b);return bounds.inflate(0,.6,0);}
    private static void loaded(ServerLevel level,AABB bounds){for(double v:new double[]{bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ})if(!Double.isFinite(v)||Math.abs(v)>30000001)throw new IllegalArgumentException("Belt query bounds");int x0=net.minecraft.util.Mth.floor(bounds.minX)>>4,x1=net.minecraft.util.Mth.floor(bounds.maxX)>>4,z0=net.minecraft.util.Mth.floor(bounds.minZ)>>4,z1=net.minecraft.util.Mth.floor(bounds.maxZ)>>4;if((long)(x1-x0+1)*(z1-z0+1)>64)throw new IllegalArgumentException("Belt chunk budget");for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalArgumentException("Belt waits for loaded chunks");}
    private NativeRampBeltRiders(){}
}
