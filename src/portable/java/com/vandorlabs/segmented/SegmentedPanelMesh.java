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
  build(window,shape,layout,column,row,width,height,wc,wr,ww,wh,glassLayout,opaqueGlass,pass,body,pane,edge,out,window?WindowOutline.localSlice(ww,wh,shape,0,wc,wr):null);
 }
 public static void build(boolean window,int shape,int layout,int column,int row,int width,int height,int wc,int wr,int ww,int wh,int glassLayout,boolean opaqueGlass,int pass,Surface body,Surface pane,Texture edge,Sink out,WindowOutline.Slice slice){
  buildLayered(window,shape,layout,column,row,width,height,wc,wr,ww,wh,glassLayout,opaqueGlass,pass,body,pane,edge,out,slice,false,3);
 }
 /** Components: body/edges=1, window pane=2. Translucent image faces keep opaque edge geometry separate. */
 public static void buildLayered(boolean window,int shape,int layout,int column,int row,int width,int height,int wc,int wr,int ww,int wh,int glassLayout,boolean opaqueGlass,int pass,Surface body,Surface pane,Texture edge,Sink out,WindowOutline.Slice slice,boolean translucentBody,int components){
  if((components&~3)!=0)throw new IllegalArgumentException("Segmented layer components");
  if((components&1)!=0){if(pass==(translucentBody?1:0)){List<double[]> panels=window?slice.frameQuads:Collections.singletonList(new double[]{0,0,16,0,16,16,0,16});for(double[] q:panels){front(out,q,SegmentedDoorGeometry.HALF_THICKNESS*16,1,layout,column,row,width,height,body);front(out,q,-SegmentedDoorGeometry.HALF_THICKNESS*16,-1,layout,column,row,width,height,body);}}if(pass==0)edges(out,slice,edge);}
  if((components&2)!=0&&window&&(pass==1&&!opaqueGlass||pass==0&&opaqueGlass))for(double[] q:slice.glassQuads)front(out,q,0,1,glassLayout,wc,wh-1-wr,ww,wh,pane);
 }

 /** Authored four-window aluminum frame; image alpha never writes the metal's depth. */
 public static void spiral(int pass,int components,int glassLayout,boolean opaqueGlass,Surface body,Surface pane,Texture edge,Sink out){
  double left=5D/8,right=122D/8;double[][] openings={{left,.5,right,19D/8},{left,35D/8,right,51D/8},{left,67D/8,right,82D/8},{left,98D/8,right,113D/8}};
  if(pass==0&&(components&1)!=0){
   double y=0;for(double[] box:openings){rectangle(out,0,y,16,box[1],body);rectangle(out,0,box[1],left,box[3],body);rectangle(out,right,box[1],16,box[3],body);y=box[3];
    edge(out,box[0],box[1],box[2],box[1],edge);edge(out,box[2],box[1],box[2],box[3],edge);edge(out,box[2],box[3],box[0],box[3],edge);edge(out,box[0],box[3],box[0],box[1],edge);
   }rectangle(out,0,y,16,16,body);edges(out,null,edge);
  }
  if((components&2)!=0&&pass==(opaqueGlass?0:1))for(double[] box:openings)front(out,new double[]{box[0],box[1],box[2],box[1],box[2],box[3],box[0],box[3]},0,1,glassLayout,0,0,1,1,pane);
 }
 private static void rectangle(Sink out,double x0,double y0,double x1,double y1,Surface body){if(x1<=x0||y1<=y0)return;double[] q={x0,y0,x1,y0,x1,y1,x0,y1};for(int side:new int[]{-1,1})front(out,q,side*SegmentedDoorGeometry.HALF_THICKNESS*16,side,0,0,0,1,1,body);}
 /** Fast rectangular sections share the exact Tile transform and clipping used by window frames. */
 public static void nativeBody(int row,int height,int column,boolean endpoint,Surface body,Texture edge,Sink out){
  nativeCell(!endpoint||row==0,!endpoint||row==height-1,column,row,body,edge,out);
 }
 public static void nativeCell(boolean top,boolean bottom,int column,int row,Surface body,Texture edge,Sink out){nativeCellPhase(top,bottom,tilePhase(column,row,body.door?.5:body.aspect),body,edge,out);}
 public static void nativeCellPhase(boolean top,boolean bottom,long phase,Surface body,Texture edge,Sink out){
  if(bottom)outer(out,2,0,16,edge);
  if(top)outer(out,3,0,16,edge);
  double[] q={0,0,16,0,16,16,0,16};double depth=SegmentedDoorGeometry.HALF_THICKNESS*16;
  front(out,q,-depth,-1,TextureLayoutMode.TILE,0,0,1,1,body,true,phase);
  front(out,q,depth,1,TextureLayoutMode.TILE,0,0,1,1,body,true,phase);
  outer(out,0,0,16,edge);outer(out,1,0,16,edge);
 }
 private static void front(Sink out,double[] q,double depth,int side,int mode,int column,int row,int width,int height,Surface surface){front(out,q,depth,side,mode,column,row,width,height,surface,false);}
 private static void front(Sink out,double[] q,double depth,int side,int mode,int column,int row,int width,int height,Surface surface,boolean rectangle){front(out,q,depth,side,mode,column,row,width,height,surface,rectangle,tilePhase(column,row,surface.door?.5:surface.aspect));}
 private static void front(Sink out,double[] q,double depth,int side,int mode,int column,int row,int width,int height,Surface surface,boolean rectangle,long phase){
  double phaseU=Float.intBitsToFloat((int)(phase>>>32)),phaseV=Float.intBitsToFloat((int)phase);
  double aspect=surface.door?.5:surface.aspect;boolean fit=TextureLayoutMode.fit(mode,width,height,aspect);List<double[]> polygon=new ArrayList<>();double uMin=Double.POSITIVE_INFINITY,uMax=Double.NEGATIVE_INFINITY,vMin=Double.POSITIVE_INFINITY,vMax=Double.NEGATIVE_INFINITY;
  for(int n=0;n<4;n++){int i=side>0?n:3-n;double x=q[i*2],y=q[i*2+1],u=fit?(column+x/16)/width:phaseU+TextureLayoutMode.tileU(x/16,aspect),v=fit?(row+1-y/16)/height:phaseV+TextureLayoutMode.tileV(1-y/16,aspect);polygon.add(new double[]{x,y,depth,u,v});uMin=Math.min(uMin,u);uMax=Math.max(uMax,u);vMin=Math.min(vMin,v);vMax=Math.max(vMax,v);}
  int u0=fit?0:(int)Math.floor(uMin+1e-8),u1=fit?0:(int)Math.ceil(uMax-1e-8)-1,v0=fit?0:(int)Math.floor(vMin+1e-8),v1=fit?0:(int)Math.ceil(vMax-1e-8)-1;
  for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++)for(int half=0;half<(surface.door?2:1);half++){
   double low=v+(surface.door?half*.5:0),high=v+(surface.door?(half+1)*.5:1);List<double[]> clipped=clip(clip(clip(clip(polygon,3,u,true),3,u+1,false),4,low,true),4,high,false);Texture image=surface.door&&half==0?surface.upper:surface.lower;
   if(rectangle&&clipped.size()==4){for(double[] p:clipped)mapped(out,p,image,u,low,high,side);}
   else for(int i=1;i+1<clipped.size();i++)for(double[] p:new double[][]{clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1)})mapped(out,p,image,u,low,high,side);
  }
 }
 private static void mapped(Sink out,double[] p,Texture image,int u,double low,double high,int side){out.vertex(p[0],p[1],p[2],image.u(Math.max(0,Math.min(1,p[3]-u))*16),image.v(Math.max(0,Math.min(1,(p[4]-low)/(high-low)))*16),0,0,side);}
 /** Texture phase is independent of complete-door dimensions and window membership. */
 public static long tilePhase(int column,int row,double aspect){float u=(float)(TextureLayoutMode.tileU(column,aspect)-Math.floor(TextureLayoutMode.tileU(column,aspect))),v=(float)(TextureLayoutMode.tileV(row,aspect)-Math.floor(TextureLayoutMode.tileV(row,aspect)));return((long)Float.floatToRawIntBits(u)<<32)|(Float.floatToRawIntBits(v)&0xffffffffL);}
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
