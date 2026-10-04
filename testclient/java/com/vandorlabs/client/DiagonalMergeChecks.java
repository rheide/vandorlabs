package com.vandorlabs.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.model.pipeline.LightUtil;
import java.util.*;

/** Independent triangle sampling checks surface coverage and interpolated attributes. */
final class DiagonalMergeChecks {
    private static final class Triangle {
        final TextureAtlasSprite sprite;
        final double[][] p;
        Triangle(TextureAtlasSprite sprite,double[] a,double[] b,double[] c){this.sprite=sprite;p=new double[][]{a,b,c};}
        double area(){
            double ux=p[1][0]-p[0][0],uy=p[1][1]-p[0][1],uz=p[1][2]-p[0][2];
            double vx=p[2][0]-p[0][0],vy=p[2][1]-p[0][1],vz=p[2][2]-p[0][2];
            double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
            return Math.sqrt(x*x+y*y+z*z)/2;
        }
        boolean contains(double[] point) {
            double uu=0,vv=0,uv=0,wu=0,wv=0;
            for(int i=0;i<3;i++) {
                double u=p[1][i]-p[0][i],v=p[2][i]-p[0][i],w=point[i]-p[0][i];
                uu+=u*u;vv+=v*v;uv+=u*v;wu+=w*u;wv+=w*v;
            }
            double den=uu*vv-uv*uv;if(den<1E-16)return false;
            double s=(wu*vv-wv*uv)/den,t=(wv*uu-wu*uv)/den;
            if(s<-1E-6 || t<-1E-6 || s+t>1+1E-6)return false;
            for(int i=0;i<8;i++)if(Math.abs(point[i]-(p[0][i]+s*(p[1][i]-p[0][i])+t*(p[2][i]-p[0][i])))>1E-6)return false;
            return true;
        }
    }
    static void compare(List<BakedQuad> original,List<BakedQuad> merged) {
        if(merged.size()>original.size())throw new AssertionError("merge increased vertex count");
        checkBacks(merged);
        List<Triangle> a=triangles(original),b=triangles(merged);
        double areaA=0,areaB=0;for(Triangle t:a)areaA+=t.area();for(Triangle t:b)areaB+=t.area();
        if(Math.abs(areaA-areaB)>1E-6)throw new AssertionError("merge changed area: "+areaA+" -> "+areaB);
        sample(a,b);sample(b,a);
    }
    private static void checkBacks(List<BakedQuad> quads) {
        if((quads.size()&1)!=0)throw new AssertionError("missing back face");
        for(int q=0;q<quads.size();q+=2)for(int v=0;v<4;v++)for(int e=0;e<quads.get(q).getFormat().getElementCount();e++) {
            float[] front=new float[4],back=new float[4];
            LightUtil.unpack(quads.get(q).getVertexData(),front,quads.get(q).getFormat(),v,e);
            LightUtil.unpack(quads.get(q+1).getVertexData(),back,quads.get(q+1).getFormat(),(4-v)&3,e);
            if(!Arrays.equals(front,back))throw new AssertionError("merged back face changed attributes");
        }
    }
    private static List<Triangle> triangles(List<BakedQuad> quads) {
        List<Triangle> result=new ArrayList<>();
        for(int q=0;q<quads.size();q+=2) {
            BakedQuad quad=quads.get(q);double[][] p=new double[4][8];
            for(int v=0;v<4;v++) {
                float[] data=new float[4];int offset=0;
                for(int e:new int[]{0,2,3}) {
                    LightUtil.unpack(quad.getVertexData(),data,quad.getFormat(),v,e);
                    for(int i=0;i<(e==2?2:3);i++)p[v][offset++]=data[i];
                }
            }
            for(int[] indexes:new int[][]{{0,1,2},{0,2,3}}) {
                Triangle t=new Triangle(quad.getSprite(),p[indexes[0]],p[indexes[1]],p[indexes[2]]);
                if(t.area()>1E-10)result.add(t);
            }
        }
        return result;
    }
    private static void sample(List<Triangle> from,List<Triangle> to) {
        for(Triangle source:from)for(int s=0;s<=4;s++)for(int t=0;t<=4-s;t++) {
            double[] point=new double[8];for(int i=0;i<8;i++)point[i]=source.p[0][i]+s/4D*(source.p[1][i]-source.p[0][i])+t/4D*(source.p[2][i]-source.p[0][i]);
            boolean found=false;
            for(Triangle target:to)if(source.sprite==target.sprite && target.contains(point)){found=true;break;}
            if(!found)throw new AssertionError("merge changed covered surface/UV/normal: "+Arrays.toString(point));
        }
    }
}
