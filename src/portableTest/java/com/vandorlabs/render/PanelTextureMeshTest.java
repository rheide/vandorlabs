package com.vandorlabs.render;

import java.util.*;

public final class PanelTextureMeshTest {
    private static final class Capture {final List<double[]> points=new ArrayList<>();int atlas,pixel;}
    private static final PanelTextureMesh.Sink<Capture,String> SINK=new PanelTextureMesh.Sink<Capture,String>(){
        public void atlas(Capture capture,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){capture.atlas++;capture.points.add(new double[]{x,y,z,u,v,nx,ny,nz});}
        public void pixel(Capture capture,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){capture.pixel++;capture.points.add(new double[]{x,y,z,u,v,nx,ny,nz});}
    };
    private static float[] source(){return new float[]{0,0,0,.25F,.75F,0,0,1,48,0,0,.5F,.75F,0,0,1,48,16,0,.5F,.5F,0,0,1,0,16,0,.25F,.5F,0,0,1};}
    private static double area(List<double[]> points){double area=0;for(int q=0;q<points.size();q+=4)for(int i=1;i<3;i++){double[] a=points.get(q),b=points.get(q+i),c=points.get(q+i+1);area+=Math.abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]))/2;}return area;}
    public static void main(String[] ignored){
        float[] source=source(),before=source.clone();Capture tiled=new Capture();PanelTextureMesh.map(SINK,tiled,"wall",source,.25F,.5F,.5F,.75F,1,0,0,1,1,false,false,false,0,0,1);require(tiled.pixel==24&&tiled.atlas==0,"three repeats, each split into two triangles");require(area(tiled.points)==768,"repeat clipping preserves area");for(double[] point:tiled.points)require(point[3]>=0&&point[3]<=16&&point[4]>=0&&point[4]<=16&&point[7]==1,"UV tile bounds and normal payload");
        Capture fitted=new Capture();PanelTextureMesh.map(SINK,fitted,"wall",source,.25F,.5F,.5F,.75F,3,0,0,1,1,false,false,false,0,0,2);require(fitted.pixel==8&&area(fitted.points)==768,"aspect Fit keeps one complete surface");
        Capture flipped=new Capture();PanelTextureMesh.map(SINK,flipped,"wall",source,.25F,.5F,.5F,.75F,1,1,2,2,3,true,true,false,0,0,1);require(flipped.pixel==24&&area(flipped.points)==768,"offset and mirrored group retains repeat area");
        float[] outside=source();for(int i=0;i<4;i++)outside[i*8+3]+=.5F;Capture excluded=new Capture();PanelTextureMesh.map(SINK,excluded,"wall",outside,.25F,.5F,.5F,.75F,1,0,0,1,1,false,false,false,0,0,1);require(excluded.atlas==4&&excluded.pixel==0,"other atlas material is copied unchanged");for(int i=0;i<4;i++)require(excluded.points.get(i)[3]==outside[i*8+3],"raw atlas coordinate preserved");require(Arrays.equals(before,source),"input mesh remains owned/unchanged");
        System.out.println("PASS: panel UV subdivision repeat/fit/group mirroring, exact area, bounded atlas coordinates, original normals and unchanged foreign material/input records");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
