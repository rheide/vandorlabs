package com.vandorlabs.programmer;

/** Authored 320×136 journal plane and released half-open button regions. No platform/world state. */
public final class ProgrammerJournalPlane {
    public static final int WIDTH=JournalOverlay.WIDTH,HEIGHT=JournalOverlay.MIN_HEIGHT;
    public static final class Point {public final double x,y,z;private Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}
    public static final class Hit {public final double u,v;private Hit(double u,double v){this.u=u;this.v=v;}public int action(){return ProgrammerJournalPlane.action(u,v);}}
    private final double ox,oy,oz,ax,ay,az,dx,dy,dz,nx,ny,nz,acrossSquared,downSquared;
    public ProgrammerJournalPlane(ProgrammerPicking.Mesh mesh){
        double[][] points=mesh.screen();ox=points[1][0];oy=points[1][1];oz=points[1][2];
        ax=points[0][0]-ox;ay=points[0][1]-oy;az=points[0][2]-oz;dx=points[2][0]-ox;dy=points[2][1]-oy;dz=points[2][2]-oz;
        double x=dy*az-dz*ay,y=dz*ax-dx*az,z=dx*ay-dy*ax,length=(double)(float)Math.sqrt(x*x+y*y+z*z);
        if(length<1e-4)throw new IllegalArgumentException("Degenerate Programmer screen plane");nx=x/length;ny=y/length;nz=z/length;
        acrossSquared=ax*ax+ay*ay+az*az;downSquared=dx*dx+dy*dy+dz*dz;
    }
    public Point point(double u,double v){return new Point(ox+ax*(u/WIDTH)+dx*(v/HEIGHT),oy+ay*(u/WIDTH)+dy*(v/HEIGHT),oz+az*(u/WIDTH)+dz*(v/HEIGHT));}
    public Point world(ProgrammerTransform.Affine a,int originX,int originY,int originZ,double u,double v){Point p=point(u,v);return new Point(originX+a.x(p.x,p.z),originY+p.y,originZ+a.z(p.x,p.z));}
    /** Released GL column-major pixel frame, including the small normal offset and depth scale. */
    public void matrix(ProgrammerTransform.Affine a,float[] target){
        if(target.length<16)throw new IllegalArgumentException("Programmer journal matrix");
        double x=ax*(1D/WIDTH),y=ay*(1D/WIDTH),z=az*(1D/WIDTH),downX=dx*(1D/HEIGHT),downY=dy*(1D/HEIGHT),downZ=dz*(1D/HEIGHT),depth=Math.sqrt(x*x+y*y+z*z);
        target[0]=(float)(a.xx*x+a.xz*z);target[1]=(float)y;target[2]=(float)(a.zx*x+a.zz*z);target[3]=0;
        target[4]=(float)(a.xx*downX+a.xz*downZ);target[5]=(float)downY;target[6]=(float)(a.zx*downX+a.zz*downZ);target[7]=0;
        target[8]=(float)((a.xx*nx+a.xz*nz)*depth);target[9]=(float)(ny*depth);target[10]=(float)((a.zx*nx+a.zz*nz)*depth);target[11]=0;
        target[12]=(float)a.x(ox+nx*.001,oz+nz*.001);target[13]=(float)(oy+ny*.001);target[14]=(float)a.z(ox+nx*.001,oz+nz*.001);target[15]=1;
    }
    public Hit hit(ProgrammerTransform.Affine a,int originX,int originY,int originZ,double startX,double startY,double startZ,double endX,double endY,double endZ){
        double x=startX-originX-a.offsetX,z=startZ-originZ-a.offsetZ,fx=a.xx*x+a.zx*z,fy=startY-originY,fz=a.xz*x+a.zz*z;
        x=endX-originX-a.offsetX;z=endZ-originZ-a.offsetZ;double rx=a.xx*x+a.zx*z-fx,ry=endY-originY-fy,rz=a.xz*x+a.zz*z-fz;
        double denominator=rx*nx+ry*ny+rz*nz;if(denominator>=-1e-9)return null;
        double t=((ox-fx)*nx+(oy-fy)*ny+(oz-fz)*nz)/denominator;if(t<0||t>1)return null;
        double lx=fx+rx*t-ox,ly=fy+ry*t-oy,lz=fz+rz*t-oz;
        double u=(lx*ax+ly*ay+lz*az)/acrossSquared*WIDTH,v=(lx*dx+ly*dy+lz*dz)/downSquared*HEIGHT;
        return u>=0&&u<=WIDTH&&v>=0&&v<=HEIGHT?new Hit(u,v):null;
    }
    public static int action(double u,double v){if(v<116||v>=132)return 0;return u>=JournalOverlay.PREVIOUS_LEFT&&u<JournalOverlay.PREVIOUS_RIGHT?-1:u>=JournalOverlay.NEXT_LEFT&&u<JournalOverlay.NEXT_RIGHT?1:0;}
}
