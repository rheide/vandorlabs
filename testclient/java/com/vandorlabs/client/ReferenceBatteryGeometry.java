package com.vandorlabs.client;

/** TEBattery shell/cap emitter from 9388235f108837487d9fb7ea8abbcfb2b7050982,
 * retaining its vertex/normal/material order and replacing only the graphics sink and facing enum. */
final class ReferenceBatteryGeometry {
    private static final double H=Math.sqrt(3)/4;
    private static final double[][] HEX={{.25,.5-H},{.75,.5-H},{1,.5},{.75,.5+H},{.25,.5+H},{0,.5}},BLOCK={{0,0},{1,0},{1,1},{0,1}};
    private static double[] orient(double[] p,int axis){return axis==0?new double[]{p[1],p[2],p[0]}:axis==2?new double[]{p[0],p[2],p[1]}:p;}
    private static float[] normal(double[][] outline,int edge,int axis){double[] a=outline[edge],b=outline[(edge+1)%outline.length];double length=Math.hypot(b[0]-a[0],b[1]-a[1]);double[] n=orient(new double[]{(b[1]-a[1])/length,0,(a[0]-b[0])/length},axis);return new float[]{(float)n[0],(float)n[1],(float)n[2]};}
    // Facing IDs match Minecraft's DOWN, UP, NORTH, SOUTH, WEST, EAST order.
    private static double[] portUV(double[] p,int cap){double u=cap==2?1-p[0]:cap==5?1-p[2]:cap==4?p[2]:p[0];double v=cap==1?p[2]:cap==0?1-p[2]:1-p[1];return new double[]{u*16,v*16};}
    private static void quad(BatteryGeometryCache.Sink b,int material,double[][] points,float[] normal,int axis){
        b.beginQuad(material,normal[0],normal[1],normal[2]);double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=orient(points[i],axis);b.vertex(p[0],p[1],p[2],uv[i][0],uv[i][1]);}
    }
    static void emit(boolean block,int axis,boolean positive,BatteryGeometryCache.Sink b){
        double[][] outline=block?BLOCK:HEX;
        for(int i=0;i<outline.length;i++){
            double[] a=outline[i],c=outline[(i+1)%outline.length];float[] normal=normal(outline,i,axis);
            quad(b,Math.abs(normal[1])>.99?0:1,new double[][]{{a[0],1,a[1]},{a[0],0,a[1]},{c[0],0,c[1]},{c[0],1,c[1]}},normal,axis);
        }
        for(int end=0;end<2;end++){
            double[] n=orient(new double[]{0,end==0?-1:1,0},axis);float[] normal={(float)n[0],(float)n[1],(float)n[2]};
            boolean input=positive==(end!=0);int material=input?2:3;
            int cap=normal[0]<0?4:normal[0]>0?5:normal[1]<0?0:normal[1]>0?1:normal[2]<0?2:3;
            for(int i=0;i<outline.length;i++){
                b.beginQuad(material,normal[0],normal[1],normal[2]);
                for(double[] p:new double[][]{{.5,end,.5},{outline[i][0],end,outline[i][1]},{outline[(i+1)%outline.length][0],end,outline[(i+1)%outline.length][1]},{.5,end,.5}}){double[] q=orient(p,axis);double[] uv=portUV(q,cap);b.vertex(q[0],q[1],q[2],uv[0],uv[1]);}
            }
        }
    }
}
