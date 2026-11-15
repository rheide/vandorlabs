package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.vandorlabs.turntable.TurntableGeometry;

/** Cached UV-clipped pad faces never repeat outside a sprite's atlas rectangle. */
final class SurfaceTextureMesh {
    private static final int[] ORDER={0,2,1,1};
    private final List<com.vandorlabs.surface.core.SurfaceUvMesh.Vertex[]> triangles;
    private static int retained;
    private static final Map<String,SurfaceTextureMesh> CACHE=new LinkedHashMap<>(64,.75f,true);
    private SurfaceTextureMesh(List<com.vandorlabs.surface.core.SurfaceUvMesh.Vertex[]> triangles){this.triangles=triangles;}
    static void clear(){CACHE.clear();retained=0;}
    static SurfaceTextureMesh get(TextureAtlasSprite sprite,int size,int mode,boolean disc){return get(sprite,size,size,mode,disc);}
    static SurfaceTextureMesh get(TextureAtlasSprite sprite,int width,int height,int mode,boolean disc){
        String key=System.identityHashCode(sprite)+":"+width+":"+height+":"+mode+":"+disc;SurfaceTextureMesh found=CACHE.get(key);if(found!=null)return found;
        List<com.vandorlabs.surface.core.SurfaceUvMesh.Vertex[]> triangles=com.vandorlabs.surface.core.SurfaceUvMesh.build(width,height,mode,UnifiedTextureSprites.aspect(sprite),disc);
        found=new SurfaceTextureMesh(Collections.unmodifiableList(triangles));int bytes=128+triangles.size()*184;while(!CACHE.isEmpty()&&(CACHE.size()>=256||retained+bytes>8*1024*1024)){Iterator<SurfaceTextureMesh> it=CACHE.values().iterator();SurfaceTextureMesh old=it.next();it.remove();retained-=128+old.triangles.size()*184;}if(bytes<=8*1024*1024){CACHE.put(key,found);retained+=bytes;}return found;
    }
    void draw(BufferBuilder buffer,TurntableGeometry.Face face,TextureAtlasSprite sprite,double depth,int light){draw(buffer,face,sprite,depth,light,0,0,0);}
    void draw(BufferBuilder buffer,TurntableGeometry.Face face,TextureAtlasSprite sprite,double depth,int light,double x,double y,double z){for(com.vandorlabs.surface.core.SurfaceUvMesh.Vertex[] triangle:triangles)for(int index:ORDER){
        com.vandorlabs.surface.core.SurfaceUvMesh.Vertex v=triangle[index];buffer.pos(x+face.ux*v.x+face.vx*v.y+face.x*depth,y+face.uy*v.x+face.vy*v.y+face.y*depth,z+face.uz*v.x+face.vz*v.y+face.z*depth).color(1F,1F,1F,1F).tex(sprite.getInterpolatedU(v.u*16),sprite.getInterpolatedV(v.v*16)).lightmap(light&65535,light>>>16);
        if(buffer.getVertexFormat().hasNormal())buffer.normal(face.x,face.y,face.z);buffer.endVertex();
    }}
}
