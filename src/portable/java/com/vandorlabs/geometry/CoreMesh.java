package com.vandorlabs.geometry;

import java.util.*;

/** Texture-free tesseract scaffold, shared by game models, fabrication and map renderers. */
public final class CoreMesh {
    public static final float ITEM_GUI_SCALE=.70F;
    public static final List<String> IDS=Collections.unmodifiableList(Arrays.asList("assembly_press","synthesis_tower","enclosed_gantry","longburn_drive","meridian_core","paladyne_navigation_computer","seyr_navigation_computer","vector_fold_array","oculus_drive","sentinel_array","standalone_antenna","standalone_dish","survey_dish"));
    private static final int[] COLORS={0xffbd54,0xb38aff,0x62dfc0,0xff805c,0x62adff,0x68d8ff,0x8cffb6,0xff8cdb,0xb8a1ff,0xffd86b,0x8cdfe8,0x81b8ff,0xa5e28b};
    public static final class Face {
        public final int rgb;
        private final double[] vertices;
        public final double nx,ny,nz;
        private Face(int rgb,double[][] corners){this.rgb=rgb;vertices=new double[12];for(int i=0;i<4;i++)System.arraycopy(corners[i],0,vertices,i*3,3);double[] n=cross(sub(corners[1],corners[0]),sub(corners[2],corners[0]));double length=Math.sqrt(dot(n,n));nx=n[0]/length;ny=n[1]/length;nz=n[2]/length;}
        public double coordinate(int vertex,int axis){return vertices[vertex*3+axis];}
    }
    private static final Map<String,List<Face>> MESHES=new LinkedHashMap<>();
    static{for(int i=0;i<IDS.size();i++)MESHES.put(IDS.get(i),build(COLORS[i]));}
    public static int color(String id){int index=IDS.indexOf(id);if(index<0)throw new IllegalArgumentException("Unknown Core "+id);return COLORS[index];}
    public static List<Face> faces(String id){List<Face> mesh=MESHES.get(id);if(mesh==null)throw new IllegalArgumentException("Unknown Core "+id);return mesh;}
    private static List<Face> build(int color){
        List<Face> faces=new ArrayList<>();double lo=.14,hi=.86,innerLo=.40,innerHi=.60;
        // Twelve slender outline edges. Corner nodes and struts remain visibly separate geometry.
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){double[] start={0,0,0},end={0,0,0};start[axis]=lo;end[axis]=hi;start[(axis+1)%3]=end[(axis+1)%3]=a==0?lo:hi;start[(axis+2)%3]=end[(axis+2)%3]=b==0?lo:hi;rod(faces,start,end,.012,shade(color,.70));}
        for(int corner=0;corner<8;corner++){double[] outer=new double[3],inner=new double[3];for(int axis=0;axis<3;axis++){boolean high=(corner&(1<<axis))!=0;outer[axis]=high?hi:lo;inner[axis]=high?innerHi:innerLo;}box(faces,outer,.032,shade(color,.95));rod(faces,inner,outer,.009,shade(color,.82));}
        box(faces,new double[]{.5,.5,.5},.10,color);
        return Collections.unmodifiableList(faces);
    }
    private static int shade(int rgb,double amount){return ((int)((rgb>>16&255)*amount)<<16)|((int)((rgb>>8&255)*amount)<<8)|(int)((rgb&255)*amount);}
    private static void box(List<Face> out,double[] center,double radius,int color){rod(out,new double[]{center[0],center[1]-radius,center[2]},new double[]{center[0],center[1]+radius,center[2]},radius,color);}
    private static void rod(List<Face> out,double[] start,double[] end,double radius,int color){
        double[] axis=sub(end,start);double length=Math.sqrt(dot(axis,axis));for(int i=0;i<3;i++)axis[i]/=length;
        double[] u=cross(axis,Math.abs(axis[1])<.9?new double[]{0,1,0}:new double[]{1,0,0});double ul=Math.sqrt(dot(u,u));for(int i=0;i<3;i++)u[i]*=radius/ul;double[] v=cross(axis,u);
        double[][] points=new double[8][];for(int endIndex=0;endIndex<2;endIndex++)for(int corner=0;corner<4;corner++){double us=corner==0||corner==3?-1:1,vs=corner<2?-1:1;double[] p=new double[3];for(int i=0;i<3;i++)p[i]=(endIndex==0?start[i]:end[i])+us*u[i]+vs*v[i];points[endIndex*4+corner]=p;}
        int[][] indices={{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}};for(int[] face:indices)out.add(new Face(color,new double[][]{points[face[0]],points[face[1]],points[face[2]],points[face[3]]}));
    }
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private CoreMesh(){}
}
