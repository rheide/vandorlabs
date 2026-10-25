package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Authored flat-panel contour faces and four-pixel metal caps, in pixel units. */
public final class FlatWallMesh {
    public static final class Quad {
        public final boolean metal,reverse;
        /** Each row is X/Y/Z/U/V, retaining the legacy contour's UV origin. */
        public final double[][] vertices;
        private Quad(boolean metal,boolean reverse,double[]...vertices){this.metal=metal;this.reverse=reverse;this.vertices=vertices;}
    }
    public final Quad[] quads;
    private FlatWallMesh(List<Quad> quads){this.quads=quads.toArray(new Quad[quads.size()]);}
    public static FlatWallMesh build(double near,FlatWallTopology.Corner corner){
        List<Quad> quads=new ArrayList<Quad>();double[][] outline=FlatWallTopology.outline(near,corner);
        for(int i=0;i<outline.length;i++){
            double[] a=outline[i],b=outline[(i+1)%outline.length];
            if(Math.abs(a[0]-b[0])+Math.abs(a[1]-b[1])<1E-7)continue;
            boolean cap=Math.abs(a[0]-b[0])<1E-7&&Math.abs(Math.abs(a[1]-b[1])-4)<1E-7;
            cap|=Math.abs(a[1]-b[1])<1E-7&&Math.abs(Math.abs(a[0]-b[0])-4)<1E-7&&corner!=null
                    &&((corner.frontArm!=null&&near>0&&a[1]==0)||(corner.backArm!=null&&near+4<16&&a[1]==16));
            boolean acrossX=Math.abs(a[0]-b[0])>Math.abs(a[1]-b[1]);double u0=acrossX?a[0]:a[1],u1=acrossX?b[0]:b[1];
            quads.add(new Quad(cap,true,new double[]{a[0],0,a[1],u0,16},new double[]{b[0],0,b[1],u1,16},
                    new double[]{b[0],16,b[1],u1,0},new double[]{a[0],16,a[1],u0,0}));
        }
        for(int y:new int[]{0,16})for(double[] box:FlatWallTopology.boxes(near,corner)){
            if(box[3]-box[1]<1E-7)continue;
            double x0=box[0],z0=box[1],x1=box[2],z1=box[3];
            quads.add(new Quad(true,y==16,new double[]{x0,y,z0,x0,z0},new double[]{x1,y,z0,x1,z0},
                    new double[]{x1,y,z1,x1,z1},new double[]{x0,y,z1,x0,z1}));
        }
        return new FlatWallMesh(quads);
    }
}
