package com.vandorlabs.segmented;

import java.util.*;
import com.vandorlabs.render.PanelTextureMapping;

/** Cold strip clipping and allocation-free small-panel motion. Coordinates remain in authored pixels. */
public final class SegmentedTreads {
 private SegmentedTreads(){}
 public static boolean valid(int pixels){return pixels==1||pixels==2||pixels==4||pixels==8||pixels==16;}
 public static void describe(double[] pose,int row,int height,int column,int width,double progress,int direction,int pixels,double depth){
  if(pose.length<14)return;pose[6]=row;pose[7]=height;pose[8]=column;pose[9]=width;pose[10]=progress;pose[11]=direction;pose[12]=pixels;pose[13]=depth;
 }
 public static float[] split(float[] vertices,int axis,int pixels){
  if(!valid(pixels)||axis<0||axis>1||vertices.length%32!=0)throw new IllegalArgumentException("Invalid tread mesh");if(pixels==16)return vertices;
  List<Float> result=new ArrayList<>();for(int at=0;at<vertices.length;at+=32){
   List<double[]> quad=new ArrayList<>();for(int v=0;v<4;v++){double[] p=new double[8];for(int k=0;k<8;k++)p[k]=vertices[at+v*8+k];quad.add(p);}
   for(int low=0;low<16;low+=pixels){List<double[]> clipped=PanelTextureMapping.clip(PanelTextureMapping.clip(quad,axis,low,true),axis,low+pixels,false);if(clipped.size()<3)continue;
    if(clipped.size()==4)append(result,clipped.get(0),clipped.get(1),clipped.get(2),clipped.get(3));else for(int i=1;i+1<clipped.size();i++)append(result,clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1));
   }
  }float[] data=new float[result.size()];for(int i=0;i<data.length;i++)data[i]=result.get(i);return data;
 }
 private static void append(List<Float> out,double[]... points){for(double[] p:points)for(double value:p)out.add((float)value);}
 /** Strip uses the original cell coordinate: only its pivot and path change. */
 public static void pose(double[] descriptor,int strip,double[] out){
  int direction=(int)descriptor[11],pixels=(int)descriptor[12],count=16/pixels;double size=pixels/16D;boolean vertical=direction<2;int row=(int)descriptor[6],column=(int)descriptor[8],span=(int)(vertical?descriptor[7]:descriptor[9]);
  int cell=direction==0?row:direction==1?(int)descriptor[7]-1-row:direction==2?column:(int)descriptor[9]-1-column;
  int leading=direction==0?count-1-strip:direction==1?strip:direction==2?strip:count-1-strip;double sign=direction==1||direction==2?-1:1;
  double start=1-size/2,travel=Math.max(0,Math.min(1,descriptor[10]))*(span+SegmentedDoorGeometry.ARC-size/2),t=start-(cell*count+leading)*size+travel,along,depth,sin,cos;
  if(t<=start){along=t;depth=.5;sin=0;cos=1;}else if(t<start+SegmentedDoorGeometry.ARC){double angle=(t-start)/SegmentedDoorGeometry.TURN;sin=Math.sin(angle);cos=Math.cos(angle);along=start+SegmentedDoorGeometry.TURN*sin;depth=.5+SegmentedDoorGeometry.TURN*(1-cos);}else{along=start+SegmentedDoorGeometry.TURN;depth=1+t-start-SegmentedDoorGeometry.ARC;sin=1;cos=0;}
  double delta=along-(start-(cell*count+leading)*size),center=(strip+.5)*size,offset=center-.5;
  out[0]=vertical?.5:center+sign*delta;out[1]=vertical?.5-row+offset+sign*delta:.5-row;out[2]=depth+descriptor[13];out[3]=sin*sign;out[4]=cos;out[5]=vertical?1:0;
  // vertex() uses the original cell origin; remove the strip's local pivot after rotation.
  if(vertical){out[1]-=cos*offset;out[2]-=sin*sign*offset;}else{out[0]-=cos*offset;out[2]-=sin*sign*offset;}
 }
}
