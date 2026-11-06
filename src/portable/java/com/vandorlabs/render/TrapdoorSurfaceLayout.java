package com.vandorlabs.render;

import java.util.*;

/** Immutable clipped affine triangles. Atlas, lighting, pose and consumer format remain caller-owned. */
public final class TrapdoorSurfaceLayout {
 public interface Sink { void vertex(double x,double y,double z,double u,double v,boolean lower,float nx,float ny,float nz); }
 private final Face[] faces; private final int points;
 private TrapdoorSurfaceLayout(Face[] faces,int points){this.faces=faces;this.points=points;}
 public int points(){return points;}
 public long retainedBytes(){return 64L+faces.length*32L+points*48L;}
 public static TrapdoorSurfaceLayout build(double[][] uv,int first,int end,boolean tiled,boolean mirror,boolean customDoor) {
  if(uv.length!=8||first<0||end>6||first>=end)throw new IllegalArgumentException("leaf UV faces");
  for(double[] row:uv){if(row.length!=3)throw new IllegalArgumentException("leaf UV row");for(double value:row)if(!Double.isFinite(value)||Math.abs(value)>64)throw new IllegalArgumentException("bounded leaf UV");}
        Face[] faces=new Face[end-first];int count=0;
        for(int face=first;face<end;face++) {
            List<double[]> polygon=new ArrayList<>();
            for(int index:TrapdoorGeometry.FACES[face])polygon.add(new double[]{index&1,(index>>1)&1,(index>>2)&1,uv[index][0],face<2?uv[index][2]:uv[index][1]});
            double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
            for(double[] point:polygon){minU=Math.min(minU,point[3]);maxU=Math.max(maxU,point[3]);minV=Math.min(minV,point[4]);maxV=Math.max(maxV,point[4]);}
            int u0=tiled?(int)Math.floor(minU):0,u1=tiled?(int)Math.ceil(maxU)-1:0;
            int v0=tiled?(int)Math.floor(minV):0,v1=tiled?(int)Math.ceil(maxV)-1:0;
            List<double[]> triangles=new ArrayList<>();
            for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++)for(int half=0;half<(customDoor?2:1);half++) {
                double low=v+(customDoor?half*.5:0),high=v+(customDoor?(half+1)*.5:1);
                List<double[]> clipped=clip(clip(clip(clip(polygon,3,u,true),3,u+1,false),4,low,true),4,high,false);
                for(int j=1;j+1<clipped.size();j++)for(double[] point:new double[][]{clipped.get(0),clipped.get(j),clipped.get(j+1)}) {
                    double pu=point[3]-u,pv=point[4]-v;
                    if(mirror && (u&1)!=0)pu=1-pu;
                    if(customDoor)pv=(pv-half*.5)*2;
                    triangles.add(new double[]{point[0],point[1],point[2],pu,pv,half});
                }
            }
            double[] data=new double[triangles.size()*6];
            for(int i=0;i<triangles.size();i++)System.arraycopy(triangles.get(i),0,data,i*6,6);
            faces[face-first]=new Face(face,data);count+=triangles.size();
        }
        return new TrapdoorSurfaceLayout(faces,count);
 }
 public void emit(double[][] vertices,Sink sink) {
        for(Face face:faces) {
            int[] indices=TrapdoorGeometry.FACES[face.index];
            double[] a=vertices[indices[0]],b=vertices[indices[1]],c=vertices[indices[2]];
            double nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            float normalX=(float)(nx/length),normalY=(float)(ny/length),normalZ=(float)(nz/length);
            // Clipped coordinates are affine weights in the leaf's corner basis.
            double[] origin=vertices[0],x=vertices[1],y=vertices[2],z=vertices[4];
            for(int i=0;i<face.data.length;i+=18) {
                double[] data=face.data;
                double ax=coordinate(data,i,origin[0],x[0],y[0],z[0]),ay=coordinate(data,i,origin[1],x[1],y[1],z[1]),az=coordinate(data,i,origin[2],x[2],y[2],z[2]);
                double bx=coordinate(data,i+6,origin[0],x[0],y[0],z[0]),by=coordinate(data,i+6,origin[1],x[1],y[1],z[1]),bz=coordinate(data,i+6,origin[2],x[2],y[2],z[2]);
                double cx=coordinate(data,i+12,origin[0],x[0],y[0],z[0]),cy=coordinate(data,i+12,origin[1],x[1],y[1],z[1]),cz=coordinate(data,i+12,origin[2],x[2],y[2],z[2]);
                double crossX=(by-ay)*(cz-az)-(bz-az)*(cy-ay),crossY=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),crossZ=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
                if(crossX*crossX+crossY*crossY+crossZ*crossZ<1e-18)continue;
                boolean lower=data[i+5]!=0;
                emit(sink,lower,data,i,ax,ay,az,normalX,normalY,normalZ);
                emit(sink,lower,data,i+6,bx,by,bz,normalX,normalY,normalZ);
                emit(sink,lower,data,i+12,cx,cy,cz,normalX,normalY,normalZ);
                emit(sink,lower,data,i+12,cx,cy,cz,normalX,normalY,normalZ);
            }
        }
    }
 private static double coordinate(double[] point,int i,double origin,double x,double y,double z) {return origin+point[i]*(x-origin)+point[i+1]*(y-origin)+point[i+2]*(z-origin);}
 private static void emit(Sink sink,boolean lower,double[] data,int i,double x,double y,double z,float nx,float ny,float nz){sink.vertex(x,y,z,data[i+3],data[i+4],lower,nx,ny,nz);}
 private static List<double[]> clip(List<double[]> polygon,int axis,double edge,boolean greater){return PanelTextureMapping.clip(polygon,axis,edge,greater);}
 private static final class Face {final int index;final double[] data;Face(int index,double[] data){this.index=index;this.data=data;}}
}
