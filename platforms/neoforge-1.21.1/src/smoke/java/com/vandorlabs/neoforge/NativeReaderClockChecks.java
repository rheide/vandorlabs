package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import net.minecraft.core.BlockPos;
public final class NativeReaderClockChecks {
 static void need(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  var visual=new NativeTokenReader.Visual(false,1,1,true,15,true);var motion=new NativeTokenReader.Motion(4,6,true,12,200);var state=new NativeReaderVisualClock.State();
  state.observe(100,visual,motion);need(state.age()==6&&state.rejected(),"initial packet age/rejection");state.observe(104,visual,motion);need(state.age()==10&&state.rejected(),"repeated packet cannot restart insertion");state.observe(112,visual,motion);need(state.age()==18&&!state.rejected(),"repeated rejection expires locally");
  state.observe(112,visual,new NativeTokenReader.Motion(4,0,true,12,201));need(state.age()==18&&state.rejected(),"fresh rejection restarts without insertion restart");state.observe(124,visual,new NativeTokenReader.Motion(4,0,true,12,201));need(state.age()==20&&!state.rejected(),"new expiry repeated packet dedupe");
  state.observe(124,visual,new NativeTokenReader.Motion(5,5,true,0,201));need(state.age()==0,"new insertion sequence restarts locally");
  Object world=new Object();var first=NativeReaderVisualClock.sampleAt(world,BlockPos.ZERO,0,visual,motion,10);var replacement=NativeReaderVisualClock.sampleAt(world,BlockPos.ZERO,0,new NativeTokenReader.Visual(false,1,1,true,15,true),motion,12);need(first==replacement&&replacement.age()==8,"owner position survives replaced immutable snapshots");NativeReaderVisualClock.clear(world);need(NativeReaderVisualClock.sampleAt(world,BlockPos.ZERO,0,visual,motion,12)!=first,"world cache clears");
  need(com.vandorlabs.render.ReaderVisualMesh.insertionOffset(0,false)==.6&&com.vandorlabs.render.ReaderVisualMesh.insertionOffset(8,false)==0&&com.vandorlabs.render.ReaderVisualMesh.insertionOffset(20,true)==.6,"source smooth insertion/return endpoints");
  var clock=new com.vandorlabs.animation.VisualClock();need(clock.sample(0,false)==0&&clock.sample(50000000,false)==1&&clock.sample(100000000,true)==1&&clock.sample(1000000000,true)==1&&clock.sample(1050000000,false)==1&&clock.sample(1100000000,false)==2,"pause/resume sample clock");
  System.out.println("[vandorlabs][reader-clock] CPU PASS repeated snapshots/rejections, new sequence, return endpoints, owner-position replacement, world unload and pause/resume");
 }
}
