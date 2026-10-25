package com.vandorlabs.render;

import com.vandorlabs.render.ControlMount.Point;

/** Physical face layout for configured slab/cube items, independent of the native atlas. */
public final class HousingItemTextureMapping {
    private HousingItemTextureMapping(){}
    public static double[][] map(Point[] positions,double[] u,double[] v,int mode,double aspect){
        double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=maxU,width=0,height=0;
        for(int i=0;i<4;i++){minU=Math.min(minU,u[i]);maxU=Math.max(maxU,u[i]);minV=Math.min(minV,v[i]);maxV=Math.max(maxV,v[i]);}
        for(int a=0;a<4;a++)for(int b=0;b<4;b++){
            double dx=positions[a].x-positions[b].x,dy=positions[a].y-positions[b].y,dz=positions[a].z-positions[b].z,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(Math.abs(v[a]-v[b])<1e-6&&Math.abs(u[a]-u[b])>1e-6)width=Math.max(width,length);
            if(Math.abs(u[a]-u[b])<1e-6&&Math.abs(v[a]-v[b])>1e-6)height=Math.max(height,length);
        }
        double[][] result=new double[4][2];for(int i=0;i<4;i++){result[i][0]=u[i];result[i][1]=v[i];}
        if(width>0&&height>0){boolean fit=TextureLayoutMode.fit(mode,width,height,aspect);for(int i=0;i<4;i++){
            double x=(u[i]-minU)/Math.max(1e-8,maxU-minU),y=(v[i]-minV)/Math.max(1e-8,maxV-minV);
            if(!fit){x=TextureLayoutMode.tileU(x*width,aspect);y=TextureLayoutMode.tileV(y*height,aspect);}result[i][0]=x*16;result[i][1]=y*16;
        }}return result;
    }
}
