package com.vandorlabs.segmented;

/** Released section transform over immutable pixel-space position/UV/normal records. */
public final class SegmentedPanelTransform {
 private SegmentedPanelTransform(){}
 public static void treadPose(float[] vertices,int quad,double[] descriptor,double[] out){int axis=descriptor[11]<2?1:0;double midpoint=0;for(int v=0;v<4;v++)midpoint+=vertices[quad+v*8+axis]/4D;int pixels=(int)descriptor[12],strip=Math.max(0,Math.min(16/pixels-1,(int)Math.floor(midpoint/pixels)));SegmentedTreads.pose(descriptor,strip,out);}
 /** Writes world position and unit normal to the caller-owned six-scalar scratch. */
 public static void vertex(float[] vertices,int at,double[] pose,int rotation,double x,double y,double z,double[] out){
  boolean vertical=pose[5]!=0;double sin=pose[3],cos=pose[4],px=vertices[at]/16D,py=vertices[at+1]/16D-.5,pz=vertices[at+2]/16D,nx=vertices[at+5],ny=vertices[at+6],nz=vertices[at+7],rx,ry,rz,rnx,rny,rnz;
  if(vertical){rx=px;ry=pose[1]+cos*py-sin*pz;rz=pose[2]+sin*py+cos*pz;rnx=nx;rny=cos*ny-sin*nz;rnz=sin*ny+cos*nz;}
  else{rx=pose[0]+cos*(px-.5)-sin*pz;ry=pose[1]+py;rz=pose[2]+sin*(px-.5)+cos*pz;rnx=cos*nx-sin*nz;rny=ny;rnz=sin*nx+cos*nz;}
  out[0]=x+(rotation==1?rz:rotation==2?1-rx:rotation==3?1-rz:rx);out[1]=y+ry;out[2]=z+(rotation==1?1-rx:rotation==2?1-rz:rotation==3?rx:rz);
  out[3]=rotation==1?rnz:rotation==2?-rnx:rotation==3?-rnz:rnx;out[4]=rny;out[5]=rotation==1?-rnx:rotation==2?-rnz:rotation==3?rnx:rnz;
 }
}
