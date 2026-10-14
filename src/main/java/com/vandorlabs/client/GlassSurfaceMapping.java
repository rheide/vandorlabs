package com.vandorlabs.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Cached atlas UV transforms preserve geometry, normals and frame artwork. */
final class GlassSurfaceMapping {
    private static final class Key {
        final StaticSurfaceMesh mesh;final TextureAtlasSprite sprite;final String layout;final boolean raw;final double bx,by;
        Key(StaticSurfaceMesh mesh,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double bx,double by){this.mesh=mesh;this.sprite=sprite;this.layout=layout.key();this.raw=raw;this.bx=bx;this.by=by;}
        @Override public int hashCode(){return Objects.hash(System.identityHashCode(mesh),System.identityHashCode(sprite),layout,raw,bx,by);}
        @Override public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return mesh==k.mesh && sprite==k.sprite && layout.equals(k.layout) && raw==k.raw && bx==k.bx && by==k.by;}
    }
    private static final Map<Key,StaticSurfaceMesh> CACHE=new LinkedHashMap<Key,StaticSurfaceMesh>(128,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<Key,StaticSurfaceMesh> entry){return size()>1024;}};
    static StaticSurfaceMesh map(StaticSurfaceMesh mesh,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double borderX,double borderY){
        Key key=new Key(mesh,sprite,layout,raw,borderX,borderY);StaticSurfaceMesh result=CACHE.get(key);
        if(result==null){result=mesh.glassTexture(sprite,layout,raw,borderX,borderY);CACHE.put(key,result);}return result;
    }
    private GlassSurfaceMapping(){}
}
