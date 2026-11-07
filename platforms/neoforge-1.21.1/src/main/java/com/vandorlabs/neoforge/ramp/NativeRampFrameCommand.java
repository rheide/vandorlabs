package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;

/** Internal frame caller only. Capture, command persistence, original reset and scheduling remain external. */
final class NativeRampFrameCommand {
 enum Outcome {INSTALLED,RESTORED,REFUSED,PENDING,HELD_FAILURE}
 record Notices(Set<BlockPos> pending,String error){Notices{var copy=new LinkedHashSet<BlockPos>();for(var pos:pending)copy.add(pos.immutable());if(copy.size()>8193)throw new IllegalArgumentException("Ramp notification budget");pending=Set.copyOf(copy);error=bound(error);}}
 record Receipt(Outcome outcome,NativeRampFramePublisher.Frame frame,Notices notices,int correctionFailures,String error){Receipt{Objects.requireNonNull(outcome);Objects.requireNonNull(notices);if(correctionFailures<0)throw new IllegalArgumentException("Ramp correction count");error=bound(error);}}
 private static final Notices CLEAR=new Notices(Set.of(),"");
 static Receipt advance(ServerLevel level,NativeRampJournal.Operation operation,ServerPlayer actor,NativeRampFramePublisher.Frame before,NativeRampFramePublisher.Frame candidate,long rollbackRevision,NativeRampRiders.Sync sync){
  NativeRampRiders.requireThread(level);Objects.requireNonNull(before);Objects.requireNonNull(candidate);Objects.requireNonNull(sync);var readiness=NativeRampJournal.frameReadiness(level,operation);if(readiness!=NativeRampJournal.Status.READY)return receipt(readiness==NativeRampJournal.Status.PENDING?Outcome.PENDING:Outcome.REFUSED,before,"Ramp publication is not ready");var changed=new LinkedHashSet<BlockPos>(before.cells());changed.addAll(candidate.cells());if(!NativeRampJournal.frameAuthority(level,operation,actor,changed))return receipt(Outcome.REFUSED,before,"Ramp movement authority changed");if(level.noSave)return receipt(Outcome.PENDING,before,"Saving is disabled");
  // Belt transport uses its separate real planner/state. It must never inherit an empty-rider fallback.
  if(before.belt().mode()!=0||candidate.belt().mode()!=0)return receipt(Outcome.REFUSED,before,"Belt frame transport requires its deployed planner");
  Set<BlockPos> swept;try{var assembly=operation.assembly();double next=candidate.motion().pose(level.getGameTime()+1,0);if(!candidate.cells().equals(assembly.cells(candidate.motion().observed(),next)))return receipt(Outcome.REFUSED,before,"Ramp candidate reservation does not match its timeline");swept=assembly.cells(before.motion().observed(),next);changed.addAll(swept);}catch(RuntimeException failure){return receipt(Outcome.REFUSED,before,message(failure));}if(!NativeRampJournal.frameAuthority(level,operation,actor,changed))return receipt(Outcome.REFUSED,before,"Ramp swept permission or loaded state changed");
  for(var pos:swept){if(before.cells().contains(pos))continue;var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return receipt(Outcome.PENDING,before,"Load all platform chunks first");if(chunk.getBlockState(pos).isAir()&&chunk.getBlockEntities().get(pos)==null&&chunk.getBlockEntityNbt(pos)==null)continue;if(NativeRampClearance.cooperative(level,pos,operation.assembly().settings(),candidate.motion().target()>0)!=null)return receipt(Outcome.PENDING,before,"Waiting for canopy to open");return receipt(Outcome.REFUSED,before,"Movement path is obstructed");}

  NativeRampFramePublisher.Prepared staged;NativeRampRiderPlan riders;try{var assembly=operation.assembly();boolean shaped=assembly.original().getBlock() instanceof StairBlock;riders=NativeRampRiderPlan.prepare(level,assembly.footprint(),shaped,before.motion().observed(),candidate.motion().observed(),false,false);if(riders==null)return receipt(Outcome.REFUSED,before,"Movement stopped: a rider is obstructed");staged=NativeRampFramePublisher.prepare(level,NativeRampJournal.frameProxy(operation),operation.id(),operation.root(),assembly,before,candidate,rollbackRevision);}catch(RuntimeException failure){return receipt(Outcome.REFUSED,before,message(failure));}
  try(staged){
   if(!NativeRampJournal.frameAuthority(level,operation,actor,changed)||!riders.ready())return receipt(Outcome.REFUSED,before,"Ramp preparation changed");NativeRampFramePublisher.Result installed;boolean actorsCommitted=false;
   try(var scope=NativeRampHeldOwners.mutate(operation)){
    installed=staged.install();if(installed.status()!=NativeRampFramePublisher.Status.INSTALLED)return switch(installed.status()){case RESTORED->receipt(Outcome.RESTORED,restored(before,rollbackRevision),installed.error());case REFUSED->receipt(Outcome.REFUSED,before,installed.error());default->receipt(Outcome.HELD_FAILURE,null,installed.error());};
    try{if(!riders.recheck()||!riders.commit(sync)){var result=staged.restoreLastValid();return receipt(result.status()==NativeRampFramePublisher.Status.RESTORED?Outcome.RESTORED:Outcome.HELD_FAILURE,result.status()==NativeRampFramePublisher.Status.RESTORED?restored(before,rollbackRevision):null,"Rider changed before physical commit: "+result.error());}actorsCommitted=true;}catch(RuntimeException failure){var result=staged.restoreLastValid();return receipt(result.status()==NativeRampFramePublisher.Status.RESTORED?Outcome.RESTORED:Outcome.HELD_FAILURE,result.status()==NativeRampFramePublisher.Status.RESTORED?restored(before,rollbackRevision):null,message(failure)+"; "+result.error());}
   }
   // The rollback fence closes after every physical actor commits and before any external callback.
   if(!actorsCommitted)throw new IllegalStateException("Ramp actor commit fence");staged.complete();var positions=new LinkedHashSet<BlockPos>();if(before.revision()!=candidate.revision()||before.motion().sequence()!=candidate.motion().sequence()){positions.add(operation.root());positions.addAll(before.cells());positions.addAll(candidate.cells());}else{for(var pos:changed)if(before.cells().contains(pos)!=candidate.cells().contains(pos))positions.add(pos);}var notices=notify(level,operation,positions,candidate.cells());return new Receipt(Outcome.INSTALLED,candidate,notices,riders.correctionFailures(),notices.error().isEmpty()&&riders.correctionFailures()>0?"Rider correction delivery failed; endpoint retained":notices.error());
  }
 }
 /** Retry publication only; never rebuild geometry, move actors or roll their endpoint back. */
 static Notices retry(ServerLevel level,NativeRampJournal.Operation operation,Notices previous,Set<BlockPos> currentCells){NativeRampRiders.requireThread(level);return previous.pending().isEmpty()?CLEAR:notify(level,operation,previous.pending(),currentCells);}
 private static Notices notify(ServerLevel level,NativeRampJournal.Operation operation,Collection<BlockPos> positions,Set<BlockPos> currentCells){
  if(!NativeRampJournal.frameInstalled(level,operation))return new Notices(new LinkedHashSet<>(positions),"Ramp notifications wait for the installed controller");var pending=new LinkedHashSet<BlockPos>();String problem="";var proxy=NativeRampJournal.frameProxy(operation);
  for(var pos:positions){if(!NativeRampJournal.frameCoordinate(operation,pos)){pending.add(pos);problem="Ramp notification outside its durable coordinates";continue;}var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null){pending.add(pos);problem="Ramp notifications wait for loaded chunks";continue;}var state=chunk.getBlockState(pos);var tile=chunk.getBlockEntities().get(pos);if(currentCells.contains(pos)&&(!(tile instanceof NativeRampPartEntity part)||!state.is(proxy.getBlock())||!part.belongs(operation.id(),operation.root()))){pending.add(pos);problem="Ramp notification owner changed";continue;}
   if(pos.equals(operation.root())||currentCells.contains(pos))try{if(tile!=null)tile.setChanged();}catch(RuntimeException failure){pending.add(pos);problem=message(failure);}
   try{level.sendBlockUpdated(pos,pos.equals(operation.root())?state:proxy,state,Block.UPDATE_CLIENTS);}catch(RuntimeException failure){pending.add(pos);problem=message(failure);}
  }return pending.isEmpty()?CLEAR:new Notices(pending,problem);
 }
 private static NativeRampFramePublisher.Frame restored(NativeRampFramePublisher.Frame before,long revision){return new NativeRampFramePublisher.Frame(before.cells(),before.motion(),before.belt(),revision);}
 private static Receipt receipt(Outcome outcome,NativeRampFramePublisher.Frame frame,String error){return new Receipt(outcome,frame,CLEAR,0,error);}
 private static String message(Throwable failure){var message=failure.getMessage();return bound(message==null?failure.getClass().getSimpleName():message);}
 private static String bound(String text){return text==null?"":text.substring(0,Math.min(256,text.length()));}
 private NativeRampFrameCommand(){}
}
