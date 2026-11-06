package com.vandorlabs.neoforge;
import com.vandorlabs.animation.VisualClock;
import net.minecraft.core.BlockPos;
public final class NativeTrapdoorClockChecks {
 public static void main(String[] args){
  Object world=new Object();BlockPos owner=new BlockPos(4,60,2);VisualClock clock=new VisualClock();long nanos=1_000_000_000L;double now=clock.sample(nanos,false);need(NativeTrapdoorVisualClock.sample(world,owner,false,now)==0,"initial closed");need(NativeTrapdoorVisualClock.sample(world,owner,true,now)==0,"new observation");
  now=clock.sample(nanos+225_000_000L,false);double half=NativeTrapdoorVisualClock.sample(world,owner,true,now);need(half==.5,"nine tick half");for(int i=0;i<20;i++)need(NativeTrapdoorVisualClock.sample(world,new BlockPos(4,60,2),true,now)==half,"replacement/repeated packets");
  double paused=clock.sample(nanos+10_000_000_000L,true);need(NativeTrapdoorVisualClock.sample(world,owner,true,paused)==half,"pause");now=clock.sample(nanos+20_000_000_000L,false);need(NativeTrapdoorVisualClock.sample(world,owner,false,now)==half,"resume reversal continuity");now=clock.sample(nanos+20_225_000_000L,false);need(NativeTrapdoorVisualClock.sample(world,owner,false,now)==.25,"reversal easing");
  Object other=new Object();need(NativeTrapdoorVisualClock.sample(other,owner,true,now)==1,"world isolation");for(int i=0;i<1100;i++)NativeTrapdoorVisualClock.sample(world,new BlockPos(i,0,0),false,now);need(NativeTrapdoorVisualClock.retained(world)==1024,"bounded world cache");NativeTrapdoorVisualClock.clear(world);need(NativeTrapdoorVisualClock.retained(world)==0,"unload");NativeTrapdoorVisualClock.clear(other);
  System.out.println("[vandorlabs][trapdoor-clock] CPU PASS nine ticks/repeats/replacement/pause/reversal/world isolation/bounded cache/unload");
 }
 private static void need(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
