package com.vandorlabs.gear.core;
/** Independent scalar oracle for the Source temporary-End adjustment, including blocked reversal and reload. */
public final class ArmAngleAdjustmentChecks {
 private static int checks;
 private static void same(double expected,double actual){checks++;if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))throw new AssertionError(expected+" != "+actual);}
 private static void yes(boolean value){checks++;if(!value)throw new AssertionError();}
 public static void main(String[] args){
  for(int start=-180;start<=180;start+=15)for(int end=-180;end<=180;end+=15)for(int next=-180;next<=180;next+=15){
   ArmAngleAdjustment base=ArmAngleAdjustment.idle(start,end),stroke=base.begin(next,end,1,true);
   same(start,stroke.physicalStart);same(next==start?end:next,stroke.physicalEnd);same(next,stroke.configuredStart());same(end,stroke.configuredEnd());
   for(int i=0;i<=16;i++){
    double phase=i/16.0;ArmAngleAdjustment restored=ArmAngleAdjustment.retained(stroke.physicalStart,stroke.physicalEnd,stroke.adjusting,stroke.savedEnd);
    same(stroke.configuredStart(),restored.configuredStart());same(stroke.configuredEnd(),restored.configuredEnd());
    ArmAngleAdjustment.Completion partial=restored.complete(phase,false);
    if(next!=start&&i==0){yes(partial.finished);same(next,partial.angles.physicalStart);same(end,partial.angles.physicalEnd);same(1,partial.phase);yes(partial.target);}
    else {yes(!partial.finished);same(phase,partial.phase);}
   }
   ArmAngleAdjustment.Completion rolledBack=stroke.complete(1,true);same(start,rolledBack.angles.physicalStart);same(end,rolledBack.angles.physicalEnd);same(1,rolledBack.phase);yes(rolledBack.target);
  }
  ArmAngleAdjustment state=ArmAngleAdjustment.idle(0,90);
  for(double phase:new double[]{0,.25,.5,.75})try{state.begin(15,90,phase,true);throw new AssertionError("Non-start edit admitted");}catch(IllegalArgumentException expected){checks++;}
  try{state.begin(15,90,1,false);throw new AssertionError("Leaving-start edit admitted");}catch(IllegalArgumentException expected){checks++;}
  try{state.begin(15,90,1,true).begin(30,90,1,true);throw new AssertionError("In-flight edit admitted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("arm-angle-adjustment PASS profiles=15625 checks="+checks);
 }
}
