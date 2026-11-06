package com.vandorlabs.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vandorlabs.light.ProgrammableLightMesh;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.opengl.GL11;

/** Released Light housing records shared with the native backend; atlas objects stay per draw. */
final class LightHousingMeshes {
    private static final int BUDGET=4*1024*1024;
    private static final class Key {
        final double x0,y0,z0,x1,y1,z1,aspect;final int visible,layout;
        Key(double x0,double y0,double z0,double x1,double y1,double z1,int visible,int layout,double aspect){this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;this.visible=visible;this.layout=layout;this.aspect=aspect;}
        boolean matches(double x0,double y0,double z0,double x1,double y1,double z1,int visible,int layout,double aspect){return this.x0==x0&&this.y0==y0&&this.z0==z0&&this.x1==x1&&this.y1==y1&&this.z1==z1&&this.visible==visible&&this.layout==layout&&this.aspect==aspect;}
        @Override public boolean equals(Object other){if(!(other instanceof Key))return false;Key key=(Key)other;return matches(key.x0,key.y0,key.z0,key.x1,key.y1,key.z1,key.visible,key.layout,key.aspect);}
        private static long bits(double value){return Double.doubleToLongBits(value==0?0:value);}
        @Override public int hashCode(){long hash=bits(x0);hash=31*hash+bits(y0);hash=31*hash+bits(z0);hash=31*hash+bits(x1);hash=31*hash+bits(y1);hash=31*hash+bits(z1);hash=31*hash+bits(aspect);return 31*(31*(int)(hash^(hash>>>32))+visible)+layout;}
    }
    private static final Cache<Key,ProgrammableLightMesh> MESHES=CacheBuilder.newBuilder().maximumWeight(BUDGET).weigher((Key key,ProgrammableLightMesh mesh)->128+mesh.retainedBytes()).build();
    private static final class Emitter implements ProgrammableLightMesh.Sink {
        Key key;java.lang.ref.WeakReference<ProgrammableLightMesh> mesh;BufferBuilder buffer;TextureAtlasSprite sprite;
        public void vertex(double x,double y,double z,double u,double v,float nx,float ny,float nz){WorldSurface.vertex(buffer,x,y,z,sprite.getInterpolatedU(u),sprite.getInterpolatedV(v),nx,ny,nz);}
    }
    private static final ThreadLocal<Emitter> EMITTER=ThreadLocal.withInitial(Emitter::new);
    static void render(TextureAtlasSprite sprite,int visible,double x0,double y0,double z0,double x1,double y1,double z1,int layout){
        Emitter emitter=EMITTER.get();double aspect=UnifiedTextureSprites.aspect(sprite);ProgrammableLightMesh mesh=emitter.mesh==null?null:emitter.mesh.get();
        if(mesh==null||emitter.key==null||!emitter.key.matches(x0,y0,z0,x1,y1,z1,visible,layout,aspect)){
            Key key=new Key(x0,y0,z0,x1,y1,z1,visible,layout,aspect);mesh=MESHES.getIfPresent(key);
            if(mesh==null){mesh=ProgrammableLightMesh.housing(new double[]{x0,y0,z0,x1,y1,z1},visible,layout,aspect);if(mesh.retainedBytes()+128<=BUDGET)MESHES.put(key,mesh);}
            emitter.key=key;emitter.mesh=new java.lang.ref.WeakReference<>(mesh);
        }
        if(mesh.vertices()==0)return;
        Tessellator tess=Tessellator.getInstance();emitter.buffer=tess.getBuffer();emitter.sprite=sprite;
        try{emitter.buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh.emit(emitter);tess.draw();}
        finally{emitter.buffer=null;emitter.sprite=null;}
    }
    static void clear(){MESHES.invalidateAll();EMITTER.remove();}
    private LightHousingMeshes(){}
}
