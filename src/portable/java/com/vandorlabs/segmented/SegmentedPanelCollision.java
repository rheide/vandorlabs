package com.vandorlabs.segmented;

import java.util.*;

/** Cold, world-free collision preparation. Angled surfaces use at most one-pixel slices. */
public final class SegmentedPanelCollision {
 public static final class Builder {
  private final List<double[]> boxes=new ArrayList<>();
  public void add(double[] next){
   if(!boxes.isEmpty()){double[] old=boxes.get(boxes.size()-1);for(int axis=0;axis<3;axis++){boolean same=true;for(int a=0;a<3;a++)if(a!=axis&&(Math.abs(old[a]-next[a])>1e-8||Math.abs(old[a+3]-next[a+3])>1e-8))same=false;if(same&&next[axis]<=old[axis+3]+1e-8&&old[axis]<=next[axis+3]+1e-8){old[axis]=Math.min(old[axis],next[axis]);old[axis+3]=Math.max(old[axis+3],next[axis+3]);return;}}}
   if(boxes.size()>=512)throw new IllegalArgumentException("Segmented collision capacity");boxes.add(next);
  }
  public List<double[]> boxes(){return Collections.unmodifiableList(boxes);}
 }
 public static void append(Builder output,int x,int y,int z,int rotation,int row,int height,int column,int width,double progress,int direction,boolean straight,int pixels,double depth){
  if(!SegmentedTreads.valid(pixels))throw new IllegalArgumentException("Invalid collision tread");boolean articulated=pixels<16&&!straight&&progress>0;int count=articulated?16/pixels:1;
  double[] descriptor=new double[14],pose=new double[6],point=new double[6];float[] input=new float[8];if(articulated)SegmentedTreads.describe(descriptor,row,height,column,width,progress,direction,pixels,depth);
  for(int strip=0;strip<count;strip++){
   if(articulated)SegmentedTreads.pose(descriptor,strip,pose);else{SegmentedDoorGeometry.panel(row,height,column,width,progress,direction,straight,pose);pose[2]+=depth;}
   double low=articulated?strip*pixels:0,high=articulated?(strip+1)*pixels:16;int steps=Math.abs(pose[3]*pose[4])<1e-9?1:(int)(high-low);
   for(int step=0;step<steps;step++){double lo=low+(high-low)*step/steps,hi=low+(high-low)*(step+1)/steps;double[] box={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
    for(int corner=0;corner<8;corner++){input[0]=(float)(direction<2?((corner&1)==0?0:16):((corner&1)==0?lo:hi));input[1]=(float)(direction<2?((corner&2)==0?lo:hi):((corner&2)==0?0:16));input[2]=(corner&4)==0?-1:1;SegmentedPanelTransform.vertex(input,0,pose,rotation,x,y,z,point);for(int a=0;a<3;a++){box[a]=Math.min(box[a],point[a]);box[a+3]=Math.max(box[a+3],point[a]);}}
    output.add(box);
   }
  }
 }
 private SegmentedPanelCollision(){}
}
