package com.vandorlabs.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Cached atlas UV transforms preserve geometry, normals and frame artwork. */
final class GlassSurfaceMapping {
    private static final long BUDGET=8L*1024*1024;
    private static final class Key {
        StaticSurfaceMesh mesh;TextureAtlasSprite sprite;int column,row,columns,rows,mode;
        boolean flipU,flipV,raw;double bx,by;
        Key(){}
        Key(Key source){mesh=source.mesh;sprite=source.sprite;column=source.column;row=source.row;columns=source.columns;rows=source.rows;mode=source.mode;flipU=source.flipU;flipV=source.flipV;raw=source.raw;bx=source.bx;by=source.by;}
        void set(StaticSurfaceMesh mesh,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double bx,double by,int mode){this.mesh=mesh;this.sprite=sprite;column=layout.column;row=layout.row;columns=layout.columns;rows=layout.rows;flipU=layout.flipU;flipV=layout.flipV;this.raw=raw;this.bx=bx;this.by=by;this.mode=mode;}
        void release(){mesh=null;sprite=null;}
        private static int bits(double value){long bits=Double.doubleToLongBits(value==0?0:value);return (int)(bits^(bits>>>32));}
        @Override public int hashCode(){int hash=System.identityHashCode(mesh);hash=31*hash+System.identityHashCode(sprite);hash=31*hash+column;hash=31*hash+row;hash=31*hash+columns;hash=31*hash+rows;hash=31*hash+(flipU?1:0);hash=31*hash+(flipV?1:0);hash=31*hash+(raw?1:0);hash=31*hash+bits(bx);hash=31*hash+bits(by);return 31*hash+mode;}
        @Override public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return mesh==k.mesh&&sprite==k.sprite&&column==k.column&&row==k.row&&columns==k.columns&&rows==k.rows&&flipU==k.flipU&&flipV==k.flipV&&raw==k.raw&&bx==k.bx&&by==k.by&&mode==k.mode;}
    }
    private static final class Entry {
        final StaticSurfaceMesh mesh;final long bytes;
        Entry(StaticSurfaceMesh source,StaticSurfaceMesh mapped){mesh=mapped;bytes=192+source.cacheRetainedBytes()+mapped.cacheRetainedBytes();}
    }
    private static final Map<Key,Entry> CACHE=new LinkedHashMap<>(128,.75F,true);
    // Used only under this class monitor; never inserted or retained by a cache entry.
    private static final Key LOOKUP=new Key();
    private static long retained;
    static StaticSurfaceMesh map(StaticSurfaceMesh mesh,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double borderX,double borderY){return map(mesh,sprite,layout,raw,borderX,borderY,0);}
    static synchronized StaticSurfaceMesh map(StaticSurfaceMesh mesh,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double borderX,double borderY,int mode){
        LOOKUP.set(mesh,sprite,layout,raw,borderX,borderY,mode);
        try {
            Entry found=CACHE.get(LOOKUP);if(found!=null)return found.mesh;
            StaticSurfaceMesh result=mode==0?mesh.glassTexture(sprite,layout,raw,borderX,borderY):mesh.textureLayout(sprite,layout,raw,borderX,borderY,mode);
            Entry next=new Entry(mesh,result);
            // Bound retained geometry by bytes; a fixed entry limit thrashes dense sets of small panes.
            if(next.bytes<=BUDGET){CACHE.put(new Key(LOOKUP),next);retained+=next.bytes;while(retained>BUDGET){Iterator<Entry> oldest=CACHE.values().iterator();retained-=oldest.next().bytes;oldest.remove();}}
            return result;
        } finally {LOOKUP.release();}
    }
    static synchronized long retainedBytes(){return retained;}
    static synchronized int entries(){return CACHE.size();}
    static synchronized void clear(){CACHE.clear();LOOKUP.release();retained=0;}
    private GlassSurfaceMapping(){}
}
