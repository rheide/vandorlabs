package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Canonical straight wall/porthole faces before platform transforms, in authored pixel units. */
public final class FlatPanelMesh {
    public static final class Quad {
        public final boolean side,reverse;
        public final double[][] vertices;
        private Quad(boolean side,boolean reverse,double[]...vertices){this.side=side;this.reverse=reverse;this.vertices=vertices;}
    }
    private FlatPanelMesh(){}
    /** Boundary flags suppress only rims adjoining compatible porthole cells. */
    public static List<Quad> opaque(PortholeSlice opening,double depthUv,boolean bottom,boolean top,boolean left,boolean right){return build(opening,depthUv,bottom,top,left,right,true);}
    /** Bar ownership clips these complete caps after rotating the source mesh. */
    public static List<Quad> bar(PortholeSlice opening){
        if(opening!=null)return build(opening,4,false,false,false,false,false);
        List<Quad> out=new ArrayList<Quad>();for(FlatWallMesh.Quad quad:FlatWallMesh.build(6,null).quads)out.add(new Quad(quad.metal,quad.reverse,quad.vertices));
        return java.util.Collections.unmodifiableList(out);
    }
    private static List<Quad> build(PortholeSlice opening,double depthUv,boolean bottom,boolean top,boolean left,boolean right,boolean cutRims){
        List<Quad> result=new ArrayList<Quad>();
        if(opening==null)panel(result,new double[]{0,0,16,0,16,16,0,16});
        else {
            for(double[] quad:opening.frameQuads)panel(result,quad);
            for(double[] edge:opening.hexEdges)rim(result,edge[0],edge[1],edge[2],edge[3],depthUv);
        }
        for(int y:new int[]{0,16}){
            if(opening!=null&&(y==0?bottom:top))continue;
            double[] cut=opening==null||!cutRims?null:opening.edgeOpening(1,y);
            if(cut==null)top(result,y,0,16,depthUv);
            else {top(result,y,0,cut[0],depthUv);top(result,y,cut[1],16,depthUv);}
        }
        for(int x:new int[]{0,16}){
            if(opening!=null&&(x==0?left:right))continue;
            double[] cut=opening==null||!cutRims?null:opening.edgeOpening(0,x);
            if(cut==null)side(result,x,0,16,depthUv);
            else {side(result,x,0,cut[0],depthUv);side(result,x,cut[1],16,depthUv);}
        }
        return java.util.Collections.unmodifiableList(result);
    }
    private static void panel(List<Quad> out,double[] quad){for(int z:new int[]{6,10}){
        double[][] points=new double[4][5];for(int i=0;i<4;i++)points[i]=new double[]{quad[i*2],quad[i*2+1],z,quad[i*2],16-quad[i*2+1]};
        out.add(new Quad(false,z==6,points));
    }}
    private static void top(List<Quad> out,double y,double x0,double x1,double depthUv){
        if(x1-x0<1E-7)return;
        out.add(new Quad(true,y==16,new double[]{x0,y,6,x0,0},new double[]{x1,y,6,x1,0},new double[]{x1,y,10,x1,depthUv},new double[]{x0,y,10,x0,depthUv}));
    }
    private static void side(List<Quad> out,double x,double y0,double y1,double depthUv){
        if(y1-y0<1E-7)return;
        out.add(new Quad(true,x==16,new double[]{x,y0,6,0,16-y0},new double[]{x,y0,10,depthUv,16-y0},new double[]{x,y1,10,depthUv,16-y1},new double[]{x,y1,6,0,16-y1}));
    }
    private static void rim(List<Quad> out,double x0,double y0,double x1,double y1,double depthUv){
        double length=Math.hypot(x1-x0,y1-y0);
        for(double start=0;start<length-1E-7;start+=16){
            double end=Math.min(length,start+16),ax=x0+(x1-x0)*start/length,ay=y0+(y1-y0)*start/length,bx=x0+(x1-x0)*end/length,by=y0+(y1-y0)*end/length;
            out.add(new Quad(true,true,new double[]{ax,ay,6,0,0},new double[]{bx,by,6,end-start,0},new double[]{bx,by,10,end-start,depthUv},new double[]{ax,ay,10,0,depthUv}));
        }
    }
    /** Glass UVs deliberately remain normalized for the existing independent pane mapping. */
    public static List<Quad> glass(PortholeSlice opening){
        List<Quad> out=new ArrayList<Quad>();for(int depth:new int[]{7,9})for(double[] quad:opening.glassQuads){
            double[][] points=new double[4][5];for(int i=0;i<4;i++)points[i]=new double[]{quad[i*2],quad[i*2+1],depth,quad[i*2]/16,1-quad[i*2+1]/16};
            out.add(new Quad(false,depth==7,points));
        }return java.util.Collections.unmodifiableList(out);
    }
}
