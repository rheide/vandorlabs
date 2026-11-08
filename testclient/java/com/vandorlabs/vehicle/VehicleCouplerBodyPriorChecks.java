package com.vandorlabs.vehicle;

import com.vandorlabs.physics.*;
import java.lang.reflect.Method;

/** Actual Source approach/joint single-axis policy; not a live driver or combined-Euler equivalence claim. */
public final class VehicleCouplerBodyPriorChecks {
 private static int checks;
 private static void need(boolean b,String why){checks++;if(!b)throw new AssertionError("Source body prior: "+why);}
 private static void same(double[] a,double[] b,String why){for(int i=0;i<9;i++)need(Math.abs(a[i]-b[i])<1e-10,why+" matrix="+i);}
 public static void main(String[] args)throws Exception{
  Method approach=VehicleArticulation.class.getDeclaredMethod("approach",double.class,double.class,double.class);approach.setAccessible(true);double[] out=new double[9];int profiles=0;
  for(boolean pitch:new boolean[]{false,true})for(double previous:new double[]{-60,-20,0,30,60})for(double target:new double[]{-70,-15,0,45,70})for(double step:new double[]{0,1,4,12}){
   double next=((Double)approach.invoke(null,previous,target,step)).doubleValue();RigidTransform prior=pitch?RigidTransform.joint(0,0,0,1,0,0,previous):RigidTransform.joint(0,0,0,1,0,previous,0),desired=pitch?RigidTransform.joint(0,0,0,1,0,0,target):RigidTransform.joint(0,0,0,1,0,target,0),expected=pitch?RigidTransform.joint(0,0,0,1,0,0,next):RigidTransform.joint(0,0,0,1,0,next,0);double[] centre=prior.point(2,0,0),goal=desired.point(2,0,0);
   CouplerBodyPrior.Status status=CouplerBodyPrior.write(prior.r,centre[0]+11,centre[1]+97,centre[2]-17,11,97,-17,goal[0]+11,goal[1]+97,goal[2]-17,Math.toRadians(step),out);need(status==CouplerBodyPrior.Status.APPLIED||status==CouplerBodyPrior.Status.UNCHANGED,"finite Source single-axis target");same(out,expected.r,"actual Source bounded approach and joint basis");profiles++;
  }
  // Existing propose keeps the old WORLD centre's X/Z; only Y is supplied by hover/gravity/support.
  RigidTransform previousWorld=RigidTransform.translation(100,80,-50).then(RigidTransform.rotation(0,0,1,20)),newRoot=RigidTransform.translation(100.2,80.1,-50).then(RigidTransform.rotation(0,0,1,23));double[] localCentre={3,1,0},old=previousWorld.point(localCentre[0],localCentre[1],localCentre[2]),neutral=newRoot.point(localCentre[0],localCentre[1],localCentre[2]),pivot=newRoot.point(0,0,0);double desiredY=old[1]+(neutral[1]-old[1])*.35;
  CouplerBodyPrior.Status status=CouplerBodyPrior.write(newRoot.r,neutral[0],neutral[1],neutral[2],pivot[0],pivot[1],pivot[2],old[0],desiredY,old[2],Math.toRadians(4),out);need(status==CouplerBodyPrior.Status.APPLIED,"inertial old world centre under changed root produces correction");double[] actual={out[0]*localCentre[0]+out[1]*localCentre[1],out[3]*localCentre[0]+out[4]*localCentre[1],out[6]*localCentre[0]+out[7]*localCentre[1]},wanted={old[0]-pivot[0],desiredY-pivot[1],old[2]-pivot[2]};double a=Math.hypot(Math.hypot(actual[0],actual[1]),actual[2]),b=Math.hypot(Math.hypot(wanted[0],wanted[1]),wanted[2]);for(int i=0;i<3;i++)need(Math.abs(actual[i]/a-wanted[i]/b)<1e-9,"same Source inertial/hover desired direction when within step");
  double[] saved=out.clone();need(CouplerBodyPrior.write(saved,pivot[0],pivot[1],pivot[2],pivot[0],pivot[1],pivot[2],old[0],desiredY,old[2],Math.toRadians(4),out)==CouplerBodyPrior.Status.DEGENERATE,"centred lever has no fabricated heading");same(saved,out,"degenerate preserves current world orientation");
  System.out.println("Source Coupler body prior PASS profiles="+profiles+" checks="+checks+" actual private approach + joint bases, world-centre inertia/hover35%; no terrain/player or combined-Euler claim");
 }
 private VehicleCouplerBodyPriorChecks(){}
}
