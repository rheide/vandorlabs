package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

public final class PanelVertexEmitterTest {
    private static final PanelVertexEmitter.RawSink<List<double[]>> RAW=new PanelVertexEmitter.RawSink<List<double[]>>(){
        public void vertex(List<double[]> points,double x,double y,double z,double u,double v,float nx,float ny,float nz){points.add(new double[]{x,y,z,u,v,nx,ny,nz});}
    };
    private static final PanelVertexEmitter.PixelSink<List<double[]>,String> PIXEL=new PanelVertexEmitter.PixelSink<List<double[]>,String>(){
        public void vertex(List<double[]> points,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){require(material.equals("wall"),"material slot");RAW.vertex(points,x,y,z,u,v,nx,ny,nz);}
    };
    public static void main(String[] ignored){
        List<double[]> front=new ArrayList<>(),back=new ArrayList<>();PanelVertexEmitter.raw(RAW,front,false,0,0,0,0,0,1,0,0,1,0,1,1,0,1,1,0,1,0,0,1);PanelVertexEmitter.raw(RAW,back,true,0,0,0,0,0,1,0,0,1,0,1,1,0,1,1,0,1,0,0,1);require(front.size()==4&&back.size()==4,"quad count");require(front.get(1)[0]==1&&front.get(1)[1]==0&&front.get(0)[7]==1,"front order/normal");require(back.get(1)[0]==0&&back.get(1)[1]==1&&back.get(0)[7]==-1,"reverse order/normal");for(double[] p:back)require(p[0]==p[3]&&p[1]==p[4],"UV remains attached");
        List<double[]> tiny=new ArrayList<>();double t=1e-5;PanelVertexEmitter.raw(RAW,tiny,false,0,0,0,0,0,t,0,0,1,0,t,t,0,1,1,0,t,0,0,1);require(tiny.size()==4,"released immediate epsilon");List<double[]> skipped=new ArrayList<>();PanelVertexEmitter.normal(PIXEL,skipped,"wall",1,Double.NaN,null,new double[]{0,0,0,0,0},new double[]{t,0,0,16,0},new double[]{t,t,0,16,16},new double[]{0,t,0,0,16});require(skipped.isEmpty(),"released diagonal epsilon remains distinct");
        List<double[]> reflected=new ArrayList<>();PanelVertexEmitter.normal(PIXEL,reflected,"wall",1,8,null,new double[]{0,0,6,-1,18},new double[]{16,0,6,16,0},new double[]{16,16,10,16,16},new double[]{0,16,10,0,16});require(reflected.size()==4&&reflected.get(0)[1]==14&&reflected.get(0)[2]==0&&reflected.get(1)[1]==18&&reflected.get(1)[2]==16,"half reflection and reversed winding");require(reflected.get(0)[6]>0&&reflected.get(0)[7]<0,"reflected normal direction");require(reflected.get(0)[3]==0&&reflected.get(0)[4]==16,"diagonal pixel UV clamp");
        List<double[]> cap=new ArrayList<>();PanelVertexEmitter.normal(PIXEL,cap,"wall",1,Double.NaN,new double[]{0,0,0,16,16,16},new double[]{16,0,0,0,0},new double[]{16,16,0,16,0},new double[]{16,16,4,16,16},new double[]{16,0,4,0,16});require(cap.size()==4,"contact cap retained at exact cell plane");
        System.out.println("PASS: panel scalar vertex winding/normals, attached UVs, distinct tiny-face thresholds, half-height reflection, UV clamp and exact contact cap");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
