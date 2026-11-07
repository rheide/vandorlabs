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
 @SubscribeEvent public static void starting(ServerStartingEvent event){
  var server=event.getServer();
  try{if(!NativeRampJournal.recoveryRootPresent(server))return;}catch(IOException failure){throw new IllegalStateException("Ramp recovery admission cannot inspect journal directory",failure);}
  var pending=new ArrayList<ServerLevel>();for(var level:server.getAllLevels())pending.add(level);
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
  while(!pending.isEmpty()){
   for(var iterator=pending.iterator();iterator.hasNext();){var level=iterator.next();var status=NativeRampJournal.load(level);if(status==NativeRampJournal.Status.REFUSED)throw new IllegalStateException("Ramp recovery admission refused in "+level.dimension().location()+": "+NativeRampJournal.loadFailure(level));if(status==NativeRampJournal.Status.READY)iterator.remove();}
   if(pending.isEmpty())return;
   if(System.nanoTime()-deadline>=0)throw new IllegalStateException("Ramp recovery admission timed out; journals retained before gameplay");
   try{Thread.sleep(1);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("Ramp recovery admission interrupted; journals retained",failure);}
  }
 }
 private NativeRampRecoveryAdmission(){}
}
