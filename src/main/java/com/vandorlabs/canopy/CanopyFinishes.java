package com.vandorlabs.canopy;

import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.render.TextureLayoutMode;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Immutable palette variants share authored vertices and UVs. */
public final class CanopyFinishes {
    /** Native glass keeps its texture alpha; only replacement panels use the tint opacity. */
    public static float opacity(CanopyMesh.Face face,int material,int tint){
        return face.glazing && face.baseMaterial.equals("panel")?com.vandorlabs.tiles.GlassMaterials.opacity(material,tint):1F;
    }
    public static boolean translucent(CanopyMesh.Face face,int material,int tint){return face.baseMaterial.equals("glass")&&!com.vandorlabs.tiles.GlassMaterials.opaque(material,tint) || opacity(face,material,tint)<1F;}
    public static float opacity(CanopyMesh.Face face,LargeCanopySettings settings){
        return opacity(face,settings.material(face.sideGlass),settings.tint(face.sideGlass));
    }
    public static boolean translucent(CanopyMesh.Face face,LargeCanopySettings settings){
        return face.display==null&&translucent(face,settings.material(face.sideGlass),settings.tint(face.sideGlass));
    }

    private static final class Key {
        final List<CanopyMesh.Face> source;
        final String finish;
        Key(List<CanopyMesh.Face> source,String finish){this.source=source;this.finish=finish;}
        public int hashCode(){return System.identityHashCode(source)*31+finish.hashCode();}
        public boolean equals(Object other){
            if(!(other instanceof Key))return false;
            Key key=(Key)other;return source==key.source&&finish.equals(key.finish);
        }
    }
    private static final Map<Key,List<CanopyMesh.Face>> CACHE=new LinkedHashMap<Key,List<CanopyMesh.Face>>(64,.75F,true){
        protected boolean removeEldestEntry(Map.Entry<Key,List<CanopyMesh.Face>> entry){return size()>128;}
    };
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
        Key key=new Key(source,shade+":"+tint+":"+material+":"+layout+":"+aspect);
        List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(CanopyMesh.Face f:source)result.add(finish(f,shade,tint,material,layout,aspect,false));
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }

    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,LargeCanopySettings settings,
                                                           double mainAspect,double sideAspect){
        if(!settings.hasSideFinish()&&!settings.hasDisplay()){
            return faces(source,settings.shade,settings.tint,settings.material,settings.textureLayout(),mainAspect);
        }
        Key key=new Key(source,settings.shade+":"+settings.tint+":"+settings.material+":"+settings.textureLayout()+":"+mainAspect
                +":side:"+settings.shade(true)+":"+settings.tint(true)+":"+settings.material(true)+":"+settings.textureLayout(true)+":"+sideAspect);
        List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(CanopyMesh.Face face:source){
            boolean side=face.baseMaterial.equals("glass")&&isSideGlass(face);
            result.add(finish(face,settings.shade(side),settings.tint(side),settings.material(side),
                    settings.textureLayout(side),side?sideAspect:mainAspect,side));
        }
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }

    public static boolean isSideGlass(CanopyMesh.Face face){
        for(int i=1;i<face.vertices.length-1;i++){
            Vec3d normal=face.vertices[i].subtract(face.vertices[0]).crossProduct(face.vertices[i+1].subtract(face.vertices[0]));
            if(normal.lengthSquared()>1e-16)return Math.abs(normal.x)>Math.abs(normal.y)&&Math.abs(normal.x)>Math.abs(normal.z);
        }
        return false;
    }

    private static CanopyMesh.Face finish(CanopyMesh.Face face,int shade,int tint,int material,int layout,double aspect,boolean side){
        if(!face.baseMaterial.equals("glass"))return face;
        CanopyMesh.Face copy=new CanopyMesh.Face();
        copy.vertices=face.vertices;copy.joinedStretchUv=face.joinedStretchUv;
        copy.display=VehicleInstrument.forChoice(material);copy.sideGlass=side;
        copy.uv=copy.display!=null?surfaceUv(face,TextureLayoutMode.STRETCH,0,1):surfaceUv(face,layout,material,aspect);
        copy.tiled=material>=0&&!CanopyMaterialUv.fits(face,layout,aspect);
        copy.glazing=true;copy.baseMaterial=material<0?face.baseMaterial:"panel";
        copy.group=face.group;copy.doubleSided=face.doubleSided;
        copy.material=material<0?GlassTextures.texture(shade,1,tint):com.vandorlabs.tiles.ScreenHousingTextures.texture(material);
        return copy;
    }

    public static double[][] surfaceUv(CanopyMesh.Face face,int layout,int material,double aspect) {
        if (face.joinedStretchUv!=null && layout==com.vandorlabs.render.TextureLayoutMode.STRETCH)
            return face.joinedStretchUv;
        return material<0?face.uv:CanopyMaterialUv.coordinates(face,layout,aspect);
    }
    private CanopyFinishes(){}
}
