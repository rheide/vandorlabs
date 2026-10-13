package com.vandorlabs.canopy;

import com.vandorlabs.tiles.GlassTextures;
import java.util.*;

/** Immutable palette variants share authored vertices and UVs. */
public final class CanopyFinishes {
    private static final class Key {
        final List<CanopyMesh.Face> source;final int shade,tint,material;
        Key(List<CanopyMesh.Face> source,int shade,int tint,int material){this.material=material;this.source=source;this.shade=shade;this.tint=tint;}
        public int hashCode(){return System.identityHashCode(source)*31+shade*3+tint+material*97;}
        public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return source==k.source && shade==k.shade && tint==k.tint && material==k.material;}
    }
    private static final Map<Key,List<CanopyMesh.Face>> CACHE=new LinkedHashMap<Key,List<CanopyMesh.Face>>(64,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,List<CanopyMesh.Face>> entry){return size()>128;}};
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint){
        return faces(source,shade,tint,-1);
    }
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint,int material){
        Key key=new Key(source,shade,tint,material);List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(CanopyMesh.Face f:source){if(!f.baseMaterial.equals("glass")){result.add(f);continue;}CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=f.vertices;copy.uv=f.uv;copy.glazing=true;copy.baseMaterial=material<0?f.baseMaterial:"panel";copy.group=f.group;copy.doubleSided=f.doubleSided;copy.material=material<0?GlassTextures.texture(shade,1,tint):com.vandorlabs.tiles.ScreenHousingTextures.texture(material);result.add(copy);}
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private CanopyFinishes(){}
}
