package com.vandorlabs.render;

import java.util.*;

/** Immutable released diagonal Screen inventory silhouette and optional control-panel artwork. */
public final class ScreenDiagonalItemMesh {
    public static final class Quad {
        public final String texture;private final double[] values;
        private Quad(String texture,double[][] points){this.texture=texture;values=new double[20];for(int i=0;i<4;i++)System.arraycopy(points[i],0,values,i*5,5);}
        public double value(int vertex,int field){return values[vertex*5+field];}
        public int face(){
            double ux=value(1,0)-value(0,0),uy=value(1,1)-value(0,1),uz=value(1,2)-value(0,2),vx=value(2,0)-value(0,0),vy=value(2,1)-value(0,1),vz=value(2,2)-value(0,2);
            float x=(float)(uy*vz-uz*vy),y=(float)(uz*vx-ux*vz),z=(float)(ux*vy-uy*vx),best=Float.MIN_VALUE;int face=2;
            float[] dots={-y,y,-z,z,-x,x};for(int i=0;i<6;i++)if(dots[i]>best){best=dots[i];face=i;}return face;
        }
    }
    private static final List<Quad> SCREEN=build(false),CONTROLS=build(true);
    public static List<Quad> quads(boolean controls){return controls?CONTROLS:SCREEN;}
    private static List<Quad> build(boolean controls){
        List<Quad> result=new ArrayList<>();
        for(ScreenHousingMesh.Face face:ScreenHousingMesh.diagonal(false).quads){double[][] points=new double[4][5];for(int i=0;i<4;i++){ScreenHousingMesh.Vertex v=face.vertices[(4-i)&3];points[i]=new double[]{v.x,v.y,v.z,v.u,v.v};}result.add(new Quad("vandorlabs:blocks/programmable_glass/door_side",points));}
        ScreenSurface.Quad surface=ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false);ScreenSurface.Vertex[] vertices={surface.topLeft,surface.topRight,surface.bottomRight,surface.bottomLeft};double[][] points=new double[4][5];
        for(int i=0;i<4;i++){ScreenSurface.Vertex v=vertices[i];points[i]=new double[]{v.x,v.y+.01,v.z-.01,i==0||i==3?16:0,i<2?0:16};}
        result.add(new Quad(controls?"minecraft:blocks/concrete_black":"vandorlabs:blocks/unified/screen_bridge_bare_engineering_static",points));
        if(controls){result.add(panel(surface,3,3,125,20,.03,"concrete_cyan"));for(int row=0;row<3;row++){int y=24+row*12;result.add(panel(surface,8,y+3,70-row*10,y+7,.04,"concrete_light_blue"));result.add(panel(surface,98,y,120,y+10,.04,row==0?"concrete_lime":"concrete_cyan"));}}
        return Collections.unmodifiableList(result);
    }
    private static Quad panel(ScreenSurface.Quad surface,int left,int top,int right,int bottom,double offset,String texture){
        double[][] points=new double[4][5];int[] xs={right,left,left,right},ys={top,top,bottom,bottom};
        for(int i=0;i<4;i++)points[i]=new double[]{surface.topRight.x+(surface.topLeft.x-surface.topRight.x)*xs[i]/128,surface.topRight.y+(surface.bottomRight.y-surface.topRight.y)*ys[i]/128+surface.ny*offset,surface.topRight.z+(surface.bottomRight.z-surface.topRight.z)*ys[i]/128+surface.nz*offset,i==0||i==3?0:16,i<2?0:16};
        return new Quad("minecraft:blocks/"+texture,points);
    }
    private ScreenDiagonalItemMesh(){}
}
