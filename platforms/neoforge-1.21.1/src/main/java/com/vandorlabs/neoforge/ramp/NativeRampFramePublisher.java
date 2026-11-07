package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Same-tick prepared frame replacement. The journal owns durable scope, riders and notifications. */
public final class NativeRampFramePublisher {
 public enum Status {INSTALLED,RESTORED,REFUSED,FAILED}
 public record Result(Status status,String error){}
 public record Frame(Set<BlockPos> cells,NativeRampPartData.Motion motion,NativeRampPartData.Belt belt,long revision){public Frame{var immutable=new LinkedHashSet<BlockPos>();for(var pos:cells)immutable.add(pos.immutable());cells=Set.copyOf(immutable);Objects.requireNonNull(motion);Objects.requireNonNull(belt);if(cells.isEmpty()||cells.size()>8192||revision<0)throw new IllegalArgumentException("Ramp frame bounds");}}
 static volatile java.util.function.Consumer<String> testFault;
 private static void fault(String stage){var callback=testFault;if(callback!=null&&System.getProperty("vandorlabs.transmitterPhase","").startsWith("ramp-assembly-journal-frame"))callback.accept(stage);}
 private static final long LIMIT=64L*1024*1024;
 public static final class Prepared {
  private ServerLevel level;private NativeRampPartPublisher.Prepared candidate,rollback;private Map<BlockPos,NativeRampPartEntity> originalOwners;private final UUID operation;private final BlockPos root;private final BlockState proxy;private final Frame before,after;private final long rollbackRevision,tick,bytes;private boolean consumed;
  private Prepared(ServerLevel level,BlockState proxy,UUID operation,BlockPos root,Frame before,Frame after,long rollbackRevision,Map<BlockPos,NativeRampPartEntity> owners,NativeRampPartPublisher.Prepared candidate,NativeRampPartPublisher.Prepared rollback){this.level=level;this.proxy=proxy;this.operation=operation;this.root=root.immutable();this.before=before;this.after=after;this.rollbackRevision=rollbackRevision;originalOwners=Map.copyOf(owners);this.candidate=candidate;this.rollback=rollback;tick=level.getGameTime();bytes=candidate.preparedBytes()+rollback.preparedBytes()+overhead(before,after);}
  public long preparedBytes(){return bytes;}
  public Frame before(){return before;}public Frame after(){return after;}public long rollbackRevision(){return rollbackRevision;}
  /** No observers run here. On failure, return the last valid frozen frame when exact ownership permits it. */
  public Result install(){
   if(consumed)throw new IllegalStateException("Ramp frame already consumed");var world=level;NativeRampRiders.requireThread(world);boolean mutationStarted=false;
   try{
    if(world.getGameTime()!=tick)throw new IllegalStateException("Ramp prepared frame expired");preflight(world);
    mutationStarted=true;candidate.install();fault("after-candidate");for(var pos:before.cells())if(!after.cells().contains(pos))remove(world,pos,originalOwners.get(pos));fault("after-retirement");return new Result(Status.INSTALLED,"");
   }catch(RuntimeException failure){
    String problem=message(failure);if(!mutationStarted)return new Result(Status.REFUSED,problem);
    try{for(var pos:after.cells())if(!before.cells().contains(pos)){var chunk=loaded(world,pos);var state=chunk.getBlockState(pos);var owner=chunk.getBlockEntities().get(pos);if(state.isAir()&&owner==null&&chunk.getBlockEntityNbt(pos)==null)continue;if(!(owner instanceof NativeRampPartEntity part)||!state.is(proxy.getBlock())||!part.belongs(operation,root)||part.publication()==null||part.publication().revision()!=after.revision()||part.publication().observationTick()!=tick)throw new IllegalStateException("Changed Ramp candidate cannot be retired");remove(world,pos,part);}rollback.install();return new Result(Status.RESTORED,problem);
    }catch(RuntimeException recovery){return new Result(Status.FAILED,problem+"; last frame: "+message(recovery));}
   }finally{consumed=true;level=null;candidate=null;rollback=null;originalOwners=Map.of();}
  }
  private void preflight(ServerLevel world){
   for(var entry:originalOwners.entrySet()){var pos=entry.getKey();scope(world,pos);var chunk=loaded(world,pos);if(!chunk.getBlockState(pos).is(proxy.getBlock())||chunk.getBlockEntities().get(pos)!=entry.getValue()||!entry.getValue().belongs(operation,root)||entry.getValue().publication()==null)throw new IllegalStateException("Ramp old frame owner changed");var publication=entry.getValue().publication();if(publication.revision()!=before.revision()||publication.revision()>after.revision()||publication.revision()==after.revision()&&publication.observationTick()>=tick)throw new IllegalStateException("Ramp old frame revision changed");}
   for(var pos:after.cells()){scope(world,pos);if(before.cells().contains(pos))continue;var chunk=loaded(world,pos);if(!chunk.getBlockState(pos).isAir()||chunk.getBlockEntities().get(pos)!=null||chunk.getBlockEntityNbt(pos)!=null)throw new IllegalStateException("Ramp next frame is obstructed");}
  }
  private void scope(ServerLevel world,BlockPos pos){if(!NativeRampHeldOwners.mutationAllows(world,operation,root,pos))throw new IllegalStateException("Ramp frame requires exact durable operation scope");}
  private void remove(ServerLevel world,BlockPos pos,NativeRampPartEntity expected){scope(world,pos);var chunk=loaded(world,pos);if(chunk.getBlockEntities().get(pos)!=expected||!chunk.getBlockState(pos).is(proxy.getBlock())||!expected.belongs(operation,root))throw new IllegalStateException("Ramp retirement owner changed");if(!world.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Ramp frame retirement refused");}
 }
 public static Prepared prepare(ServerLevel level,BlockState proxy,UUID operation,BlockPos root,NativeRampAssembly assembly,Frame lastValid,Frame candidate,long rollbackRevision){
  NativeRampRiders.requireThread(level);Objects.requireNonNull(operation);Objects.requireNonNull(root);Objects.requireNonNull(assembly);Objects.requireNonNull(lastValid);Objects.requireNonNull(candidate);
  var motion=lastValid.motion();var belt=lastValid.belt();if(motion.moving()||motion.from()!=motion.target()||motion.from()!=motion.observed()||belt.running()||belt.distance()!=belt.observed()||candidate.revision()<lastValid.revision()||rollbackRevision<=candidate.revision()||!assembly.potential().containsAll(lastValid.cells())||!assembly.potential().containsAll(candidate.cells()))throw new IllegalArgumentException("Ramp frame requires frozen last-valid controls and ordered revisions");
  var owners=new LinkedHashMap<BlockPos,NativeRampPartEntity>();for(var pos:lastValid.cells()){var chunk=loaded(level,pos);var owner=chunk.getBlockEntities().get(pos);if(!chunk.getBlockState(pos).is(proxy.getBlock())||!(owner instanceof NativeRampPartEntity part)||!part.belongs(operation,root)||part.publication()==null||part.publication().revision()!=lastValid.revision())throw new IllegalArgumentException("Ramp old frame unavailable");owners.put(pos,part);}
  long available=LIMIT-overhead(lastValid,candidate);var next=NativeRampPartPublisher.prepare(level,proxy,operation,root,assembly,candidate.cells(),candidate.motion(),candidate.belt(),candidate.revision(),available);long remaining=available-next.preparedBytes();if(remaining<=0)throw new IllegalArgumentException("Ramp paired frame retained-size budget");var rollback=NativeRampPartPublisher.prepare(level,proxy,operation,root,assembly,lastValid.cells(),motion,belt,rollbackRevision,remaining);return new Prepared(level,proxy,operation,root,lastValid,candidate,rollbackRevision,owners,next,rollback);
 }
 private static long overhead(Frame before,Frame after){return 1024+192L*before.cells().size()+128L*after.cells().size();}
 private static net.minecraft.world.level.chunk.LevelChunk loaded(ServerLevel level,BlockPos pos){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(level.isOutsideBuildHeight(pos)||chunk==null)throw new IllegalStateException("Ramp frame waits for loaded cell");return chunk;}
 private static String message(Throwable failure){String s=failure.getMessage();return s==null?failure.getClass().getSimpleName():s.substring(0,Math.min(512,s.length()));}
 private NativeRampFramePublisher(){}
}
