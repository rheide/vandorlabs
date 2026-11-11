package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable local geometry only: no world, atlas, lighting, buffer or GL objects are retained. */
final class HexTankGeometryCache {
    static final int X=0,Y=1,Z=2;
    static final int CASING=0,LINER=1,PORT=2,GLASS=3;
    static final long MAX_OUTLINE_BYTES=64*1024,MAX_STATIC_BYTES=2*1024*1024;
    private static final double H=Math.sqrt(3)/4,INNER=.90,FLOOR=.04,CEILING=.96;
    private static final double[][] HEX={{.25,.5-H},{.75,.5-H},{1,.5},{.75,.5+H},{.25,.5+H},{0,.5}};
    private static final double[][] BLOCK={{0,0},{1,0},{1,1},{0,1}};
    private static final Outline[] FULL={new Outline(HEX),new Outline(BLOCK)};
    private final int capacity;
    private final LinkedHashMap<Integer,Outline> outlines=new LinkedHashMap<>(16,.75F,true);
    // Two shapes, three axes, two layouts, and the supported one/two/three-block cross sections.
    private final Template[] templates=new Template[36];
    private long outlineBytes,staticBytes,outlineBuilds,templateBuilds;

    HexTankGeometryCache(int capacity){
        if(capacity<=0||capacity>Integer.MAX_VALUE/2-1)throw new IllegalArgumentException("tank capacity");
        this.capacity=capacity;
    }

    /** Read-only points avoid exposing the cached arrays to callers. */
    static final class Outline {
        private final double[] points;
        private Outline(double[][] polygon){points=new double[polygon.length*2];for(int i=0;i<polygon.length;i++){points[i*2]=polygon[i][0];points[i*2+1]=polygon[i][1];}}
        int size(){return points.length/2;}
        double x(int index){return points[index*2];}
        double z(int index){return points[index*2+1];}
        double[][] copy(){double[][] out=new double[size()][2];for(int i=0;i<out.length;i++){out[i][0]=x(i);out[i][1]=z(i);}return out;}
        private long retainedBytes(){return 64L+points.length*8L;}
    }

    interface Sink {
        void beginQuad(int material,int color,boolean diffuse,float nx,float ny,float nz);
        void vertex(double x,double y,double z,double u,double v);
    }

    private static final class Quad {
        final int material,color;
        final boolean diffuse;
        final float nx,ny,nz;
        private final double[] vertices;
        Quad(int material,int color,boolean diffuse,float[] normal,double[][] positions,double[][] uv,int axis){
            this.material=material;this.color=color;this.diffuse=diffuse;nx=normal[0];ny=normal[1];nz=normal[2];
            vertices=new double[20];
            for(int i=0;i<4;i++){
                double[] p=orient(positions[i],axis);int at=i*5;
                vertices[at]=p[0];vertices[at+1]=p[1];vertices[at+2]=p[2];vertices[at+3]=uv[i][0];vertices[at+4]=uv[i][1];
            }
        }
    }

    static final class Mesh {
        private final Quad[] quads;
        private Mesh(List<Quad> source){quads=source.toArray(new Quad[0]);}
        int vertexCount(){return quads.length*4;}
        long retainedBytes(){return 64L+quads.length*256L;}
        void emit(Sink sink){for(Quad q:quads){sink.beginQuad(q.material,q.color,q.diffuse,q.nx,q.ny,q.nz);for(int i=0;i<q.vertices.length;i+=5)sink.vertex(q.vertices[i],q.vertices[i+1],q.vertices[i+2],q.vertices[i+3],q.vertices[i+4]);}}
    }

    static final class Template {
        final Mesh shell,windows;
        private Template(Mesh shell,Mesh windows){this.shell=shell;this.windows=windows;}
        long retainedBytes(){return 64L+shell.retainedBytes()+windows.retainedBytes();}
    }

    static Outline fullOutline(boolean block){return FULL[block?1:0];}

    synchronized Outline fluidOutline(boolean block,int amount){
        int bounded=Math.max(0,Math.min(capacity,amount));
        if(bounded==capacity)return fullOutline(block);
        int key=bounded+(block?capacity+1:0);Outline found=outlines.get(key);
        if(found!=null)return found;
        double[][] polygon=block?BLOCK:HEX;
        double fraction=bounded/(double)capacity,low=0,high=1,total=area(polygon);
        // Preserve the previous volume solver exactly; there is no level quantization.
        for(int i=0;i<40;i++){double mid=(low+high)/2;if(area(below(polygon,mid))<total*fraction)low=mid;else high=mid;}
        found=new Outline(below(polygon,(low+high)/2));outlineBuilds++;
        long bytes=96L+found.retainedBytes();
        Iterator<Map.Entry<Integer,Outline>> oldest=outlines.entrySet().iterator();
        while(outlineBytes+bytes>MAX_OUTLINE_BYTES&&oldest.hasNext()){outlineBytes-=96L+oldest.next().getValue().retainedBytes();oldest.remove();}
        outlines.put(key,found);outlineBytes+=bytes;return found;
    }

    synchronized Template template(boolean block,int axis,boolean tiled,int size){
        if(axis<X||axis>Z||size<1||size>3)throw new IllegalArgumentException("tank geometry key");
        int key=(((block?1:0)*3+axis)*2+(tiled?1:0))*3+size-1;
        Template found=templates[key];if(found!=null)return found;
        found=build(block,axis,tiled,size);templateBuilds++;
        // The finite key space normally fits in full; retain the byte bound if geometry grows.
        long bytes=found.retainedBytes();
        if(staticBytes+bytes>MAX_STATIC_BYTES){Arrays.fill(templates,null);staticBytes=0;}
        if(bytes<=MAX_STATIC_BYTES){templates[key]=found;staticBytes+=bytes;}
        return found;
    }

    synchronized void clear(){outlines.clear();outlineBytes=0;Arrays.fill(templates,null);staticBytes=0;}
    synchronized long outlineBytes(){return outlineBytes;}
    synchronized long staticBytes(){return staticBytes;}
    synchronized long outlineBuilds(){return outlineBuilds;}
    synchronized long templateBuilds(){return templateBuilds;}

    private static double area(double[][] polygon){double sum=0;for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];sum+=a[0]*b[1]-b[0]*a[1];}return Math.abs(sum)*.5;}
    private static double[][] below(double[][] polygon,double level){List<double[]> out=new ArrayList<>();for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];boolean inside=a[1]<=level,next=b[1]<=level;if(inside)out.add(a.clone());if(inside!=next){double t=(level-a[1])/(b[1]-a[1]);out.add(new double[]{a[0]+(b[0]-a[0])*t,level});}}return out.toArray(new double[0][]);}
    private static double[] orient(double[] p,int axis){return axis==X?new double[]{p[1],p[2],p[0]}:axis==Z?new double[]{p[0],p[2],p[1]}:p;}
    private static double[] point(double[][] outline,int edge,double u,double y,double scale){double[] a=outline[edge],c=outline[(edge+1)%outline.length];return new double[]{.5+(a[0]+(c[0]-a[0])*u-.5)*scale,y,.5+(a[1]+(c[1]-a[1])*u-.5)*scale};}
    private static double[] window(double[][] outline,int edge,int axis){
        if(axis!=Y&&(outline.length==4?edge==0||edge==2:edge==0||edge==3))return null;
        double[] a=outline[edge],b=outline[(edge+1)%outline.length];double half=.125/Math.hypot(b[0]-a[0],b[1]-a[1]);return new double[]{.5-half,.5+half,.375,.625};
    }
    private static void quad(List<Quad> out,int material,int color,double[][] vertices,int axis,boolean cap){
        double[][] uv=cap?new double[4][2]:new double[][]{{0,0},{0,16},{16,16},{16,0}};
        if(cap)for(int i=0;i<4;i++){uv[i][0]=vertices[i][0]*16;uv[i][1]=vertices[i][2]*16;}
        out.add(new Quad(material,color,false,new float[]{0,1,0},vertices,uv,axis));
    }
    private static void side(double[][] outline,List<Quad> out,int material,int color,int edge,double left,double right,double bottom,double top,double scale,int axis){quad(out,material,color,new double[][]{point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)},axis,false);}
    private static void cap(double[][] outline,List<Quad> out,int material,double y,double scale,int axis){double cx=0,cz=0;for(double[] p:outline){cx+=p[0];cz+=p[1];}cx=.5+(cx/outline.length-.5)*scale;cz=.5+(cz/outline.length-.5)*scale;for(int i=0;i<outline.length;i++)quad(out,material,0xffffff,new double[][]{{cx,y,cz},point(outline,i,0,y,scale),point(outline,i,1,y,scale),{cx,y,cz}},axis,true);}
    private static double[] casingUV(double[][] outline,int edge,double u,double y,boolean tiled,double scale){double[] a=outline[edge],b=outline[(edge+1)%outline.length];return new double[]{u*(tiled?Math.hypot(b[0]-a[0],b[1]-a[1])*scale:1)*16,(1-y)*16};}
    private static void casingSide(double[][] outline,List<Quad> out,int material,int edge,double left,double right,double bottom,double top,double scale,int axis,boolean tiled,double repeat){
        double[] a=outline[edge],c=outline[(edge+1)%outline.length];double units=Math.hypot(c[0]-a[0],c[1]-a[1])*scale*repeat,begin=left;
        if(tiled&&units>0)for(int tile=(int)Math.floor(left*units)+1;tile<right*units;tile++){double end=tile/units;casingSegment(outline,out,material,edge,begin,end,bottom,top,scale,axis,true,repeat);begin=end;}
        casingSegment(outline,out,material,edge,begin,right,bottom,top,scale,axis,tiled,repeat);
    }
    private static void casingSegment(double[][] outline,List<Quad> out,int material,int edge,double left,double right,double bottom,double top,double scale,int axis,boolean tiled,double repeat){
        double[][] vertices={point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)};
        double[][] uv={casingUV(outline,edge,left,top,tiled,scale*repeat),casingUV(outline,edge,left,bottom,tiled,scale*repeat),casingUV(outline,edge,right,bottom,tiled,scale*repeat),casingUV(outline,edge,right,top,tiled,scale*repeat)};
        double[] a=outline[edge],c=outline[(edge+1)%outline.length];double length=Math.hypot(c[0]-a[0],c[1]-a[1]);
        if(tiled){double base=Math.floor((left+right)*.5*length*scale*repeat)*16;for(double[] p:uv)p[0]-=base;}
        for(double[] p:uv)if(p[0]<-.00001||p[0]>16.00001||p[1]<-.00001||p[1]>16.00001)throw new IllegalStateException("Tank casing crossed its atlas sprite boundary");
        double[] n=orient(new double[]{(c[1]-a[1])/length,0,(a[0]-c[0])/length},axis);
        out.add(new Quad(material,0xffffff,true,new float[]{(float)n[0],(float)n[1],(float)n[2]},vertices,uv,axis));
    }
    private static void wall(double[][] outline,List<Quad> out,int material,int edge,double scale,double low,double high,int axis,boolean tiled,double repeat){
        double[] w=window(outline,edge,axis);if(w==null){casingSide(outline,out,material,edge,0,1,low,high,scale,axis,tiled,repeat);return;}
        casingSide(outline,out,material,edge,0,1,low,w[2],scale,axis,tiled,repeat);casingSide(outline,out,material,edge,0,1,w[3],high,scale,axis,tiled,repeat);
        casingSide(outline,out,material,edge,0,w[0],w[2],w[3],scale,axis,tiled,repeat);casingSide(outline,out,material,edge,w[1],1,w[2],w[3],scale,axis,tiled,repeat);
    }
    private static Template build(boolean block,int axis,boolean tiled,int size){
        double[][] outline=block?BLOCK:HEX;List<Quad> shell=new ArrayList<>(),windows=new ArrayList<>();
        for(int i=0;i<outline.length;i++){
            wall(outline,shell,CASING,i,1,0,1,axis,tiled,size);wall(outline,shell,LINER,i,INNER,FLOOR,CEILING,axis,false,1);
            double[] w=window(outline,i,axis);if(w==null)continue;
            for(double u:new double[]{w[0],w[1]})quad(shell,CASING,0xb5c2cc,new double[][]{point(outline,i,u,w[2],1),point(outline,i,u,w[3],1),point(outline,i,u,w[3],INNER),point(outline,i,u,w[2],INNER)},axis,false);
            for(double h:new double[]{w[2],w[3]})quad(shell,CASING,0xb5c2cc,new double[][]{point(outline,i,w[0],h,1),point(outline,i,w[1],h,1),point(outline,i,w[1],h,INNER),point(outline,i,w[0],h,INNER)},axis,false);
            side(outline,windows,GLASS,0xffffff,i,w[0],w[1],w[2],w[3],1.0005,axis);
        }
        cap(outline,shell,PORT,0,1,axis);cap(outline,shell,PORT,1,1,axis);cap(outline,shell,LINER,FLOOR,INNER,axis);cap(outline,shell,LINER,CEILING,INNER,axis);
        return new Template(new Mesh(shell),new Mesh(windows));
    }
}
