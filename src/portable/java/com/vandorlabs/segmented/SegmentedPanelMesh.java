package com.vandorlabs.segmented;

import com.vandorlabs.surface.WindowOutline;
import com.vandorlabs.render.TextureLayoutMode;
import java.util.*;

/** Released panel/window topology and UV clipping, independent of Minecraft atlas/vertex APIs. */
public final class SegmentedPanelMesh {
 private SegmentedPanelMesh(){}
 public interface Texture {float u(double pixel);float v(double pixel);}
 public interface Sink {void vertex(double x,double y,double z,float u,float v,float nx,float ny,float nz);}
 public static final class Surface {
  public final Texture lower,upper;public final double aspect;public final boolean door;
  public Surface(Texture lower,Texture upper,double aspect,boolean door){this.lower=lower;this.upper=upper;this.aspect=aspect;this.door=door;}
 }
 public static void build(boolean window,int shape,int layout,int column,int row,int width,int height,int wc,int wr,int ww,int wh,int glassLayout,boolean opaqueGlass,int pass,Surface body,Surface pane,Texture edge,Sink out){
  WindowOutline.Slice slice=window?WindowOutline.localSlice(ww,wh,shape,0,wc,wr):null;
  if(pass==0){List<double[]> panels=window?slice.frameQuads:Collections.singletonList(new double[]{0,0,16,0,16,16,0,16});for(double[] q:panels){front(out,q,SegmentedDoorGeometry.HALF_THICKNESS*16,1,layout,column,row,width,height,body);front(out,q,-SegmentedDoorGeometry.HALF_THICKNESS*16,-1,layout,column,row,width,height,body);}edges(out,slice,edge);}
  if(window&&(pass==1&&!opaqueGlass||pass==0&&opaqueGlass))for(double[] q:slice.glassQuads)front(out,q,0,1,glassLayout,wc,wh-1-wr,ww,wh,pane);
 }
 private static void front(Sink out,double[] q,double depth,int side,int mode,int column,int row,int width,int height,Surface surface){
  double aspect=surface.door?.5:surface.aspect;boolean fit=TextureLayoutMode.fit(mode,width,height,aspect);List<double[]> polygon=new ArrayList<>();double uMin=Double.POSITIVE_INFINITY,uMax=Double.NEGATIVE_INFINITY,vMin=Double.POSITIVE_INFINITY,vMax=Double.NEGATIVE_INFINITY;
  for(int n=0;n<4;n++){int i=side>0?n:3-n;double x=q[i*2],y=q[i*2+1],u=fit?(column+x/16)/width:TextureLayoutMode.tileU(column+x/16,aspect),v=fit?(row+1-y/16)/height:TextureLayoutMode.tileV(row+1-y/16,aspect);polygon.add(new double[]{x,y,depth,u,v});uMin=Math.min(uMin,u);uMax=Math.max(uMax,u);vMin=Math.min(vMin,v);vMax=Math.max(vMax,v);}
  int u0=fit?0:(int)Math.floor(uMin+1e-8),u1=fit?0:(int)Math.ceil(uMax-1e-8)-1,v0=fit?0:(int)Math.floor(vMin+1e-8),v1=fit?0:(int)Math.ceil(vMax-1e-8)-1;
  for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++)for(int half=0;half<(surface.door?2:1);half++){
   double low=v+(surface.door?half*.5:0),high=v+(surface.door?(half+1)*.5:1);List<double[]> clipped=clip(clip(clip(clip(polygon,3,u,true),3,u+1,false),4,low,true),4,high,false);Texture image=surface.door&&half==0?surface.upper:surface.lower;
   for(int i=1;i+1<clipped.size();i++)for(double[] p:new double[][]{clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1)})out.vertex(p[0],p[1],p[2],image.u(Math.max(0,Math.min(1,p[3]-u))*16),image.v(Math.max(0,Math.min(1,(p[4]-low)/(high-low)))*16),0,0,side);
  }
 }
 private static List<double[]> clip(List<double[]> polygon,int axis,double value,boolean greater){return com.vandorlabs.render.PanelTextureMapping.clip(polygon,axis,value,greater);}
 private static void edges(Sink out,WindowOutline.Slice slice,Texture image){
  if(slice!=null)for(double[] e:slice.hexEdges)edge(out,e[2],e[3],e[0],e[1],image);
  for(int side=0;side<4;side++){int axis=side<2?0:1;double boundary=(side&1)==0?0:16;double[] opening=slice==null?null:slice.edgeOpening(axis,boundary);double lo=opening==null?16:opening[0],hi=opening==null?16:opening[1];outer(out,side,0,lo,image);if(hi<16)outer(out,side,hi,16,image);}
 }
 private static void outer(Sink out,int side,double lo,double hi,Texture image){if(hi-lo<1e-7)return;if(side==0)edge(out,0,hi,0,lo,image);if(side==1)edge(out,16,lo,16,hi,image);if(side==2)edge(out,lo,0,hi,0,image);if(side==3)edge(out,hi,16,lo,16,image);}
 private static void edge(Sink out,double ax,double ay,double bx,double by,Texture image){
  double length=Math.hypot(bx-ax,by-ay);if(length<1e-8)return;double h=SegmentedDoorGeometry.HALF_THICKNESS*16;
  for(double start=0;start<length;start+=16){double end=Math.min(length,start+16),x0=ax+(bx-ax)*start/length,y0=ay+(by-ay)*start/length,x1=ax+(bx-ax)*end/length,y1=ay+(by-ay)*end/length;double[][] points={{x0,y0,-h},{x1,y1,-h},{x1,y1,h},{x0,y0,h}};
   for(int i=0;i<4;i++)out.vertex(points[i][0],points[i][1],points[i][2],image.u(i==1||i==2?end-start:0),image.v(i<2?2*h:0),(float)((by-ay)/length),(float)((ax-bx)/length),0);
  }
 }
}
