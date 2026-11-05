package com.vandorlabs.render;

import java.util.*;

public final class PanelOutlineFrameTest {
    private static final PanelOutlineFrame.Textures<String> TEXTURES=new PanelOutlineFrame.Textures<String>(){
        public String edge(String sprite,double[] a,double[] b){return sprite;}
        public boolean authored(String original,String edge){return original.equals("authored");}
        public String map(String sprite,double[][] points){return sprite;}
        public double unU(String sprite,float u){return u;}
        public double unV(String sprite,float v){return v;}
    };
    private static final PanelTextureMesh.Sink<List<double[]>,String> OUTPUT=new PanelTextureMesh.Sink<List<double[]>,String>(){
        public void atlas(List<double[]> out,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){pixel(out,material,x,y,z,u,v,nx,ny,nz);}
        public void pixel(List<double[]> out,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){if(!material.equals("rail")&&!material.equals("authored"))throw new AssertionError("Unexpected exposed rail material");out.add(new double[]{x,y,z,u,v,nx,ny,nz});}
    };
    public static void main(String[] args){
        float n=(float)Math.sqrt(.5);float[] source={0,0,0,0,16,0,n,-n,0,16,16,0,0,0,n,-n,16,16,16,16,0,0,n,-n,16,0,0,16,16,0,n,-n},before=source.clone();
        for(String material:new String[]{"rail","authored"}){
            List<double[]> out=new ArrayList<double[]>();PanelOutlineFrame.emit(OUTPUT,out,TEXTURES,source,material,"pane","inner","outer",false,false,false,false,false,false,1,false);need(out.size()==16,"four exposed edges emit four rails");
            for(double[] v:out){for(double number:v)need(Double.isFinite(number),"finite emitted frame records");need(v[5]==0&&v[6]==n&&v[7]==-n,"source pane normals retained");if(material.equals("authored"))need((v[3]==0||v[3]==16)&&(v[4]==0||v[4]==16),"authored full edge artwork");}
            out.clear();PanelOutlineFrame.emit(OUTPUT,out,TEXTURES,source,material,"pane","inner","outer",true,false,false,false,false,false,1,false);need(out.size()==12,"one joined boundary removes only one rail");
            out.clear();PanelOutlineFrame.emit(OUTPUT,out,TEXTURES,source,material,"pane","inner","outer",true,true,true,true,true,true,1,false);need(out.isEmpty(),"all contacts suppress exposed rails");
        }
        need(Arrays.equals(source,before),"borrowed source records stay unchanged");System.out.println("PASS: outline rail exposure, authored UVs, normals and source ownership");
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
