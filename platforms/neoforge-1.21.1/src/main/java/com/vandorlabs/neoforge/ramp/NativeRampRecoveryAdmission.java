package com.vandorlabs.neoforge.ramp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** Startup-only admission: no normal tick may precede exact recovered ownership holds. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampRecoveryAdmission {
 private static net.minecraft.server.MinecraftServer startup;
 static volatile java.util.function.Consumer<ServerLevel> storageProbe;
 static boolean active(ServerLevel level){return startup!=null&&startup==level.getServer()&&startup.isSameThread()&&startup.getLevel(level.dimension())==level;}
 @SubscribeEvent public static void starting(ServerStartingEvent event){
  var server=event.getServer();
  try{if(!NativeRampJournal.recoveryRootPresent(server))return;}catch(IOException failure){throw new IllegalStateException("Ramp recovery admission cannot inspect journal directory",failure);}
  if(startup!=null)throw new IllegalStateException("Nested Ramp startup recovery");startup=server;try{
  NativeRampColdActorRecovery repair=null;ServerLevel repairing=null;RuntimeException problem=null;try{
  var pending=new ArrayList<ServerLevel>();for(var level:server.getAllLevels())pending.add(level);
  var probe=storageProbe;if(probe!=null&&System.getProperty("vandorlabs.transmitterPhase","").startsWith("ramp-actor-storage-"))for(var level:pending)probe.accept(level);
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
  while(!pending.isEmpty()){
   for(var iterator=pending.iterator();iterator.hasNext();){var level=iterator.next();var status=NativeRampJournal.load(level);if(status==NativeRampJournal.Status.REFUSED&&!NativeRampJournal.startupActorOperations(level).isEmpty()){if(repair==null){repair=new NativeRampColdActorRecovery(level,NativeRampJournal.startupActorOperations(level));repairing=level;}if(repairing==level){status=repair.poll();if(status==NativeRampJournal.Status.READY){repair.close();repair=null;repairing=null;}}else status=NativeRampJournal.Status.PENDING;}if(status==NativeRampJournal.Status.REFUSED)throw new IllegalStateException("Ramp recovery admission refused in "+level.dimension().location()+": "+NativeRampJournal.loadFailure(level));if(status==NativeRampJournal.Status.READY)iterator.remove();}
   if(pending.isEmpty())return;
   if(System.nanoTime()-deadline>=0)throw new IllegalStateException("Ramp recovery admission timed out; journals retained before gameplay");
   try{Thread.sleep(1);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("Ramp recovery admission interrupted; journals retained",failure);}
  }
  }catch(RuntimeException failure){problem=failure;throw failure;}finally{if(repair!=null)try{repair.close();}catch(RuntimeException failure){if(problem!=null)problem.addSuppressed(failure);else throw failure;}}
  }finally{startup=null;}
 }
 private NativeRampRecoveryAdmission(){}
}
