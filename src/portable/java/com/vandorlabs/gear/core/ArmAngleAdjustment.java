package com.vandorlabs.gear.core;
/** Immutable scalar start-adjustment state. Geometry, collision, journaling and publication stay in the host. */
public final class ArmAngleAdjustment {
 public final double physicalStart,physicalEnd,savedEnd;
 public final boolean adjusting;
 private ArmAngleAdjustment(double start,double end,boolean adjusting,double savedEnd){
  if(!Double.isFinite(start)||!Double.isFinite(end)||!Double.isFinite(savedEnd)||Math.abs(start)>180||Math.abs(end)>180||Math.abs(savedEnd)>180)throw new IllegalArgumentException("Invalid retained angle adjustment");
  physicalStart=start;physicalEnd=end;this.adjusting=adjusting;this.savedEnd=savedEnd;
 }
 public static ArmAngleAdjustment idle(double start,double end){return new ArmAngleAdjustment(start,end,false,end);}
 /** Read the existing Source adjustment fields without normalizing the timeline or its phase. */
 public static ArmAngleAdjustment retained(double start,double end,boolean adjusting,double savedEnd){return new ArmAngleAdjustment(start,end,adjusting,savedEnd);}
 public double configuredStart(){return adjusting?physicalEnd:physicalStart;}
 public double configuredEnd(){return adjusting?savedEnd:physicalEnd;}
 /** Begin only at the physical Start. Temporary End becomes the requested Start; moving to phase zero is a normal admitted stroke. */
 public ArmAngleAdjustment begin(double requestedStart,double requestedEnd,double phase,boolean target){
  ArmLandingAngles.step(requestedStart);ArmLandingAngles.step(requestedEnd);
  if(adjusting||phase!=1||!target)throw new IllegalArgumentException("Return the Rotating Arm to Start before changing its starting angle");
  return requestedStart==physicalStart?idle(physicalStart,requestedEnd):new ArmAngleAdjustment(physicalStart,requestedStart,true,requestedEnd);
 }
 /** A refused start-adjustment returns to its original phase-one pose through the host's normal collision path. */
 public Completion complete(double phase,boolean target){
  if(!Double.isFinite(phase)||phase<0||phase>1)throw new IllegalArgumentException("Invalid angle adjustment phase");
  if(!adjusting)return new Completion(this,phase,target,false);
  if(phase==0)return new Completion(idle(physicalEnd,savedEnd),1,true,true);
  if(phase==1&&target)return new Completion(idle(physicalStart,savedEnd),1,true,true);
  return new Completion(this,phase,target,false);
 }
 public static final class Completion {
  public final ArmAngleAdjustment angles;public final double phase;public final boolean target,finished;
  private Completion(ArmAngleAdjustment angles,double phase,boolean target,boolean finished){this.angles=angles;this.phase=phase;this.target=target;this.finished=finished;}
 }
}
