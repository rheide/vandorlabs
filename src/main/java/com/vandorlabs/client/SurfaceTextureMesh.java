package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.vandorlabs.turntable.TurntableGeometry;

/** Cached UV-clipped pad faces never repeat outside a sprite's atlas rectangle. */
final class SurfaceTextureMesh {
    private static final class V {
        final double x,y,u,v;
        V(double x,double y,double u,double v){this.x=x;this.y=y;this.u=u;this.v=v;}
        double at(int axis){return axis==0?u:v;}
        V between(V other,double t){return new V(x+(other.x-x)*t,y+(other.y-y)*t,u+(other.u-u)*t,v+(other.v-v)*t);}
    }
    private static final int[] ORDER={0,2,1,1};
    private final List<V[]> triangles;
    private static final Map<String,SurfaceTextureMesh> CACHE=new LinkedHashMap<String,SurfaceTextureMesh>(64,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,SurfaceTextureMesh> entry){return size()>256;}};
    private SurfaceTextureMesh(List<V[]> triangles){this.triangles=triangles;}
    static void clear(){CACHE.clear();}
    static SurfaceTextureMesh get(TextureAtlasSprite sprite,int size,int mode,boolean disc){
        String key=System.identityHashCode(sprite)+":"+size+":"+mode+":"+disc;SurfaceTextureMesh found=CACHE.get(key);if(found!=null)return found;
        double r=size/2D,aspect=UnifiedTextureSprites.aspect(sprite);List<double[][]> polygons=new ArrayList<>();
        if(disc)for(int i=0;i<128;i++){double a=i*Math.PI/64,b=(i+1)*Math.PI/64,rad=size*com.vandorlabs.surface.SquareLayout.DISC_RADIUS;polygons.add(new double[][]{{0,0},{Math.cos(a)*rad,Math.sin(a)*rad},{Math.cos(b)*rad,Math.sin(b)*rad}});}
        else polygons.add(new double[][]{{-r,-r},{r,-r},{r,r},{-r,r}});
        List<V[]> triangles=new ArrayList<>();
        for(double[][] polygon:polygons){List<V> source=new ArrayList<>();
            for(double[] p:polygon){double u,v;if(mode==1){u=(p[0]+r)/Math.max(1,aspect);v=(p[1]+r)*Math.min(1,aspect);}else if(mode==2){u=.5+p[0]/(size*Math.min(1,aspect));v=.5+p[1]/(size*Math.min(1,1/aspect));}else{u=(p[0]+r)/size;v=(p[1]+r)/size;}source.add(new V(p[0],p[1],u,v));}
            double lowU=Double.POSITIVE_INFINITY,lowV=lowU,highU=Double.NEGATIVE_INFINITY,highV=highU;for(V p:source){lowU=Math.min(lowU,p.u);highU=Math.max(highU,p.u);lowV=Math.min(lowV,p.v);highV=Math.max(highV,p.v);}
            for(int u=mode==1?(int)Math.floor(lowU+1e-9):0;u<(mode==1?Math.ceil(highU-1e-9):1);u++)for(int v=mode==1?(int)Math.floor(lowV+1e-9):0;v<(mode==1?Math.ceil(highV-1e-9):1);v++){
                List<V> clipped=clip(clip(clip(clip(source,0,u,true),0,u+1,false),1,v,true),1,v+1,false);if(clipped.size()<3)continue;
                List<V> normalized=new ArrayList<>();for(V p:clipped)normalized.add(new V(p.x,p.y,p.u-u,p.v-v));
                for(int i=1;i+1<normalized.size();i++){V a=normalized.get(0),b=normalized.get(i),c=normalized.get(i+1);if(Math.abs((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x))>1e-12)triangles.add(new V[]{a,b,c});}
            }
        }
        found=new SurfaceTextureMesh(Collections.unmodifiableList(triangles));CACHE.put(key,found);return found;
    }
    private static List<V> clip(List<V> source,int axis,double plane,boolean lower){List<V> result=new ArrayList<>();if(source.isEmpty())return result;V last=source.get(source.size()-1);boolean before=lower?last.at(axis)>=plane:last.at(axis)<=plane;for(V next:source){boolean after=lower?next.at(axis)>=plane:next.at(axis)<=plane;if(before!=after)result.add(last.between(next,(plane-last.at(axis))/(next.at(axis)-last.at(axis))));if(after)result.add(next);last=next;before=after;}return result;}
    void draw(BufferBuilder buffer,TurntableGeometry.Face face,TextureAtlasSprite sprite,double depth,int light){for(V[] triangle:triangles)for(int index:ORDER){
        V v=triangle[index];buffer.pos(face.ux*v.x+face.vx*v.y+face.x*depth,face.uy*v.x+face.vy*v.y+face.y*depth,face.uz*v.x+face.vz*v.y+face.z*depth).color(1F,1F,1F,1F).tex(sprite.getInterpolatedU(v.u*16),sprite.getInterpolatedV(v.v*16)).lightmap(light&65535,light>>>16).normal(face.x,face.y,face.z).endVertex();
    }}
}
