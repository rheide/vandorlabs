package com.vandorlabs.render;
import java.util.*;
public final class PanelMeshTransformTest {
    private static final PanelVertexEmitter.RawSink<List<float[]>> RAW=(values,x,y,z,u,v,nx,ny,nz)->values.add(new float[]{(float)x,(float)y,(float)z,(float)u,(float)v,nx,ny,nz});
    private static final PanelTextureMesh.Sink<List<float[]>,String> PIXEL=new PanelTextureMesh.Sink<List<float[]>,String>(){
        public void atlas(List<float[]> out,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){pixel(out,material,x,y,z,u,v,nx,ny,nz);}
        public void pixel(List<float[]> out,String material,double x,double y,double z,double u,double v,float nx,float ny,float nz){need("cap".equals(material),"same cap material");RAW.vertex(out,x,y,z,u,v,nx,ny,nz);}
    };
    private static double area(List<float[]> values){double result=0;for(int q=0;q<values.size();q+=4)for(int i=1;i<3;i++){float[] a=values.get(q),b=values.get(q+i),c=values.get(q+i+1);result+=Math.abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]))/2D;}return result;}
    public static void main(String[] ignored){
        float[] source={0,0,6,0,1,0,0,-1,16,0,6,1,1,0,0,-1,16,16,6,1,0,0,0,-1,0,16,6,0,0,0,0,-1},before=source.clone();
        float[] rotated=PanelMeshTransform.transform(source,1,6,6,.5);need(rotated[0]==4&&rotated[2]==6&&rotated[5]==1&&rotated[7]==0,"local deformation/depth then quarter turn retains normal and donor UV");need(rotated[3]==source[3]&&rotated[4]==source[4],"UV remains attached to donor");
        List<float[]> inside=new ArrayList<float[]>(),outside=new ArrayList<float[]>();double[] bounds={6,10,-1,17,-1,17};PanelMeshTransform.box(RAW,inside,source,0,source.length,bounds,false);PanelMeshTransform.box(RAW,outside,source,0,source.length,bounds,true);need(area(inside)==64&&area(outside)==192,"inside and outside partition whole original face");for(float[] v:inside)need(v[0]>=6&&v[0]<=10&&v[5]==0&&v[7]==-1,"retained normals/bounds");
        List<float[]> cap=new ArrayList<float[]>();PanelMeshTransform.end(PIXEL,cap,"cap",source,8,6,false);need(cap.size()==4,"one exact source crossing cap");for(float[] v:cap)need(v[0]==8&&v[2]>=6&&v[2]<=10&&v[5]==-1&&v[6]==0&&v[7]==0,"cap closes correct plane and direction");need(Arrays.equals(source,before),"donor float records stay owned and unchanged");
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true}){
            float[] central=source.clone();for(int v=0;v<4;v++)central[v*8+2]=(float)(16*DiagonalWallGeometry.near(mode,inverted,central[v*8+1]/16D,Double.NaN,Double.NaN)+2);
            need(PanelMeshTransform.diagonalArms(central,mode,inverted,Double.NaN,Double.NaN).length==0,"all vertices in central band are removed");
            float[] connector=central.clone();connector[2]+=3;need(Arrays.equals(connector,PanelMeshTransform.diagonalArms(connector,mode,inverted,Double.NaN,Double.NaN)),"one outside vertex retains whole original quad including UV and normals");
            float[] snapshot=connector.clone();PanelMeshTransform.diagonalArms(connector,mode,inverted,Double.NaN,Double.NaN);need(Arrays.equals(connector,snapshot),"connector source stays immutable");
        }
        System.out.println("PASS: panel donor transforms, UV ownership, float surface partition and exact opaque-stem closure");
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
