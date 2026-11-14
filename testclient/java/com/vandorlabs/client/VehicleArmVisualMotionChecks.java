package com.vandorlabs.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.physics.RigidTransform;

/** Actual visual orientation accumulator, independent of server ticks and mutable tile replacement. */
public final class VehicleArmVisualMotionChecks {
 private static int checks;
 private static void need(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 private static double angle(RigidTransform pose){return Math.toDegrees(Math.atan2(pose.r[3],pose.r[0]));}
 private static void same(RigidTransform a,RigidTransform b,String why){for(int i=0;i<9;i++)need(Math.abs(a.r[i]-b.r[i])<1e-8,why);}
 public static void main(String[] args){
  for(double interval:new double[]{1,3,4,12}){
   VehicleArmVisualMotion.Pose pose=new VehicleArmVisualMotion.Pose();double previous=0;
   for(int frame=0;frame<=25*interval*8;frame++){
    double now=frame/8D,authority=Math.min(60,Math.floor(now/interval)*2.5);RigidTransform target=RigidTransform.rotation(0,0,1,authority),sample=pose.sample(target,now);double actual=angle(sample);
    need(actual>=previous-1e-8&&actual<=authority+1e-8,"20/5TPS delayed packets retain continuous bounded orientation");same(sample,pose.sample(target,now),"repeated same-clock support cannot restart motion");previous=actual;
   }
   double now=25*interval;RigidTransform held=pose.sample(RigidTransform.rotation(0,0,1,60),now),reversed=pose.sample(RigidTransform.rotation(0,0,1,-25),now);same(held,reversed,"opposite correction starts at the existing visual orientation");need(Math.abs(angle(pose.sample(RigidTransform.rotation(0,0,1,-25),now+20))+25)<1e-8,"reversed pose settles to actual authority");
  }
  VisualClock clock=new VisualClock();need(clock.sample(0,false)==0&&clock.sample(50000000L,false)==1,"client clock advances before pause");double stopped=clock.sample(100000000L,true);VehicleArmVisualMotion.Pose paused=new VehicleArmVisualMotion.Pose();paused.sample(RigidTransform.IDENTITY,0);RigidTransform original=paused.sample(RigidTransform.rotation(1,2,3,73),stopped);for(long nanos:new long[]{1000000000L,10000000000L})same(original,paused.sample(RigidTransform.rotation(1,2,3,73),clock.sample(nanos,true)),"paused client clock freezes arbitrary-axis pose");need(clock.sample(11000000000L,false)==stopped,"resume does not consume paused wall time");same(original,paused.sample(RigidTransform.rotation(1,2,3,73),stopped),"resume retains current image");RigidTransform halfway=paused.sample(RigidTransform.rotation(1,2,3,73),stopped+.5);need(Math.abs(halfway.angle()-36.5)<1e-6,"arbitrary-axis proper motion continues after resume");
  for(double x:new double[]{-1,0,1})for(double y:new double[]{-1,0,1})for(double z:new double[]{-1,0,1})if(x*x+y*y+z*z>0){RigidTransform end=RigidTransform.rotation(x,y,z,180);same(RigidTransform.interpolate(RigidTransform.IDENTITY,end,1),end,"antipodal-axis endpoint preserves the exact proper rotation");RigidTransform half=RigidTransform.interpolate(RigidTransform.IDENTITY,end,.5);need(Math.abs(half.angle()-90)<1e-6,"antipodal-axis midpoint remains proper");}
  java.util.List<net.minecraft.util.math.BlockPos> labels=java.util.Collections.singletonList(new net.minecraft.util.math.BlockPos(1,2,3));VehicleArmVisualMotion.Session before=new VehicleArmVisualMotion.Session(3,new Object(),labels,labels,null,new double[]{Double.NaN});before.poses[0].sample(RigidTransform.IDENTITY,0);before.poses[0].sample(RigidTransform.rotation(1,2,3,73),1);RigidTransform moving=before.poses[0].sample(RigidTransform.rotation(1,2,3,73),1.5);VehicleArmVisualMotion.Session after=new VehicleArmVisualMotion.Session(3,new Object(),labels,labels,before,new double[]{Double.NaN});same(moving,after.poses[0].sample(RigidTransform.rotation(1,2,3,73),1.5),"carried view/tile replacement retains visual state by world/owner/carrier");need(after.poses[0]==before.poses[0]&&after.used==0,"replacement invalidates fitted frame results while retaining owned scalar visual history");
  System.out.println("Coupled arm visual clock PASS checks="+checks+" 20/5TPS 1/3/4/12tick delayed/repeated support pause/resume/reversal arbitrary-axis and antipodal proper interpolation");
 }
}
