package com.vandorlabs.client;

import com.vandorlabs.render.ScreenHousingMesh;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Iterator;

/** Numeric, bounded preparation cache shared by screen main and shadow draws. */
final class SelectorHousingMeshes {
    static final int MAX_BYTES=2*1024*1024;
    private static final LinkedHashMap<Key,Entry> CACHE=new LinkedHashMap<>(64,.75F,true);
    private static int retained;
    private static volatile long generation;

    private static final class Key {
        ScreenHousingMesh base;
        double x0,y0,z0,x1,y1,z1,aspect;
        int mask,layout;
        Key(){}
        Key(Key other){set(other.base,other.x0,other.y0,other.z0,other.x1,other.y1,other.z1,other.mask,other.layout,other.aspect);}
        Key set(ScreenHousingMesh base,double x0,double y0,double z0,double x1,double y1,double z1,int mask,int layout,double aspect){
            this.base=base;this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;this.mask=mask;this.layout=layout;this.aspect=aspect;return this;
        }
        @Override public boolean equals(Object value){
            if(!(value instanceof Key))return false;Key k=(Key)value;
            return base==k.base && x0==k.x0 && y0==k.y0 && z0==k.z0 && x1==k.x1 && y1==k.y1 && z1==k.z1
                    && mask==k.mask && layout==k.layout && aspect==k.aspect;
        }
        private static long bits(double value){return Double.doubleToLongBits(value==0?0:value);}
        @Override public int hashCode(){
            long h=System.identityHashCode(base);h=31*h+bits(x0);h=31*h+bits(y0);h=31*h+bits(z0);
            h=31*h+bits(x1);h=31*h+bits(y1);h=31*h+bits(z1);h=31*h+bits(aspect);
            return 31*(31*(int)(h^(h>>>32))+mask)+layout;
        }
    }
    /** Retain only authored faces, not the source mesh's nested layout caches. */
    static final class Mesh {
        final ScreenHousingMesh.Face[] quads,triangles;
        Mesh(ScreenHousingMesh source){quads=source.quads;triangles=source.triangles;}
    }
    private static final class Entry {
        final Key key;final Mesh mesh;final WeakReference<Mesh> weak;final int bytes;
        Entry(Key key,Mesh mesh){this.key=key;this.mesh=mesh;weak=new WeakReference<>(mesh);bytes=280+bytes(mesh.quads)+bytes(mesh.triangles);}
        private static int bytes(ScreenHousingMesh.Face[] faces){int total=16+faces.length*8;for(ScreenHousingMesh.Face face:faces)total+=64+face.vertices.length*64;return total;}
    }
    private static final class Local {
        final Key probe=new Key();Key key;WeakReference<Mesh> mesh;long generation=-1;
    }
    private static final ThreadLocal<Local> LOCAL=ThreadLocal.withInitial(Local::new);

    static Mesh box(double x0,double y0,double z0,double x1,double y1,double z1,int layout,double aspect){
        return get(null,x0,y0,z0,x1,y1,z1,-1,layout,aspect);
    }
    static Mesh layout(ScreenHousingMesh base,int layout,double aspect){
        return get(base,0,0,0,0,0,0,-1,layout,aspect);
    }
    static Mesh cubeFaces(int visible,int layout,double aspect){
        return get(ScreenHousingMesh.cube(),0,0,0,0,0,0,visible,layout,aspect);
    }
    private static Mesh get(ScreenHousingMesh base,double x0,double y0,double z0,double x1,double y1,double z1,int mask,int layout,double aspect){
        Local local=LOCAL.get();Key probe=local.probe.set(base,x0,y0,z0,x1,y1,z1,mask,layout,aspect);
        Mesh mesh=local.mesh==null?null:local.mesh.get();
        if(mesh!=null && local.generation==generation && probe.equals(local.key))return mesh;
        synchronized(CACHE){
            Entry entry=CACHE.get(probe);
            if(entry==null){
                ScreenHousingMesh source=base==null?ScreenHousingMesh.box(x0,y0,z0,x1,y1,z1):mask<0?base:base.boxFaces(mask);
                mesh=new Mesh(source.textureLayout(layout,aspect));
                entry=new Entry(new Key(probe),mesh);
                if(entry.bytes<=MAX_BYTES){
                    while(!CACHE.isEmpty() && retained+entry.bytes>MAX_BYTES){Iterator<Entry> oldest=CACHE.values().iterator();retained-=oldest.next().bytes;oldest.remove();}
                    CACHE.put(entry.key,entry);retained+=entry.bytes;
                }
            }
            local.key=entry.key;local.mesh=entry.weak;local.generation=generation;return entry.mesh;
        }
    }
    static int retainedBytes(){synchronized(CACHE){return retained;}}
    static int entries(){synchronized(CACHE){return CACHE.size();}}
    static void clear(){synchronized(CACHE){CACHE.clear();retained=0;generation++;}LOCAL.remove();}
    private SelectorHousingMeshes(){}
}
