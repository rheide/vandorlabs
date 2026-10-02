package com.vandorlabs.client;

import com.vandorlabs.render.TrapdoorGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Clips repeated artwork into its own atlas sprite; geometry remains one rigid leaf. */
final class TrapdoorSurfaceMesh {
    static void draw(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] uv,int light,int choice,int first,int end,boolean tiled,boolean mirror) {
        boolean customDoor=com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice) && CustomBlockTextures.isDoor(choice);
        for(int face=first;face<end;face++) {
            List<double[]> polygon=new ArrayList<>();
            for(int index:TrapdoorGeometry.FACES[face])polygon.add(new double[]{vertices[index][0],vertices[index][1],vertices[index][2],uv[index][0],face<2?uv[index][2]:uv[index][1]});
            double[] a=polygon.get(0),b=polygon.get(1),c=polygon.get(2);
            double nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
            for(double[] point:polygon){minU=Math.min(minU,point[3]);maxU=Math.max(maxU,point[3]);minV=Math.min(minV,point[4]);maxV=Math.max(maxV,point[4]);}
            int u0=tiled?(int)Math.floor(minU):0,u1=tiled?(int)Math.ceil(maxU)-1:0;
            int v0=tiled?(int)Math.floor(minV):0,v1=tiled?(int)Math.ceil(maxV)-1:0;
            for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++)for(int half=0;half<(customDoor?2:1);half++) {
                double low=v+(customDoor?half*.5:0),high=v+(customDoor?(half+1)*.5:1);
                List<double[]> clipped=clip(clip(clip(clip(polygon,3,u,true),3,u+1,false),4,low,true),4,high,false);
                if(clipped.size()<3)continue;
                TextureAtlasSprite part=customDoor?CustomBlockTextures.sprite(choice,half==0):sprite;
                for(int j=1;j+1<clipped.size();j++) {
                    double[] p0=clipped.get(0),p1=clipped.get(j),p2=clipped.get(j+1);
                    double cx=(p1[1]-p0[1])*(p2[2]-p0[2])-(p1[2]-p0[2])*(p2[1]-p0[1]);
                    double cy=(p1[2]-p0[2])*(p2[0]-p0[0])-(p1[0]-p0[0])*(p2[2]-p0[2]);
                    double cz=(p1[0]-p0[0])*(p2[1]-p0[1])-(p1[1]-p0[1])*(p2[0]-p0[0]);
                    if(cx*cx+cy*cy+cz*cz<1e-18)continue;
                    for(double[] point:new double[][]{p0,p1,p2,p2}) {
                        double pu=point[3]-u,pv=point[4]-v;
                        if(mirror && (u&1)!=0)pu=1-pu;
                        if(customDoor)pv=(pv-half*.5)*2;
                        buffer.pos(point[0],point[1],point[2]).color(255,255,255,255).tex(part.getInterpolatedU(pu*16),part.getInterpolatedV(pv*16))
                            .lightmap(light>>>16,light&65535).normal((float)(nx/length),(float)(ny/length),(float)(nz/length)).endVertex();
                    }
                }
            }
        }
    }
    private static List<double[]> clip(List<double[]> polygon,int axis,double edge,boolean greater) {
        List<double[]> out=new ArrayList<>();
        for(int j=0;j<polygon.size();j++) {
            double[] before=polygon.get((j+polygon.size()-1)%polygon.size()),after=polygon.get(j);
            boolean a=greater?before[axis]>=edge:before[axis]<=edge,b=greater?after[axis]>=edge:after[axis]<=edge;
            if(a!=b){double t=(edge-before[axis])/(after[axis]-before[axis]);double[] point=new double[5];for(int k=0;k<5;k++)point[k]=before[k]+t*(after[k]-before[k]);point[axis]=edge;out.add(point);}
            if(b)out.add(after);
        }
        return out;
    }
}
