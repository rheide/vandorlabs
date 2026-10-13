package com.vandorlabs.canopy;

import com.vandorlabs.tiles.GlassTextures;
import java.util.*;

/** Immutable palette variants share authored vertices and UVs. */
public final class CanopyFinishes {
    private static final class Key {
        final List<CanopyMesh.Face> source;final int shade,tint;
        Key(List<CanopyMesh.Face> source,int shade,int tint){this.source=source;this.shade=shade;this.tint=tint;}
        public int hashCode(){return System.identityHashCode(source)*31+shade*3+tint;}
        public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return source==k.source && shade==k.shade && tint==k.tint;}
    }
    private static final Map<Key,List<CanopyMesh.Face>> CACHE=new LinkedHashMap<Key,List<CanopyMesh.Face>>(64,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,List<CanopyMesh.Face>> entry){return size()>128;}};
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint){
        Key key=new Key(source,shade,tint);List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(CanopyMesh.Face f:source){if(!f.baseMaterial.equals("glass")){result.add(f);continue;}CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=f.vertices;copy.uv=f.uv;copy.baseMaterial=f.baseMaterial;copy.group=f.group;copy.doubleSided=f.doubleSided;copy.material=GlassTextures.texture(shade,1,tint);result.add(copy);}
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private CanopyFinishes(){}
}
