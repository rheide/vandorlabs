package com.vandorlabs.fluids;

import com.vandorlabs.render.DiagonalMeshClip;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exposed non-panel volume surfaces and the legacy liquid corner-height policy. */
public final class FluidSurfaceMesh {
    private FluidSurfaceMesh(){}
    public interface Height {double at(double x,double z);}
    public static final class Neighbor {
        public final FluidVolume volume;private final boolean[] matching;public final boolean hidden;
        public Neighbor(FluidVolume volume,boolean first,boolean second,boolean hidden){this.volume=volume;matching=new boolean[]{first,second};this.hidden=hidden;}
        public boolean matches(int side){return matching[side];}
    }
    public static final class Vertex {
        public final double x,y,z;public Vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    public static final class Surface {
        public final List<Vertex> vertices;public final double nx,ny,nz;public final int side,axis,boundary;
        Surface(List<double[]> polygon,int side,int axis,int boundary){List<Vertex> points=new ArrayList<Vertex>();for(double[] p:polygon)points.add(new Vertex(p[0],p[1],p[2]));vertices=Collections.unmodifiableList(points);double[] normal=normal(polygon);double length=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);nx=normal[0]/length;ny=normal[1]/length;nz=normal[2]/length;this.side=side;this.axis=axis;this.boundary=boundary;}
    }
    public static List<Surface> surfaces(FluidVolume volume,int side,Neighbor[] neighbors,Height height){
        List<Surface> result=new ArrayList<Surface>();for(FluidVolume.Face face:volume.faces)if(face.side==side){List<double[]> polygon=new ArrayList<double[]>();for(FluidVolume.Point p:face.vertices)polygon.add(new double[]{p.x,p.y,p.z});int boundary=boundary(polygon);Neighbor neighbor=boundary<0||neighbors==null?null:neighbors[boundary];if(neighbor!=null&&neighbor.hidden)continue;
            List<List<double[]>> pieces=new ArrayList<List<double[]>>();pieces.add(polygon);
            if(neighbor!=null&&neighbor.volume!=null)for(FluidVolume.Cell cell:neighbor.volume.cells)if(neighbor.matches(cell.side)){List<List<double[]>> remaining=new ArrayList<List<double[]>>();for(List<double[]> piece:pieces)subtract(piece,planes(cell.box),offset(boundary,0),offset(boundary,1),offset(boundary,2),remaining);pieces=remaining;}
            for(List<double[]> piece:pieces)if(area(piece)>1e-10){if(height!=null)for(double[] point:piece)if(point[1]>.998){double top=height.at(point[0],point[2]);if(top<.998)point[1]=top;}if(area(piece)>1e-10)result.add(new Surface(piece,side,face.axis,boundary));}
        }return Collections.unmodifiableList(result);
    }
    private static int offset(int face,int axis){return face==0&&axis==1||face==2&&axis==2||face==4&&axis==0?-1:face==1&&axis==1||face==3&&axis==2||face==5&&axis==0?1:0;}
    public static List<double[]> planes(FluidVolume.Box box){List<double[]> result=new ArrayList<double[]>();result.add(new double[]{-1,0,0,-box.minX});result.add(new double[]{1,0,0,box.maxX});result.add(new double[]{0,-1,0,-box.minY});result.add(new double[]{0,1,0,box.maxY});result.add(new double[]{0,0,-1,-box.minZ});result.add(new double[]{0,0,1,box.maxZ});return result;}
    /** Same half-space subtraction used by the legacy joined-fluid renderer, retaining vertex attributes. */
    public static void subtract(List<double[]> polygon,List<double[]> planes,int dx,int dy,int dz,List<List<double[]>> out){List<double[]> inside=polygon;for(double[] plane:planes){double d=plane[3]+plane[0]*dx+plane[1]*dy+plane[2]*dz;List<double[]> outside=DiagonalMeshClip.plane(inside,-plane[0],-plane[1],-plane[2],-d-1e-8);if(area(outside)>1e-10)out.add(outside);inside=DiagonalMeshClip.plane(inside,plane[0],plane[1],plane[2],d);if(area(inside)<1e-10)return;}}
    public static int boundary(List<double[]> polygon){for(int face=0;face<6;face++){int axis=face<2?1:face<4?2:0;double edge=(face&1)==1?1:0;boolean on=true;for(double[] p:polygon)on&=Math.abs(p[axis]-edge)<1e-8;if(on)return face;}return -1;}
    public static double[] normal(List<double[]> polygon){double[] result=new double[3];if(polygon.size()<3)return result;for(int i=0;i<polygon.size();i++){double[] a=polygon.get(i),b=polygon.get((i+1)%polygon.size());result[0]+=a[1]*b[2]-a[2]*b[1];result[1]+=a[2]*b[0]-a[0]*b[2];result[2]+=a[0]*b[1]-a[1]*b[0];}return result;}
    public static double area(List<double[]> polygon){double[] n=normal(polygon);return Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2])/2;}
    public static final class Column {
        public final boolean above,same,solid;public final int level;
        public Column(boolean above,boolean same,int level,boolean solid){this.above=above;this.same=same;this.level=level;this.solid=solid;}
    }
    public static double cornerHeight(List<Column> columns){float depth=0;int count=0;for(Column column:columns){if(column.above)return 1;if(column.same){int level=column.level>=8?0:column.level;float value=(level+1)/9F;if(column.level>=8||column.level==0){depth+=value*10;count+=10;}depth+=value;count++;}else if(!column.solid){depth++;count++;}}return count==0?8D/9:1-depth/count;}
    public static double height(double a,double b,double c,double d,double x,double z){return (a*(1-x)+d*x)*(1-z)+(b*(1-x)+c*x)*z-.001;}
}
