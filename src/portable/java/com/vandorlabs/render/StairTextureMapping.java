package com.vandorlabs.render;

import com.vandorlabs.render.ControlMount.Point;

/** Per-quad stair UV layout and front-relative material selection from the legacy model. */
public final class StairTextureMapping {
    private StairTextureMapping(){}
    public static int localFace(int face,int facing){
        if(face<2)return face;
        int opposite=facing==2?3:facing==3?2:facing==4?5:4;
        int horizontal=opposite==3?0:opposite==4?1:opposite==2?2:3;
        for(int i=0;i<((horizontal+2)&3);i++)face=face==2?4:face==4?3:face==3?5:2;
        return face;
    }
    public static double[][] map(Point[] positions,float[] u,float[] v,int layout,double aspect,int textureFace){
        float lo=16,hi=0,ulo=16,uhi=0;
        for(int i=0;i<4;i++){lo=Math.min(lo,v[i]);hi=Math.max(hi,v[i]);ulo=Math.min(ulo,u[i]);uhi=Math.max(uhi,u[i]);}
        double unitU=1D/16,unitV=1D/16;
        for(int a=0;a<4;a++)for(int b=0;b<4;b++){
            double dx=positions[a].x-positions[b].x,dy=positions[a].y-positions[b].y,dz=positions[a].z-positions[b].z;
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(Math.abs(u[a]-u[b])>1e-6&&Math.abs(v[a]-v[b])<1e-6)unitU=length/Math.abs(u[a]-u[b]);
            if(Math.abs(v[a]-v[b])>1e-6&&Math.abs(u[a]-u[b])<1e-6)unitV=length/Math.abs(v[a]-v[b]);
        }
        boolean fit=TextureLayoutMode.fit(layout,(uhi-ulo)*unitU,(hi-lo)*unitV,aspect);double[][] result=new double[4][2];
        for(int i=0;i<4;i++){
            result[i][0]=layout==2&&fit&&uhi>ulo?(u[i]-ulo)*16/(uhi-ulo):layout==0?u[i]:TextureLayoutMode.tileU(u[i],aspect);
            result[i][1]=fit&&hi>lo&&(layout==2||textureFace>=2)?(v[i]-lo)*16/(hi-lo):layout==0?v[i]:TextureLayoutMode.tileV(v[i],aspect);
        }
        return result;
    }
}
