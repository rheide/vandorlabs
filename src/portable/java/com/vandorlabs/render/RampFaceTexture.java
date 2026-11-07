package com.vandorlabs.render;

import com.vandorlabs.ramp.RampGeometry;
import java.util.ArrayList;
import java.util.List;

/** Released ramp UV layout and repeat clipping, before a platform's atlas and vertex format. */
public final class RampFaceTexture {
    public interface Sink {void vertex(double x,double y,double z,double u,double v);}
    private static final CuboidMesh.Face[] FACES=CuboidMesh.Face.values();
    private RampFaceTexture(){}
    private static boolean x(int face){return face==4||face==5;}
    private static boolean y(int face){return face==0||face==1;}
    private static boolean fits(RampGeometry.Box box,int face,double aspect){return TextureLayoutMode.fit(2,x(face)?box.maxZ-box.minZ:box.maxX-box.minX,y(face)?box.maxZ-box.minZ:box.maxY-box.minY,aspect);}
    public static double u(double value,double shift,RampGeometry.Box box,int face,double aspect,int mode){
        if(mode<1)return value+shift;
        if(mode==2&&fits(box,face,aspect)){double low=x(face)?box.minZ:box.minX,high=x(face)?box.maxZ:box.maxX;return (value-low)/Math.max(1e-12,high-low);}
        return TextureLayoutMode.tileU(value+shift,aspect);
    }
    public static double v(double value,double shift,RampGeometry.Box box,int face,double aspect,int mode,double bottom,double top){
        if(mode<1)return value+shift;
        if(mode==2&&fits(box,face,aspect)){double low=y(face)?box.minZ:Math.min(bottom,top),high=y(face)?box.maxZ:Math.max(bottom,top);return (value-low)/Math.max(1e-12,high-low);}
        return TextureLayoutMode.tileV(value+shift,aspect);
    }
    public static List<double[]> repeats(List<double[]> input,int mode){
        if(mode<1)return input;List<double[]> output=new ArrayList<>();
        for(int q=0;q<input.size();q+=4){
            List<double[]> face=new ArrayList<>(input.subList(q,q+4));double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
            for(double[] p:face){minU=Math.min(minU,p[3]);maxU=Math.max(maxU,p[3]);minV=Math.min(minV,p[4]);maxV=Math.max(maxV,p[4]);}
            for(int u=(int)Math.floor(minU);u<=Math.max((int)Math.floor(minU),(int)Math.ceil(maxU-1e-8)-1);u++)for(int v=(int)Math.floor(minV);v<=Math.max((int)Math.floor(minV),(int)Math.ceil(maxV-1e-8)-1);v++){
                List<double[]> clipped=PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(face,3,u,true),3,u+1,false),4,v,true),4,v+1,false);
                for(int i=1;i+1<clipped.size();i++)for(double[] p:new double[][]{clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1)}){double[] copy=p.clone();copy[3]=Math.max(0,Math.min(1,p[3]-u));copy[4]=Math.max(0,Math.min(1,p[4]-v));output.add(copy);}
            }
        }
        return output;
    }
    public static void emit(RampGeometry.Box box,int face,double aspect,double bottom,double top,double uShift,double vShift,int layout,Sink sink){
        if(face<0||face>5||!Double.isFinite(aspect)||aspect<=0)throw new IllegalArgumentException("Ramp face texture");
        double minU=(x(face)?box.minZ:box.minX)+uShift,maxU=(x(face)?box.maxZ:box.maxX)+uShift;
        double minV=(y(face)?box.minZ:Math.min(bottom,top))+vShift,maxV=(y(face)?box.maxZ:Math.max(bottom,top))+vShift;
        boolean nativeUv=layout<1&&minU>=-1e-8&&maxU<=1+1e-8&&minV>=-1e-8&&maxV<=1+1e-8;
        if(nativeUv)CuboidMesh.emitFace(box,FACES[face],bottom,top,(px,py,pz,pu,pv)->sink.vertex(px,py,pz,u(pu,uShift,box,face,aspect,layout),v(pv,vShift,box,face,aspect,layout,bottom,top)));
        else{List<double[]> vertices=new ArrayList<>();CuboidMesh.emitFace(box,FACES[face],bottom,top,(px,py,pz,pu,pv)->vertices.add(new double[]{px,py,pz,u(pu,uShift,box,face,aspect,layout),v(pv,vShift,box,face,aspect,layout,bottom,top)}));for(double[] p:repeats(vertices,Math.max(1,layout)))sink.vertex(p[0],p[1],p[2],p[3],p[4]);}
    }
}
