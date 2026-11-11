package com.vandorlabs.client;

import java.util.Arrays;

/** The twelve static shell/cap variants; materials, light and charge remain live in the renderer. */
final class BatteryGeometryCache {
    static final int X=0,Y=1,Z=2,CASING=0,CELLS=1,INPUT=2,OUTPUT=3;
    static final long MAX_CACHED_BYTES=64*1024;
    private static final double H=Math.sqrt(3)/4;
    private static final double[][] HEX={{.25,.5-H},{.75,.5-H},{1,.5},{.75,.5+H},{.25,.5+H},{0,.5}},BLOCK={{0,0},{1,0},{1,1},{0,1}};
    private final Mesh[] meshes=new Mesh[12];
    private long bytes,builds;
    interface Sink {
        void beginQuad(int material,float nx,float ny,float nz);
        void vertex(double x,double y,double z,double u,double v);
    }
    static final class Mesh {
        private final double[] vertices;
        private final float[] normals;
        private final int[] materials;
        private Mesh(double[] vertices,float[] normals,int[] materials){this.vertices=vertices;this.normals=normals;this.materials=materials;}
        int vertexCount(){return vertices.length/5;}
        long retainedBytes(){return 128L+vertices.length*8L+normals.length*4L+materials.length*4L;}
        void emit(Sink sink){for(int q=0;q<materials.length;q++){sink.beginQuad(materials[q],normals[q*3],normals[q*3+1],normals[q*3+2]);for(int i=q*20;i<q*20+20;i+=5)sink.vertex(vertices[i],vertices[i+1],vertices[i+2],vertices[i+3],vertices[i+4]);}}
    }
    synchronized Mesh mesh(boolean block,int axis,boolean positive){
        if(axis<X||axis>Z)throw new IllegalArgumentException("battery axis");
        int key=(block?6:0)+axis*2+(positive?1:0);Mesh found=meshes[key];if(found!=null)return found;
        found=build(block,axis,positive);builds++;
        // The fixed key space is small; retain a byte guard if the shell gains more detail later.
        long size=found.retainedBytes();if(bytes+size>MAX_CACHED_BYTES){Arrays.fill(meshes,null);bytes=0;}
        if(size<=MAX_CACHED_BYTES){meshes[key]=found;bytes+=size;}return found;
    }
    synchronized void clear(){Arrays.fill(meshes,null);bytes=0;}
    synchronized long retainedBytes(){return bytes;}
    synchronized long builds(){return builds;}
    private static double[] orient(double[] p,int axis){return axis==X?new double[]{p[1],p[2],p[0]}:axis==Z?new double[]{p[0],p[2],p[1]}:p;}
    private static double[] portUV(double[] p,int axis,boolean positive){
        double u=axis==Z&&!positive?1-p[0]:axis==X&&positive?1-p[2]:axis==X?p[2]:p[0];
        double v=axis==Y?(positive?p[2]:1-p[2]):1-p[1];return new double[]{u*16,v*16};
    }
    private static void append(double[] vertices,float[] normals,int[] materials,int quad,int material,float[] normal,double[][] points,int axis,boolean cap,boolean positive){
        materials[quad]=material;System.arraycopy(normal,0,normals,quad*3,3);
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=orient(points[i],axis),texture=cap?portUV(p,axis,positive):uv[i];int at=quad*20+i*5;vertices[at]=p[0];vertices[at+1]=p[1];vertices[at+2]=p[2];vertices[at+3]=texture[0];vertices[at+4]=texture[1];}
    }
    private static Mesh build(boolean block,int axis,boolean positive){
        double[][] outline=block?BLOCK:HEX;int count=outline.length*3,quad=0;double[] vertices=new double[count*20];float[] normals=new float[count*3];int[] materials=new int[count];
        for(int i=0;i<outline.length;i++){
            double[] a=outline[i],c=outline[(i+1)%outline.length];double length=Math.hypot(c[0]-a[0],c[1]-a[1]);double[] n=orient(new double[]{(c[1]-a[1])/length,0,(a[0]-c[0])/length},axis);float[] normal={(float)n[0],(float)n[1],(float)n[2]};
            append(vertices,normals,materials,quad++,Math.abs(normal[1])>.99?CASING:CELLS,normal,new double[][]{{a[0],1,a[1]},{a[0],0,a[1]},{c[0],0,c[1]},{c[0],1,c[1]}},axis,false,false);
        }
        for(int end=0;end<2;end++){
            double[] n=orient(new double[]{0,end==0?-1:1,0},axis);float[] normal={(float)n[0],(float)n[1],(float)n[2]};int material=positive==(end==1)?INPUT:OUTPUT;
            for(int i=0;i<outline.length;i++)append(vertices,normals,materials,quad++,material,normal,new double[][]{{.5,end,.5},{outline[i][0],end,outline[i][1]},{outline[(i+1)%outline.length][0],end,outline[(i+1)%outline.length][1]},{.5,end,.5}},axis,true,end==1);
        }
        return new Mesh(vertices,normals,materials);
    }
}
