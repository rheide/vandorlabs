package com.vandorlabs.render;

/** Native metal faces of the rigid leaf; authored artwork occupies the other two faces. */
public final class TrapdoorRigidEdges {
 private TrapdoorRigidEdges(){}
 public static void emit(double[][] corners,boolean diagonal,boolean tall,TrapdoorSurfaceLayout.Sink sink){
  int first=tall?2:0;double depthY=tall?1:diagonal?2/16D:TrapdoorGeometry.THICKNESS,depthZ=tall?2/16D:1;
  for(int face=0;face<6;face++)if(face<first||face>=first+2){
   int[] indices=TrapdoorGeometry.FACES[face];double[] a=corners[indices[0]],b=corners[indices[1]],c=corners[indices[2]];
   double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
   double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
   for(int index:indices){double[] p=corners[index];double u=face<4?((index&1)==0?0:1):((index&4)==0?0:depthZ),v=face<2?((index&4)==0?0:depthZ):((index&2)==0?0:depthY);sink.vertex(p[0],p[1],p[2],u,v,false,(float)(nx/length),(float)(ny/length),(float)(nz/length));}
  }
 }
}
