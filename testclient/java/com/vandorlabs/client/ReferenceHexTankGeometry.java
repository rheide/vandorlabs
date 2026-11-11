package com.vandorlabs.client;

/** Frozen TEHexTank geometry from 9388235f108837487d9fb7ea8abbcfb2b7050982.
 * Only the graphics sink and facing enum have been replaced for CPU comparison;
 * pixel UVs, vertex order, normals, materials and the volume solver are unchanged. */
final class ReferenceHexTankGeometry {
    private static final double H=Math.sqrt(3)/4;
    private static final double[][] OUTLINE={{.25,.5-H},{.75,.5-H},{1,.5},{.75,.5+H},{.25,.5+H},{0,.5}};
    private static final double[][] BLOCK={{0,0},{1,0},{1,1},{0,1}};
    private static final double INNER=.90, LIQUID=.895, FLOOR=.04, CEILING=.96;
    /** Horizontal hexagons retain flat top/bottom faces, like hexagonal thrusters. */
    static double[] orient(double[] p,int f){return f==0?new double[]{p[1],p[2],p[0]}:f==2?new double[]{p[0],p[2],p[1]}:p;}
    static double fluidLevel(int amount){return FLOOR+(CEILING-FLOOR)*Math.max(0,Math.min(1,amount/(double)16000));}
    private static double[] point(double[][] outline,int edge,double u,double y,double scale){double[] a=outline[edge],c=outline[(edge+1)%outline.length];return new double[]{.5+(a[0]+(c[0]-a[0])*u-.5)*scale,y,.5+(a[1]+(c[1]-a[1])*u-.5)*scale};}
    private static void quad(HexTankGeometryCache.Sink b,int s,int color,double[][] vertices,int facing,boolean cap){
        b.beginQuad(s,color,false,0,1,0);
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=orient(vertices[i],facing);b.vertex(p[0],p[1],p[2],cap?vertices[i][0]*16:uv[i][0],cap?vertices[i][2]*16:uv[i][1]);}
    }
    private static void side(double[][] outline,HexTankGeometryCache.Sink b,int s,int color,int edge,double left,double right,double bottom,double top,double scale,int facing){quad(b,s,color,new double[][]{point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)},facing,false);}
    private static void cap(double[][] outline,HexTankGeometryCache.Sink b,int s,int color,double y,double scale,int facing){double cx=0,cz=0;for(double[] p:outline){cx+=p[0];cz+=p[1];}cx=.5+(cx/outline.length-.5)*scale;cz=.5+(cz/outline.length-.5)*scale;for(int i=0;i<outline.length;i++)quad(b,s,color,new double[][]{{cx,y,cz},point(outline,i,0,y,scale),point(outline,i,1,y,scale),{cx,y,cz}},facing,true);}
    static double[] window(boolean block,int edge,int facing){
        if(facing!=1 && (block?edge==0||edge==2:edge==0||edge==3))return null;
        double[][] outline=block?BLOCK:OUTLINE;double[] a=outline[edge],b=outline[(edge+1)%outline.length];
        double half=.125/Math.hypot(b[0]-a[0],b[1]-a[1]);return new double[]{.5-half,.5+half,.375,.625};
    }
    static double[] casingUV(double[][] outline,int edge,double u,double y,boolean tiled,double scale){
        double[] a=outline[edge],b=outline[(edge+1)%outline.length];return new double[]{u*(tiled?Math.hypot(b[0]-a[0],b[1]-a[1])*scale:1)*16,(1-y)*16};
    }
    static float[] casingNormal(double[][] outline,int edge,int facing){
        double[] a=outline[edge],b=outline[(edge+1)%outline.length];double length=Math.hypot(b[0]-a[0],b[1]-a[1]);double[] n=orient(new double[]{(b[1]-a[1])/length,0,(a[0]-b[0])/length},facing);return new float[]{(float)n[0],(float)n[1],(float)n[2]};
    }
    private static void casingSide(double[][] outline,HexTankGeometryCache.Sink b,int s,int color,int edge,double left,double right,double bottom,double top,double scale,int facing,boolean tiled,double repeat){
        double[] a=outline[edge],c=outline[(edge+1)%outline.length];double units=Math.hypot(c[0]-a[0],c[1]-a[1])*scale*repeat;
        double begin=left;
        if(tiled&&units>0)for(int tile=(int)Math.floor(left*units)+1;tile<right*units;tile++){
            double end=tile/units;casingSegment(outline,b,s,color,edge,begin,end,bottom,top,scale,facing,true,repeat);begin=end;
        }
        casingSegment(outline,b,s,color,edge,begin,right,bottom,top,scale,facing,tiled,repeat);
    }
    private static void casingSegment(double[][] outline,HexTankGeometryCache.Sink b,int s,int color,int edge,double left,double right,double bottom,double top,double scale,int facing,boolean tiled,double repeat){
        double[][] vertices={point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)};
        double[][] uv={casingUV(outline,edge,left,top,tiled,scale*repeat),casingUV(outline,edge,left,bottom,tiled,scale*repeat),casingUV(outline,edge,right,bottom,tiled,scale*repeat),casingUV(outline,edge,right,top,tiled,scale*repeat)};
        if(tiled){double[] a=outline[edge],c=outline[(edge+1)%outline.length];double base=Math.floor((left+right)*.5*Math.hypot(c[0]-a[0],c[1]-a[1])*scale*repeat)*16;for(double[] point:uv)point[0]-=base;}
        if(System.getProperty("vandorlabs.reprolab")!=null)for(double[] point:uv)if(point[0]<-.00001||point[0]>16.00001||point[1]<-.00001||point[1]>16.00001)throw new IllegalStateException("Tank casing crossed its atlas sprite boundary");
        float[] normal=casingNormal(outline,edge,facing);b.beginQuad(s,color,true,normal[0],normal[1],normal[2]);
        for(int i=0;i<4;i++){double[] p=orient(vertices[i],facing);b.vertex(p[0],p[1],p[2],uv[i][0],uv[i][1]);}
    }
    private static void wall(double[][] outline,HexTankGeometryCache.Sink b,int s,int color,int edge,double scale,double low,double high,int facing,boolean tiled,double repeat){
        double[] w=window(outline.length==4,edge,facing);if(w==null){casingSide(outline,b,s,color,edge,0,1,low,high,scale,facing,tiled,repeat);return;}
        casingSide(outline,b,s,color,edge,0,1,low,w[2],scale,facing,tiled,repeat);casingSide(outline,b,s,color,edge,0,1,w[3],high,scale,facing,tiled,repeat);
        casingSide(outline,b,s,color,edge,0,w[0],w[2],w[3],scale,facing,tiled,repeat);casingSide(outline,b,s,color,edge,w[1],1,w[2],w[3],scale,facing,tiled,repeat);
    }
    static double area(double[][] polygon){double sum=0;for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];sum+=a[0]*b[1]-b[0]*a[1];}return Math.abs(sum)*.5;}
    private static double[][] below(double[][] polygon,double level){java.util.List<double[]> out=new java.util.ArrayList<>();for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];boolean inside=a[1]<=level,next=b[1]<=level;if(inside)out.add(a.clone());if(inside!=next){double t=(level-a[1])/(b[1]-a[1]);out.add(new double[]{a[0]+(b[0]-a[0])*t,level});}}return out.toArray(new double[0][]);}
    static double[][] fluidOutline(boolean block,int amount){double[][] polygon=block?BLOCK:OUTLINE;double fraction=Math.max(0,Math.min(1,amount/(double)16000)),low=0,high=1,total=area(polygon);if(fraction==1)return polygon;for(int i=0;i<40;i++){double mid=(low+high)/2;if(area(below(polygon,mid))<total*fraction)low=mid;else high=mid;}return below(polygon,(low+high)/2);}
    static void emit(boolean block,int facing,boolean tiled,int size,boolean glass,HexTankGeometryCache.Sink b){
        double[][] outline=block?BLOCK:OUTLINE;int casing=0,liner=1,port=2;
        if(glass){for(int i=0;i<outline.length;i++){double[] w=window(block,i,facing);if(w!=null)side(outline,b,3,0xffffff,i,w[0],w[1],w[2],w[3],1.0005,facing);}return;}
            for(int i=0;i<outline.length;i++){
                wall(outline,b,casing,0xffffff,i,1,0,1,facing,tiled,size);wall(outline,b,liner,0xffffff,i,INNER,FLOOR,CEILING,facing,false,1);
                double[] w=window(block,i,facing);if(w==null)continue;
                // Thickness around the four edges of every window opening.
                for(double u:new double[]{w[0],w[1]})quad(b,casing,0xb5c2cc,new double[][]{point(outline,i,u,w[2],1),point(outline,i,u,w[3],1),point(outline,i,u,w[3],INNER),point(outline,i,u,w[2],INNER)},facing,false);
                for(double h:new double[]{w[2],w[3]})quad(b,casing,0xb5c2cc,new double[][]{point(outline,i,w[0],h,1),point(outline,i,w[1],h,1),point(outline,i,w[1],h,INNER),point(outline,i,w[0],h,INNER)},facing,false);
            }
            cap(outline,b,port,0xffffff,0,1,facing);cap(outline,b,port,0xffffff,1,1,facing);
            cap(outline,b,liner,0xffffff,FLOOR,INNER,facing);cap(outline,b,liner,0xffffff,CEILING,INNER,facing);
    }
}
