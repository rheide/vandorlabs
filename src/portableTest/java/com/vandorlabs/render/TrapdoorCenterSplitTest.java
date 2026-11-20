package com.vandorlabs.render;

/** Odd groups split their middle material cell, including collision and ray snapshots. */
public final class TrapdoorCenterSplitTest {
 private static void need(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  int cases=0;
  for(int width:new int[]{3,5,7})for(int position=0;position<3;position++)for(boolean sliding:new boolean[]{false,true})for(int turns=0;turns<4;turns++){
   TrapdoorGroupMotion motion=new TrapdoorGroupMotion(0,width/2,width);
   need(Math.abs(motion.travel-(width/2D-1D/16))<1e-12,"geometric half-span travel");
   TrapdoorLeafPose pose=new TrapdoorLeafPose(false,position,turns,false,sliding,false,false,false,false,false,1,motion.hinge,motion.travel,1D/16,true);
   for(double phase:new double[]{0,.25,.5,1}){
    double[][] a=new double[8][3],b=new double[8][3];pose.piece(0).write(phase,false,a);pose.piece(1).write(phase,false,b);
    int axis=(turns&1)==0?2:0;
    double alo=Double.POSITIVE_INFINITY,ahi=Double.NEGATIVE_INFINITY,blo=alo,bhi=ahi;
    for(int v=0;v<8;v++){alo=Math.min(alo,a[v][axis]);ahi=Math.max(ahi,a[v][axis]);blo=Math.min(blo,b[v][axis]);bhi=Math.max(bhi,b[v][axis]);}
    need(Math.abs(alo+bhi-1)<1e-12&&Math.abs(ahi+blo-1)<1e-12,"two halves mirror around cell centre through motion");
   }
   TrapdoorLeafSnapshot open=new TrapdoorLeafSnapshot(pose,true);
   need(open.collisionCount()==2,"two physical leaves, no phantom box across their gap");
   double[] hit=new double[4];
   need(!open.trace(false,0,0,0,.5,-10,.5,.5,10,.5,hit),"open centre has no phantom ray target");
   cases++;
  }
  System.out.println("Trapdoor true-centre split PASS cases="+cases+" mirrored animation, fractional travel, two collision leaves and centre ray clearance");
 }
}
