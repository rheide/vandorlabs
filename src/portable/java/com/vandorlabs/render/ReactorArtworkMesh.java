package com.vandorlabs.render;

import com.vandorlabs.segmented.SegmentedPanelMesh;

/** Octagonal aperture in the supplied 128 by 256 Reactor leaf, in normalized image space. */
public final class ReactorArtworkMesh {
 private ReactorArtworkMesh(){}
 private static final double[][] HOLE={{41D/128,159D/256},{87D/128,159D/256},{93D/128,166D/256},{93D/128,193D/256},{87D/128,200D/256},{41D/128,200D/256},{35D/128,193D/256},{35D/128,166D/256}};
 public static void pane(double[] b,SegmentedPanelMesh.Surface material,int layout,double scale,SegmentedPanelMesh.Sink out){
  double width=(93D-35)/128*(b[3]-b[0]),height=(200D-159)/256*(b[4]-b[1]);boolean fit=TextureLayoutMode.fit(layout,width*scale,height*scale,material.aspect);
  java.util.List<double[]> polygon=new java.util.ArrayList<>();double umax=0,vmax=0;
  for(double[] p:HOLE){double x=(p[0]-35D/128)*(b[3]-b[0]),y=(200D/256-p[1])*(b[4]-b[1]),u=fit?x/width:x*scale/Math.max(1,material.aspect),v=fit?y/height:y*scale*Math.min(1,material.aspect);polygon.add(new double[]{b[0]+p[0]*(b[3]-b[0]),b[1]+p[1]*(b[4]-b[1]),(b[2]+b[5])/2,u,v});umax=Math.max(umax,u);vmax=Math.max(vmax,v);}
  for(int u=0;u<Math.ceil(umax-1e-8);u++)for(int v=0;v<Math.ceil(vmax-1e-8);v++){
   java.util.List<double[]> clipped=PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(polygon,3,u,true),3,u+1,false),4,v,true),4,v+1,false);
   for(int i=1;i+1<clipped.size();i++)for(double[] p:new double[][]{clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1)})out.vertex(p[0],p[1],p[2],material.lower.u(Math.max(0,Math.min(1,p[3]-u))*16),material.lower.v(Math.max(0,Math.min(1,p[4]-v))*16),0,0,1);
  }
 }
 public static void build(double[] bounds,boolean pane,SegmentedPanelMesh.Texture image,SegmentedPanelMesh.Texture edge,SegmentedPanelMesh.Sink out){
  if(pane){for(int i=1;i+1<HOLE.length;i++)quad(bounds,new double[][]{HOLE[0],HOLE[i],HOLE[i+1],HOLE[i+1]},(bounds[2]+bounds[5])/2,1,image,out);return;}
  // Each band is bounded by a pair of straight octagon edges; no alpha mask or depth overlay.
  double[] ys={0,159D/256,166D/256,193D/256,200D/256,1};
  for(int band=0;band+1<ys.length;band++){
   double y0=ys[band],y1=ys[band+1];
   if(band==0||band==4)faces(bounds,new double[][]{{0,y0},{1,y0},{1,y1},{0,y1}},image,out);
   else{double left0=band==1?41D/128:35D/128,left1=band==3?41D/128:35D/128;
    faces(bounds,new double[][]{{0,y0},{left0,y0},{left1,y1},{0,y1}},image,out);
    faces(bounds,new double[][]{{1-left0,y0},{1,y0},{1,y1},{1-left1,y1}},image,out);
   }
  }
  for(int i=0;i<HOLE.length;i++){
   double[] a=HOLE[i],b=HOLE[(i+1)%HOLE.length];double dx=(b[0]-a[0])*(bounds[3]-bounds[0]),dy=(b[1]-a[1])*(bounds[4]-bounds[1]),length=Math.hypot(dx,dy);
   for(int v=0;v<4;v++){double[] p=v==0||v==3?a:b;out.vertex(bounds[0]+p[0]*(bounds[3]-bounds[0]),bounds[1]+p[1]*(bounds[4]-bounds[1]),v<2?bounds[2]:bounds[5],edge.u(v==1||v==2?length*16:0),edge.v(v<2?0:16*(bounds[5]-bounds[2])),(float)(-dy/length),(float)(dx/length),0);}
  }
 }
 private static void faces(double[] b,double[][] q,SegmentedPanelMesh.Texture image,SegmentedPanelMesh.Sink out){quad(b,q,b[2],-1,image,out);quad(b,q,b[5],1,image,out);}
 private static void quad(double[] b,double[][] q,double z,int side,SegmentedPanelMesh.Texture image,SegmentedPanelMesh.Sink out){for(int i=0;i<4;i++){double[] p=q[side>0?i:3-i];out.vertex(b[0]+p[0]*(b[3]-b[0]),b[1]+p[1]*(b[4]-b[1]),z,image.u(p[0]*16),image.v((1-p[1])*16),0,0,side);}}
}
