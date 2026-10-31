package com.vandorlabs.canopy;

import com.vandorlabs.tiles.GlassTextures;
import java.util.*;

/** Immutable palette variants share authored vertices and UVs. */
public final class CanopyFinishes {
    /** Native glass keeps its texture alpha; only replacement panels use the tint opacity. */
    public static float opacity(CanopyMesh.Face face,int material,int tint){
        return face.glazing && face.baseMaterial.equals("panel")?com.vandorlabs.tiles.GlassMaterials.opacity(material,tint):1F;
    }
    public static boolean translucent(CanopyMesh.Face face,int material,int tint){return face.baseMaterial.equals("glass")&&!com.vandorlabs.tiles.GlassMaterials.opaque(material,tint) || opacity(face,material,tint)<1F;}
    private static final class Key {
        final List<CanopyMesh.Face> source;final int shade,tint,material;final int layout;final double aspect;
        Key(List<CanopyMesh.Face> source,int shade,int tint,int material,int layout,double aspect){this.layout=layout;this.aspect=aspect;this.material=material;this.source=source;this.shade=shade;this.tint=tint;}
        public int hashCode(){return System.identityHashCode(source)*31+shade*GlassTextures.TINT_COUNT+tint+material*97+layout+Double.hashCode(aspect);}
        public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return source==k.source && shade==k.shade && tint==k.tint && material==k.material && layout==k.layout && aspect==k.aspect;}
    }
    private static final Map<Key,List<CanopyMesh.Face>> CACHE=new LinkedHashMap<Key,List<CanopyMesh.Face>>(64,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,List<CanopyMesh.Face>> entry){return size()>128;}};
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint){
        return faces(source,shade,tint,-1);
    }
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint,int material){
        return faces(source,shade,tint,material,false);
    }
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint,int material,boolean tiled){
        return faces(source,shade,tint,material,tiled?1:0,1);
    }
    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,int shade,int tint,int material,int layout,double aspect){
        Key key=new Key(source,shade,tint,material,layout,aspect);List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(CanopyMesh.Face f:source){if(!f.baseMaterial.equals("glass")){result.add(f);continue;}CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=f.vertices;copy.uv=material<0?f.uv:CanopyMaterialUv.coordinates(f,layout,aspect);copy.tiled=material>=0 && !CanopyMaterialUv.fits(f,layout,aspect);copy.glazing=true;copy.baseMaterial=material<0?f.baseMaterial:"panel";copy.group=f.group;copy.doubleSided=f.doubleSided;copy.material=material<0?GlassTextures.texture(shade,1,tint):com.vandorlabs.tiles.ScreenHousingTextures.texture(material);result.add(copy);}
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private CanopyFinishes(){}
}
