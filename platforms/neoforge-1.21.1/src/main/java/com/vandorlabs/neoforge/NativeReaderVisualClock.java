package com.vandorlabs.neoforge;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Owner-position reader clock, preserving motion across carried/packet tile replacement. */
public final class NativeReaderVisualClock {
 private static final VisualClock CLOCK=new VisualClock();
 private static final Map<Object,LinkedHashMap<BlockPos,State[]>> WORLDS=new WeakHashMap<>();
 public static final class State {
  private boolean initialized,present;private long sequence,deadline;private double started,rejectedUntil;
  private float age;private boolean rejected;
  public float age(){return age;}public boolean rejected(){return rejected;}
  public void observe(double now,NativeTokenReader.Visual visual,NativeTokenReader.Motion motion){
   if(!initialized){initialized=true;present=visual.occupied();sequence=motion.sequence();started=now-(present?motion.age():20);deadline=motion.rejectionDeadline();rejectedUntil=now+motion.rejectedFor();}
   if(!visual.occupied()){present=false;age=20;}else{if(!present||sequence!=motion.sequence()){present=true;sequence=motion.sequence();started=now;}age=(float)Math.max(0,Math.min(20,now-started));}
   if(deadline!=motion.rejectionDeadline()){deadline=motion.rejectionDeadline();rejectedUntil=now+motion.rejectedFor();}rejected=now<rejectedUntil;
  }
 }
 private NativeReaderVisualClock(){}
 public static State sample(Object world,BlockPos owner,int slot,NativeTokenReader.Visual visual,NativeTokenReader.Motion motion){return sampleAt(world,owner,slot,visual,motion,CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused()));}
 public static State sampleAt(Object world,BlockPos owner,int slot,NativeTokenReader.Visual visual,NativeTokenReader.Motion motion,double now){
  if(slot<0||slot>2)throw new IllegalArgumentException("Reader visual slot");var owners=WORLDS.computeIfAbsent(world,ignored->new LinkedHashMap<>(16,.75F,true));var slots=owners.get(owner);if(slots==null){slots=new State[3];owners.put(owner.immutable(),slots);if(owners.size()>2048)owners.remove(owners.keySet().iterator().next());}var state=slots[slot];if(state==null)slots[slot]=state=new State();state.observe(now,visual,motion);return state;
 }
 public static void clear(Object world){WORLDS.remove(world);}
}
