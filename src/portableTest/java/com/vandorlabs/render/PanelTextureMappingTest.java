package com.vandorlabs.render;

import java.util.Arrays;

public final class PanelTextureMappingTest {
    private static int checks;
    public static void main(String[] ignored){
        for(int span=1;span<=3;span++)for(int offset=0;offset<span;offset++)for(double value:new double[]{-.5,-0D,0,.125,.5,1,1.5,Double.POSITIVE_INFINITY,Double.NaN})for(double border:new double[]{0,.0625,.5})for(double unit:new double[]{.0625,.5,1,3})for(double aspect:new double[]{.5,1,2})for(boolean fit:new boolean[]{false,true}){
            double u=fit?(offset+value-border)/(span-2*border):TextureLayoutMode.tileU((offset+value)*unit,aspect),v=fit?(span-1-offset+value-border)/(span-2*border):TextureLayoutMode.tileV((span-1-offset+value)*unit,aspect);if(fit){u=Math.max(0,Math.min(1,u));v=Math.max(0,Math.min(1,v));}same(u,PanelTextureMapping.u(fit,value,offset,span,border,unit,aspect));same(v,PanelTextureMapping.v(fit,value,offset,span,border,unit,aspect));
        }
        double[][] top={{0,12,6,0,0},{16,12,6,16,0},{16,16,10,16,4},{0,16,10,0,4}};require(FrameEdgeMapping.edge(top)==FrameEdgeMapping.TOP,"top edge");FrameEdgeMapping.map(FrameEdgeMapping.TOP,top);same(top[0][3],0);same(top[1][3],16);same(top[0][4],0);same(top[2][4],16);
        double[][] left={{0,0,6,0,0},{4,0,6,4,0},{4,16,10,4,16},{0,16,10,0,16}};require(FrameEdgeMapping.edge(left)==FrameEdgeMapping.LEFT,"left edge");FrameEdgeMapping.map(FrameEdgeMapping.LEFT,left);same(left[0][3],16);same(left[2][3],0);same(left[0][4],0);same(left[1][4],16);
        double[][] tie={{4,4,0,4,4},{12,4,0,4,4},{12,12,0,4,4},{4,12,0,4,4}};require(FrameEdgeMapping.edge(tie)==FrameEdgeMapping.TOP,"equal spans and center ties retain top");FrameEdgeMapping.map(FrameEdgeMapping.TOP,tie);for(double[] point:tie)same(point[4],0);
        double[] a={0,0,0,0,0,0,0,1},b={2,0,0,1,0,0,0,1},c={2,2,0,1,1,0,0,1},d={0,2,0,0,1,0,0,1};java.util.List<double[]> cut=PanelTextureMapping.clip(Arrays.asList(a,b,c,d),3,.5,true);require(cut.size()==4&&cut.contains(b)&&cut.contains(c),"clipping retains original inside points");int intersections=0;for(double[] point:cut){require(point[3]>=.5,"clip plane");if(point[3]==.5){same(point[0],1);same(point[7],1);intersections++;}}require(intersections==2,"two interpolated boundaries");
        System.out.println("PASS: panel texture scalar raw bits "+checks+"; groups/row origin/borders/fit/repeats, directional rail covariance/ties and attribute-preserving UV clipping");
    }
    private static void same(double a,double b){require(Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b),"released scalar arithmetic");}
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
