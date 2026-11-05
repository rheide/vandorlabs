package com.vandorlabs.render;
import java.util.Arrays;

/** Current float-record panel donor transforms and surface clipping; targets retain their capture ownership. */
public final class PanelMeshTransform {
    private PanelMeshTransform(){}
    /** Keep entire connector quads outside the sloped central four-pixel body. */
    public static float[] diagonalArms(float[] vertices,int mode,boolean inverted,double lower,double upper){
        float[] result=new float[vertices.length];int used=0;
        for(int q=0;q<vertices.length;q+=32) {
            if(diagonalArm(vertices,q,mode,inverted,lower,upper)){System.arraycopy(vertices,q,result,used,32);used+=32;}
        }
        return Arrays.copyOf(result,used);
    }
    /** Allocation-free whole-quad predicate shared by targets retaining separate material records. */
    public static boolean diagonalArm(float[] vertices,int q,int mode,boolean inverted,double lower,double upper){
        boolean body=true;
        for(int v=0;v<4;v++){
            int p=q+v*8;double near=16*DiagonalWallGeometry.near(mode,inverted,vertices[p+1]/16D,lower,upper);
            body &= vertices[p+2]>=near-1E-4 && vertices[p+2]<=near+4+1E-4;
        }
        return !body;
    }
    /** Remove shared caps while retaining input identity when no complete quad is removed. */
    public static float[] withoutPlane(float[] vertices,int axis,float plane){
        float[] result=new float[vertices.length];int used=0;
        for(int offset=0;offset<vertices.length;offset+=32) {
            boolean onPlane=true;
            for(int v=0;v<4;v++)onPlane &= Math.abs(vertices[offset+v*8+axis]-plane)<1E-5;
            if(!onPlane){System.arraycopy(vertices,offset,result,used,32);used+=32;}
        }
        return used==vertices.length?vertices:Arrays.copyOf(result,used);
    }
    public static float[] transform(float[] vertices,int turns,double depth,double left,double width){
        float[] data=vertices.clone();
        for(int i=0;i<data.length;i+=8) {
            data[i]=(float)(left+data[i]*width);data[i+2]=(float)(data[i+2]+depth);
            data[i+5]=(float)(data[i+5]/width);
            double length=Math.sqrt(data[i+5]*data[i+5]+data[i+6]*data[i+6]+data[i+7]*data[i+7]);
            if(length>1E-7)for(int axis=5;axis<8;axis++)data[i+axis]=(float)(data[i+axis]/length);
            for(int t=0;t<(turns&3);t++) {
                float x=data[i],nx=data[i+5];data[i]=16-data[i+2];data[i+2]=x;
                data[i+5]=-data[i+7];data[i+7]=nx;
            }
        }
        return data;
        }
    public static <C,M> void end(PanelTextureMesh.Sink<C,M> sink,C context,M material,float[] vertices,double cut,double near,boolean right){
        for(int q=0;q<vertices.length;q+=32) {
            if(vertices[q+7]>-.5)continue;
            java.util.List<Double> ys=new java.util.ArrayList<>();
            for(int v=0;v<4;v++) {
                int a=q+v*8,b=q+((v+1)&3)*8;double ax=vertices[a]-cut,bx=vertices[b]-cut;
                if(Math.abs(ax)<1E-5)ys.add((double)vertices[a+1]);
                if(ax*bx<0)ys.add(vertices[a+1]+(-ax)/(bx-ax)*(vertices[b+1]-vertices[a+1]));
            }
            if(ys.size()<2)continue;double low=java.util.Collections.min(ys),high=java.util.Collections.max(ys);
            if(high-low<1E-5)continue;
            double[][] points=right?new double[][]{{cut,low,near},{cut,high,near},{cut,high,near+4},{cut,low,near+4}}
                    :new double[][]{{cut,low,near+4},{cut,high,near+4},{cut,high,near},{cut,low,near}};
            for(int v=0;v<4;v++)sink.pixel(context,material,points[v][0],points[v][1],points[v][2],v<2?0:4,16-points[v][1],right?1:-1,0,0);
        }
        }
    public static <C> void box(PanelVertexEmitter.RawSink<C> sink,C context,float[] vertices,int from,int end,double[] bounds,boolean outside){
        for(int offset=from;offset<end;offset+=32) {
            java.util.List<float[]> polygon=new java.util.ArrayList<>();
            for(int v=0;v<4;v++)polygon.add(Arrays.copyOfRange(vertices,offset+v*8,offset+(v+1)*8));
            for(int plane=0;plane<6 && !polygon.isEmpty();plane++) {
                int axis=plane/2;boolean above=(plane&1)==0;
                if(outside) {
                    boolean extendsOutside=false;
                    for(float[] point:polygon)extendsOutside|=(point[axis]-bounds[plane])*(above?1:-1)<-1E-6;
                    if(extendsOutside)emitPolygon(sink,context,clipPanel(polygon,axis,bounds[plane],!above));
                }
                polygon=clipPanel(polygon,axis,bounds[plane],above);
            }
            if(!outside)emitPolygon(sink,context,polygon);
        }
        }
    private static java.util.List<float[]> clipPanel(java.util.List<float[]> polygon,int axis,double plane,boolean above) {
        java.util.List<float[]> result=new java.util.ArrayList<>();if(polygon.isEmpty())return result;
        float[] a=polygon.get(polygon.size()-1);double da=(a[axis]-plane)*(above?1:-1);
        for(float[] b:polygon) {
            double db=(b[axis]-plane)*(above?1:-1);boolean ia=da>=0,ib=db>=0;
            if(ia!=ib) {float[] v=new float[8];double t=da/(da-db);for(int k=0;k<8;k++)v[k]=(float)(a[k]+t*(b[k]-a[k]));result.add(v);}
            if(ib)result.add(b);a=b;da=db;
        }
        return result;
    }
    private static <C> void emitPolygon(PanelVertexEmitter.RawSink<C> sink,C context,java.util.List<float[]> polygon) {
        for(int v=1;v+1<polygon.size();v++) {
            float[] a=polygon.get(0),b=polygon.get(v),c=polygon.get(v+1);
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            if(Math.abs(uy*vz-uz*vy)+Math.abs(uz*vx-ux*vz)+Math.abs(ux*vy-uy*vx)<1E-7)continue;
            emit(sink,context,a);emit(sink,context,b);emit(sink,context,c);emit(sink,context,c);
        }
    }
    private static <C> void emit(PanelVertexEmitter.RawSink<C> sink,C context,float[] value){sink.vertex(context,value[0],value[1],value[2],value[3],value[4],value[5],value[6],value[7]);}
}
