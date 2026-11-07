package com.vandorlabs.programmer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable authored triangles and the released cell-clipped intersection policy. No world queries. */
public final class ProgrammerPicking {
    private ProgrammerPicking() { }
    public static final class Hit {
        public final double x,y,z;
        /** Source Direction order: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
        public final int side,slot;
        public final String group;
        private Hit(double x,double y,double z,int side,int slot,String group) {
            this.x=x;this.y=y;this.z=z;this.side=side;this.slot=slot;this.group=group;
        }
    }
    private static final class Point {
        final double x,y,z;
        Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    private static final class Triangle {
        final Point a;
        final double ex,ey,ez,ox,oy,oz,nx,ny,nz;
        final int slot;
        final String group;
        Triangle(Point a,Point b,Point c,String group) {
            this.a=a;this.group=group;slot=slot(group);
            ex=b.x-a.x;ey=b.y-a.y;ez=b.z-a.z;
            ox=c.x-a.x;oy=c.y-a.y;oz=c.z-a.z;
            nx=ey*oz-ez*oy;ny=ez*ox-ex*oz;nz=ex*oy-ey*ox;
        }
    }
    private static final ProgrammerTransform.Affine[] TRANSFORMS=new ProgrammerTransform.Affine[4];
    static {for(ProgrammerTransform.Facing facing:ProgrammerTransform.Facing.values())TRANSFORMS[facing.ordinal()]=ProgrammerTransform.of(facing,2,1);}
    private static int slot(String group) {
        return group.contains("__input_pad__")||group.contains("__input_socket_")?1:
            group.contains("__output_pad__")||group.contains("__output_socket_")?2:
            group.contains("__master_pad__")||group.contains("__master_socket_")?0:
            group.contains("__status_screen_surface")?3:-1;
    }
    public static final class Mesh {
        private final Triangle[] triangles;
        private final Point[] screen;
        private Mesh(List<Triangle> triangles,Point[] screen) {
            this.triangles=triangles.toArray(new Triangle[triangles.size()]);this.screen=screen;
        }
        public int triangles(){return triangles.length;}
        public double[][] screen(){
            if(screen==null)throw new IllegalStateException("Programmer screen plane missing");
            double[][] result=new double[screen.length][3];
            for(int i=0;i<screen.length;i++){result[i][0]=screen[i].x;result[i][1]=screen[i].y;result[i][2]=screen[i].z;}
            return result;
        }
        public Hit trace(int cellX,int cellY,int cellZ,int originX,int originY,int originZ,
                         ProgrammerTransform.Facing facing,double startX,double startY,double startZ,
                         double endX,double endY,double endZ) {
            ProgrammerTransform.Affine a=TRANSFORMS[Objects.requireNonNull(facing).ordinal()];
            double sx=startX-originX-a.offsetX,sz=startZ-originZ-a.offsetZ;
            double fx=a.xx*sx+a.zx*sz,fy=startY-originY,fz=a.xz*sx+a.zz*sz;
            double tx=endX-originX-a.offsetX,tz=endZ-originZ-a.offsetZ;
            double rx=a.xx*tx+a.zx*tz-fx,ry=endY-originY-fy,rz=a.xz*tx+a.zz*tz-fz;
            double nearest=Double.POSITIVE_INFINITY,hx=0,hy=0,hz=0;
            Triangle selected=null;
            for(Triangle triangle:triangles) {
                double px=ry*triangle.oz-rz*triangle.oy,py=rz*triangle.ox-rx*triangle.oz,pz=rx*triangle.oy-ry*triangle.ox;
                double determinant=triangle.ex*px+triangle.ey*py+triangle.ez*pz;
                if(Math.abs(determinant)<1e-10)continue;
                double inverse=1/determinant;
                double dx=fx-triangle.a.x,dy=fy-triangle.a.y,dz=fz-triangle.a.z;
                double u=(dx*px+dy*py+dz*pz)*inverse;
                if(u< -1e-7||u>1+1e-7)continue;
                double qx=dy*triangle.ez-dz*triangle.ey,qy=dz*triangle.ex-dx*triangle.ez,qz=dx*triangle.ey-dy*triangle.ex;
                double v=(rx*qx+ry*qy+rz*qz)*inverse;
                if(v< -1e-7||u+v>1+1e-7)continue;
                double distance=(triangle.ox*qx+triangle.oy*qy+triangle.oz*qz)*inverse;
                if(!(distance>=0&&distance<=1))continue;
                double x=fx+rx*distance,y=fy+ry*distance,z=fz+rz*distance;
                double wx=originX+a.x(x,z),wy=originY+y,wz=originZ+a.z(x,z);
                if(wx<cellX-1e-7||wx>cellX+1+1e-7||wy<cellY-1e-7||wy>cellY+1+1e-7||wz<cellZ-1e-7||wz>cellZ+1+1e-7)continue;
                double lx=startX-wx,ly=startY-wy,lz=startZ-wz,length=lx*lx+ly*ly+lz*lz;
                if(length>=nearest)continue;
                nearest=length;hx=wx;hy=wy;hz=wz;selected=triangle;
            }
            return selected==null?null:new Hit(hx,hy,hz,side((float)(a.xx*selected.nx+a.xz*selected.nz),(float)selected.ny,(float)(a.zx*selected.nx+a.zz*selected.nz)),selected.slot,selected.group);
        }
    }
    private static int side(float x,float y,float z){
        float best=Float.MIN_VALUE;int selected=2;
        for(int side=0;side<6;side++){
            float value=x*(side==4?-1:side==5?1:0)+y*(side==0?-1:side==1?1:0)+z*(side==2?-1:side==3?1:0);
            if(value>best){best=value;selected=side;}
        }
        return selected;
    }
    /** Consumes and closes both streams, preserving authored face and fan-triangle order. */
    public static Mesh read(InputStream staticPart,InputStream screenPart)throws IOException {
        List<Triangle> triangles=new ArrayList<Triangle>();Point[][] screen={null};
        readPart(staticPart,triangles,screen);readPart(screenPart,triangles,screen);
        return new Mesh(triangles,screen[0]);
    }
    private static void readPart(InputStream stream,List<Triangle> triangles,Point[][] screen)throws IOException {
        if(stream==null)throw new IOException("Missing programmer selection geometry");
        List<Point> vertices=new ArrayList<Point>();String group="";
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){String[] bits=line.trim().split("\\s+");
                if(bits[0].equals("v"))vertices.add(new Point(Double.parseDouble(bits[1]),Double.parseDouble(bits[2]),Double.parseDouble(bits[3])));
                else if(bits[0].equals("g"))group=bits[1];
                else if(bits[0].equals("f")){
                    Point[] points=new Point[bits.length-1];
                    for(int i=1;i<bits.length;i++)points[i-1]=vertices.get(Integer.parseInt(bits[i].split("/")[0])-1);
                    if(screen[0]==null&&slot(group)==3)screen[0]=points;
                    for(int i=1;i<points.length-1;i++)triangles.add(new Triangle(points[0],points[i],points[i+1],group));
                }
            }
        }catch(RuntimeException invalid){throw new IOException("Invalid programmer selection geometry",invalid);}
    }
}
