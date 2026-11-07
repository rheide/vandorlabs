package com.vandorlabs.segmented;
import java.util.UUID;
public final class SegmentedGateJoinTest {
 private static int checks;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static SegmentedGateJoin.Cell cell(int kind,int facing,int position,int direction,boolean straight,boolean moving,UUID motion,double x,double y,double[] r,double[] u){return new SegmentedGateJoin.Cell(kind,facing,position,direction,straight,10,moving,motion,new SegmentedGateJoin.Frame(x*r[0]+y*u[0],x*r[1]+y*u[1],x*r[2]+y*u[2],r[0],r[1],r[2],u[0],u[1],u[2]));}
 public static void main(String[] args){UUID motion=new UUID(4,7);int profiles=0;
  for(int kind=0;kind<3;kind++)for(int facing=2;facing<=5;facing++)for(int position=0;position<3;position++)for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int pose=0;pose<3;pose++){
   double angle=pose*Math.PI/4,c=Math.cos(angle),s=Math.sin(angle);double[] r=direction<2?new double[]{1,0,0}:new double[]{c,0,s},u=direction<2?new double[]{0,c,s}:new double[]{0,1,0};
   SegmentedGateJoin.Cell center=cell(kind,facing,position,direction,straight,pose==1,motion,0,0,r,u),left=cell(kind,facing,position,direction,straight,pose==1,motion,-1,0,r,u),right=cell(kind,facing,position,direction,straight,pose==1,motion,1,0,r,u),below=cell(kind,facing,position,direction,straight,pose==1,motion,0,-1,r,u),above=cell(kind,facing,position,direction,straight,pose==1,motion,0,1,r,u);
   for(int mask=0;mask<16;mask++){int actual=SegmentedGateJoin.mask(center,(mask&1)!=0?left:null,(mask&2)!=0?right:null,(mask&4)!=0?below:null,(mask&8)!=0?above:null);need(actual==mask,"four-neighbour mask");need(SegmentedGateJoin.model(kind,mask).contains(SegmentedGateJoin.style(kind)),"stable model family");need(SegmentedGateJoin.model(kind,mask)==SegmentedGateJoin.model(kind,mask),"preindexed model names");profiles++;}
   need(SegmentedGateJoin.mask(center,cell((kind+1)%3,facing,position,direction,straight,pose==1,motion,-1,0,r,u),null,null,null)==0,"different style");
   need(SegmentedGateJoin.mask(center,cell(kind,facing==5?2:facing+1,position,direction,straight,pose==1,motion,-1,0,r,u),null,null,null)==0,"different block facing");
   need(SegmentedGateJoin.mask(center,new SegmentedGateJoin.Cell(kind,facing,position,direction,straight,20,pose==1,motion,left.frame),null,null,null)==0,"different mechanism speed");
   need(SegmentedGateJoin.mask(center,cell(kind,facing,(position+1)%3,direction,straight,pose==1,motion,-1,0,r,u),null,null,null)==0,"different authored plane");
   need(SegmentedGateJoin.mask(center,cell(kind,facing,position,(direction+1)%4,straight,pose==1,motion,-1,0,r,u),null,null,null)==0,"different mechanism");
   need(SegmentedGateJoin.mask(center,cell(kind,facing,position,direction,straight,pose==1,motion,-.99,0,r,u),null,null,null)==0,"separated moving edge");
   if(pose==1)need(SegmentedGateJoin.mask(center,cell(kind,facing,position,direction,straight,true,new UUID(9,2),-1,0,r,u),null,null,null)==0,"different active motion");
  }
  need(SegmentedGateJoin.variant(0).equals("single")&&SegmentedGateJoin.variant(1).equals("right")&&SegmentedGateJoin.variant(2).equals("left")&&SegmentedGateJoin.variant(3).equals("middle"),"authored horizontal variants");
  try{new SegmentedGateJoin.Frame(0,0,0,1,0,0,1,0,0);throw new AssertionError("parallel basis");}catch(IllegalArgumentException expected){checks++;}
  try{new SegmentedGateJoin.Frame(10000,0,0,1,0,0,0,1,0);throw new AssertionError("world coordinates retained");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("Segmented gate joins PASS profiles="+profiles+" checks="+checks);
 }
}
