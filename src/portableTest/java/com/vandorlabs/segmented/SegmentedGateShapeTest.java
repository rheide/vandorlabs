package com.vandorlabs.segmented;
import java.util.*;
public final class SegmentedGateShapeTest {
 private static int checks;private static void need(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static SegmentedGateShape.Element box(float x0,float y0,float z0,float x1,float y1,float z1){return new SegmentedGateShape.Element(new float[]{x0,y0,z0},new float[]{x1,y1,z1},-1,0,null,false,63);}
 public static void main(String[] args){
  SegmentedGateShape.Model post=new SegmentedGateShape.Model(0,new SegmentedGateShape.Element[]{box(2,1,7,3,15,9)});SegmentedGateShape.Hit scratch=new SegmentedGateShape.Hit();
  need(post.ray(2.5/16,8D/16,-1,2.5/16,8D/16,2,scratch)&&scratch.face==2&&Math.abs(scratch.z-7D/16)<1e-10,"authored post front ray");need(!post.ray(8D/16,8D/16,-1,8D/16,8D/16,2,scratch),"physical model gap");need(post.contains(2.5/16,8D/16,.5,scratch),"post volume");
  List<SegmentedGateShape.Connector> top=SegmentedGateShape.connectors(post,post,true,8),bottom=SegmentedGateShape.connectors(post,post,false,4);need(top.size()==1&&bottom.size()==1,"both vertical connectors");need(top.get(0).bounds.y0==15D/16&&top.get(0).bounds.y1==1&&bottom.get(0).bounds.y0==0&&bottom.get(0).bounds.y1==1D/16,"short boundary extensions only");need(post.element(0).bounds.y1==15D/16,"original geometry unchanged");
  SegmentedGateShape.Model corner=new SegmentedGateShape.Model(0,new SegmentedGateShape.Element[]{box(3,1,7,4,15,9)});need(SegmentedGateShape.connectors(post,corner,true,8).isEmpty(),"corner-only post contact refused");need(SegmentedGateShape.connectors(post,new SegmentedGateShape.Model(1,new SegmentedGateShape.Element[]{box(2,1,7,3,15,9)}),true,8).isEmpty(),"different style refused");need(SegmentedGateShape.connectors(post,post,true,0).isEmpty(),"absent prepared join refused");
  SegmentedGateShape.Model spear=new SegmentedGateShape.Model(0,new SegmentedGateShape.Element[]{box(2,1,7,3,13,9),box(0,13,7,16,14,9),box(2.2F,14,7.2F,2.8F,15,8.8F)});List<SegmentedGateShape.Connector> tips=SegmentedGateShape.connectors(spear,spear,true,8);need(tips.size()==1&&Math.abs(tips.get(0).bounds.x0-2.2F/16)<1e-9&&tips.get(0).bounds.y0==15D/16,"spear tip stays unscaled with matching narrow connector");
  SegmentedGateShape.Element diagonal=new SegmentedGateShape.Element(new float[]{2,7.5F,7},new float[]{14,8.5F,9},2,45,new float[]{8,8,8},false,63);SegmentedGateShape.Model tilted=new SegmentedGateShape.Model(2,new SegmentedGateShape.Element[]{diagonal});need(tilted.ray(.5,.5,-1,.5,.5,2,scratch),"rotated bar ray");need(!tilted.ray(.65,.35,-1,.65,.35,2,scratch),"rotated bar bounding-box void");
  for(int i=0;i<10000;i++)need(post.ray(2.5/16,8D/16,-1,2.5/16,8D/16,2,scratch),"reused ray scratch");
  System.out.println("Segmented gate shape PASS checks="+checks);
 }
}
