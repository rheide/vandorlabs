package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Independent local polygon contracts; real Forge adapter output has its own raw-bit golden. */
public final class PanelMeshEmitterTest {
    private static final class Capture {
        final List<double[][]> faces=new ArrayList<>();
        int ordinary,normal,metal,wall;double half;double[] clip;
        boolean containsY(double y){for(double[][] face:faces)for(double[] point:face)if(point[1]==y)return true;return false;}
    }
    private static final PanelMeshEmitter.Sink<Capture,String> SINK=new PanelMeshEmitter.Sink<Capture,String>(){
        private void add(Capture capture,String material,double[] a,double[] b,double[] c,double[] d){capture.faces.add(new double[][]{a,b,c,d});if(material.equals("metal"))capture.metal++;else if(material.equals("wall"))capture.wall++;else throw new AssertionError("unknown material");}
        public void quad(Capture capture,String material,boolean reverse,double[] a,double[] b,double[] c,double[] d){boolean roof=a[1]==b[1]&&b[1]==c[1]&&c[1]==d[1];require(reverse==(roof?a[1]==16:true),"flat faces retain outward winding");capture.ordinary++;add(capture,material,a,b,c,d);}
        public void normal(Capture capture,String material,int sign,double half,double[] clip,double[] a,double[] b,double[] c,double[] d){capture.normal++;capture.half=half;capture.clip=clip;require(sign==1||sign==-1,"normal direction");add(capture,material,a,b,c,d);}
    };
    private static Capture diagonal(boolean inverted,PanelCornerGeometry.Corner corner,double span,int fill,double half,double lower,double upper,boolean hideLow,boolean hideHigh,double[] clip){Capture capture=new Capture();PanelMeshEmitter.diagonal(SINK,capture,"wall","metal",inverted,corner,span,fill,0,16,half,lower,upper,hideLow,hideHigh,clip);return capture;}
    public static void main(String[] ignored){
        Capture flat=new Capture();PanelMeshEmitter.flat(SINK,flat,"wall","metal",6,null);require(flat.ordinary==6&&flat.normal==0&&flat.wall==2&&flat.metal==4,"flat front/back, four metal edges");
        for(double[][] face:flat.faces)for(double[] point:face)require(point[2]>=6&&point[2]<=10&&point[1]>=0&&point[1]<=16,"flat four-pixel housing");
        Capture joined=new Capture();PanelMeshEmitter.flat(SINK,joined,"wall","metal",6,new PanelCornerGeometry.FlatCorner(0D,12D,false,true,false,false));require(joined.faces.size()>flat.faces.size(),"corner arms retain extra exposed faces");boolean front=false,back=false;for(double[][] face:joined.faces)for(double[] point:face){front|=point[2]==0;back|=point[2]==16;}require(front&&back,"front/back arms span correct edges");
        Capture slope=diagonal(false,null,8,0,Double.NaN,Double.NaN,Double.NaN,false,false,null);require(slope.normal==22&&slope.ordinary==0,"five diagonal bands and two caps");require(slope.faces.get(0)[0][2]==-2,"released lower-end overhang");
        Capture joinedEnd=diagonal(false,null,8,0,Double.NaN,2,10,false,false,null);require(joinedEnd.faces.get(0)[0][2]==2,"lower contact blend");
        Capture hidden=diagonal(false,null,8,0,Double.NaN,Double.NaN,Double.NaN,true,true,null);require(hidden.normal==slope.normal-2,"hidden contacts remove only caps");
        for(int fill=1;fill<4;fill++){Capture filled=diagonal(true,null,16,fill,Double.NaN,Double.NaN,Double.NaN,false,false,null);require(filled.metal==0&&filled.wall==filled.normal,"filled edge and cap material");}
        PanelCornerGeometry.Corner crossing=new PanelCornerGeometry.Corner(false,true,false,true);Capture kink=diagonal(false,crossing,16,0,Double.NaN,Double.NaN,Double.NaN,false,false,null);require(kink.containsY(8),"opposite arms split at center kink");Capture half=diagonal(false,crossing,16,0,0,Double.NaN,Double.NaN,false,false,null);require(!half.containsY(8)&&half.half==0,"half-height transform remains adapter-owned");
        double[] clip={0,0,0,16,16,16};Capture clipped=diagonal(true,null,8,0,8,Double.NaN,Double.NaN,false,false,clip);require(clipped.clip==clip&&clipped.half==8,"clipping/reflection parameters passed without a copied graph");
        System.out.println("PASS: current flat/diagonal polygon slots, winding, four-pixel depth, corner arms, fill caps, center kink, hidden contacts and adapter-owned clipping/reflection");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
