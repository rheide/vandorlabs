package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Legacy float-packed panel transforms and clipping; XYZ remain in authored pixel units. */
public final class PanelMeshTransform {
    private PanelMeshTransform(){}
    public static float[] transform(float[] source,int turns,double depth,double left,double width){
        float[] data=source.clone();
        for(int i=0;i<data.length;i+=8){
            data[i]=(float)(left+data[i]*width);data[i+2]=(float)(data[i+2]+depth);data[i+5]=(float)(data[i+5]/width);
            double length=Math.sqrt(data[i+5]*data[i+5]+data[i+6]*data[i+6]+data[i+7]*data[i+7]);
            if(length>1E-7)for(int axis=5;axis<8;axis++)data[i+axis]=(float)(data[i+axis]/length);
            for(int t=0;t<(turns&3);t++){float x=data[i],nx=data[i+5];data[i]=16-data[i+2];data[i+2]=x;data[i+5]=-data[i+7];data[i+7]=nx;}
        }return data;
    }
    public static float[] box(float[] source,double x0,double y0,double z0,double x1,double y1,double z1,boolean outside){
        List<float[]> result=new ArrayList<float[]>();double[] bounds={x0,x1,y0,y1,z0,z1};
        for(int offset=0;offset<source.length;offset+=32){
            List<float[]> polygon=new ArrayList<float[]>();for(int v=0;v<4;v++)polygon.add(Arrays.copyOfRange(source,offset+v*8,offset+(v+1)*8));
            for(int plane=0;plane<6&&!polygon.isEmpty();plane++){
                int axis=plane/2;boolean above=(plane&1)==0;
                if(outside){boolean extendsOutside=false;for(float[] point:polygon)extendsOutside|=(point[axis]-bounds[plane])*(above?1:-1)<-1E-6;
                    if(extendsOutside)emitPolygon(result,clip(polygon,axis,bounds[plane],!above));}
                polygon=clip(polygon,axis,bounds[plane],above);
            }
            if(!outside)emitPolygon(result,polygon);
        }return flatten(result);
    }
    private static List<float[]> clip(List<float[]> polygon,int axis,double plane,boolean above){
        List<float[]> result=new ArrayList<float[]>();if(polygon.isEmpty())return result;
        float[] a=polygon.get(polygon.size()-1);double da=(a[axis]-plane)*(above?1:-1);
        for(float[] b:polygon){double db=(b[axis]-plane)*(above?1:-1);boolean ia=da>=0,ib=db>=0;
            if(ia!=ib){float[] v=new float[8];double t=da/(da-db);for(int k=0;k<8;k++)v[k]=(float)(a[k]+t*(b[k]-a[k]));result.add(v);}
            if(ib)result.add(b);a=b;da=db;
        }return result;
    }
    private static void emitPolygon(List<float[]> result,List<float[]> polygon){
        for(int v=1;v+1<polygon.size();v++){
            float[] a=polygon.get(0),b=polygon.get(v),c=polygon.get(v+1);
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            if(Math.abs(uy*vz-uz*vy)+Math.abs(uz*vx-ux*vz)+Math.abs(ux*vy-uy*vx)<1E-7)continue;
            result.add(a);result.add(b);result.add(c);result.add(c);
        }
    }
    /** New cap UVs are normalized for the selected metal sprite, independent of input UV space. */
    public static float[] end(float[] source,double cut,double near,boolean right){
        List<float[]> result=new ArrayList<float[]>();
        for(int q=0;q<source.length;q+=32){
            if(source[q+7]>-.5)continue;List<Double> ys=new ArrayList<Double>();
            for(int v=0;v<4;v++){int a=q+v*8,b=q+((v+1)&3)*8;double ax=source[a]-cut,bx=source[b]-cut;
                if(Math.abs(ax)<1E-5)ys.add((double)source[a+1]);
                if(ax*bx<0)ys.add(source[a+1]+(-ax)/(bx-ax)*(source[b+1]-source[a+1]));
            }
            if(ys.size()<2)continue;double low=Collections.min(ys),high=Collections.max(ys);if(high-low<1E-5)continue;
            double[][] points=right?new double[][]{{cut,low,near},{cut,high,near},{cut,high,near+4},{cut,low,near+4}}
                    :new double[][]{{cut,low,near+4},{cut,high,near+4},{cut,high,near},{cut,low,near}};
            for(int v=0;v<4;v++)result.add(new float[]{(float)points[v][0],(float)points[v][1],(float)points[v][2],v<2?0:.25F,(float)((16-points[v][1])/16),right?1:-1,0,0});
        }return flatten(result);
    }
    private static float[] flatten(List<float[]> vertices){float[] result=new float[vertices.size()*8];for(int i=0;i<vertices.size();i++)System.arraycopy(vertices.get(i),0,result,i*8,8);return result;}
}
