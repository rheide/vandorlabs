package com.vandorlabs.canopy;

import java.util.*;

/** Physical surfaces and exact triangle rays deliberately do not use the rendering clearance/tiling mesh. */
public final class CanopyPhysics {
    private static final int[] X={0,0,0,0,-1,1},Y={-1,1,0,0,0,0},Z={0,0,-1,1,0,0};
    public static final class Box {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        public Box(double x0,double y0,double z0,double x1,double y1,double z1){minX=Math.min(x0,x1);minY=Math.min(y0,y1);minZ=Math.min(z0,z1);maxX=Math.max(x0,x1);maxY=Math.max(y0,y1);maxZ=Math.max(z0,z1);}
        public Box grow(double amount){return new Box(minX-amount,minY-amount,minZ-amount,maxX+amount,maxY+amount,maxZ+amount);}
        public Box offset(int x,int y,int z){return new Box(minX+x,minY+y,minZ+z,maxX+x,maxY+y,maxZ+z);}
        @Override public int hashCode(){int hash=Double.hashCode(minX);hash=31*hash+Double.hashCode(minY);hash=31*hash+Double.hashCode(minZ);hash=31*hash+Double.hashCode(maxX);hash=31*hash+Double.hashCode(maxY);return 31*hash+Double.hashCode(maxZ);}
        @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof Box))return false;Box b=(Box)o;return Double.compare(minX,b.minX)==0&&Double.compare(minY,b.minY)==0&&Double.compare(minZ,b.minZ)==0&&Double.compare(maxX,b.maxX)==0&&Double.compare(maxY,b.maxY)==0&&Double.compare(maxZ,b.maxZ)==0;}
    }
    public static final class Hit {public final double x,y,z;public final int face;Hit(double x,double y,double z,int face){this.x=x;this.y=y;this.z=z;this.face=face;}}
    private CanopyPhysics(){}
    public static List<Box> boxes(List<CanopyFace> faces,int facing,int x,int y,int z,boolean window){
        List<Box> boxes=new ArrayList<>();Set<Box> rails=window?new HashSet<>():null;
        for(CanopyFace face:faces){if(window&&!face.glazing&&!face.baseMaterial.equals("glass"))continue;CanopyPoint[] p=new CanopyPoint[face.points()];for(int i=0;i<p.length;i++){CanopyPoint v=face.vertex(i);if(!window&&face.group.equals("shell")){/* collision("posed",0) still performs the released zero-angle hinge arithmetic. */double yy=v.y-1,zz=v.z-2;v=new CanopyPoint(v.x,1+yy*1-zz*0,2+yy*0+zz*1);}p[i]=CanopyTransform.rotate(v,facing);}for(int i=1;i<p.length-1;i++)subdivide(boxes,p[0].x,p[0].y,p[0].z,p[i].x,p[i].y,p[i].z,p[i+1].x,p[i+1].y,p[i+1].z,3,x,y,z);
            if(window)for(int i=0;i<p.length;i++){CanopyPoint a=p[i],b=p[(i+1)%p.length];int steps=Math.max(1,(int)Math.ceil((double)(float)Math.sqrt(a.squareDistanceTo(b))*8));for(int step=0;step<steps;step++){CanopyPoint from=a.add(b.subtract(a).scale(step/(double)steps)),to=a.add(b.subtract(a).scale((step+1D)/steps));rails.add(new Box(from.x,from.y,from.z,to.x,to.y,to.z).grow(1D/16+.002).offset(x,y,z));}}
        }
        if(window)boxes.addAll(rails);return Collections.unmodifiableList(boxes);
    }
    private static void subdivide(List<Box> out,double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,int depth,int x,int y,int z){
        double x0=Math.min(ax,Math.min(bx,cx)),y0=Math.min(ay,Math.min(by,cy)),z0=Math.min(az,Math.min(bz,cz)),x1=Math.max(ax,Math.max(bx,cx)),y1=Math.max(ay,Math.max(by,cy)),z1=Math.max(az,Math.max(bz,cz));
        if(depth==0||x1-x0<.0001||y1-y0<.0001||z1-z0<.0001){out.add(new Box(x0-.002+x,y0-.002+y,z0-.002+z,x1+.002+x,y1+.002+y,z1+.002+z));return;}
        double abx=(ax+bx)*.5,aby=(ay+by)*.5,abz=(az+bz)*.5,bcx=(bx+cx)*.5,bcy=(by+cy)*.5,bcz=(bz+cz)*.5,cax=(cx+ax)*.5,cay=(cy+ay)*.5,caz=(cz+az)*.5;
        subdivide(out,ax,ay,az,abx,aby,abz,cax,cay,caz,depth-1,x,y,z);subdivide(out,abx,aby,abz,bx,by,bz,bcx,bcy,bcz,depth-1,x,y,z);subdivide(out,cax,cay,caz,bcx,bcy,bcz,cx,cy,cz,depth-1,x,y,z);subdivide(out,abx,aby,abz,bcx,bcy,bcz,cax,cay,caz,depth-1,x,y,z);
    }
    public static Box bounds(List<Box> boxes,int x,int y,int z){if(boxes.isEmpty())return new Box(x,y,z,x+1,y+1,z+1);Box first=boxes.get(0);double x0=first.minX,y0=first.minY,z0=first.minZ,x1=first.maxX,y1=first.maxY,z1=first.maxZ;for(int i=1;i<boxes.size();i++){Box b=boxes.get(i);x0=Math.min(x0,b.minX);y0=Math.min(y0,b.minY);z0=Math.min(z0,b.minZ);x1=Math.max(x1,b.maxX);y1=Math.max(y1,b.maxY);z1=Math.max(z1,b.maxZ);}return new Box(x0,y0,z0,x1,y1,z1);}
    public static Hit trace(List<CanopyFace> faces,int facing,int x,int y,int z,CanopyPoint start,CanopyPoint end){
        int inverse=facing==5?4:facing==4?5:facing;start=CanopyTransform.rotate(start.addVector(-x,-y,-z),inverse);end=CanopyTransform.rotate(end.addVector(-x,-y,-z),inverse);double dx=end.x-start.x,dy=end.y-start.y,dz=end.z-start.z,nearest=Double.POSITIVE_INFINITY,nx=0,ny=0,nz=0;
        for(CanopyFace face:faces){CanopyPoint a=face.vertex(0);double rx=start.x-a.x,ry=start.y-a.y,rz=start.z-a.z;for(int i=1;i<face.points()-1;i++){CanopyPoint b=face.vertex(i),c=face.vertex(i+1);double ex=b.x-a.x,ey=b.y-a.y,ez=b.z-a.z,fx=c.x-a.x,fy=c.y-a.y,fz=c.z-a.z,cx=dy*fz-dz*fy,cy=dz*fx-dx*fz,cz=dx*fy-dy*fx,det=ex*cx+ey*cy+ez*cz;if(Math.abs(det)<1E-10)continue;double inv=1/det,u=(rx*cx+ry*cy+rz*cz)*inv;if(u< -1E-8||u>1+1E-8)continue;double qx=ry*ez-rz*ey,qy=rz*ex-rx*ez,qz=rx*ey-ry*ex,v=(dx*qx+dy*qy+dz*qz)*inv;if(v< -1E-8||u+v>1+1E-8)continue;double distance=(fx*qx+fy*qy+fz*qz)*inv;if(distance<0||distance>1||distance>=nearest)continue;nearest=distance;nx=ey*fz-ez*fy;ny=ez*fx-ex*fz;nz=ex*fy-ey*fx;if(nx*dx+ny*dy+nz*dz>0){nx=-nx;ny=-ny;nz=-nz;}}}
        if(!Double.isFinite(nearest))return null;CanopyPoint hit=CanopyTransform.rotate(new CanopyPoint(start.x+dx*nearest,start.y+dy*nearest,start.z+dz*nearest),facing).addVector(x,y,z),normal=CanopyTransform.rotate(new CanopyPoint(nx,ny,nz).addVector(.5,0,.5),facing).addVector(-.5,0,-.5);float fx=(float)normal.x,fy=(float)normal.y,fz=(float)normal.z,best=Float.MIN_VALUE;int direction=2;for(int i=0;i<6;i++){float dot=fx*X[i]+fy*Y[i]+fz*Z[i];if(dot>best){best=dot;direction=i;}}return new Hit(hit.x,hit.y,hit.z,direction);
    }
}
