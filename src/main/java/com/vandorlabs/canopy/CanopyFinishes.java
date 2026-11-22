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
        for(CanopyMesh.Face f:source)result.add(finish(f,shade,tint,material,layout,aspect,false,null));
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }

    public static synchronized List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,LargeCanopySettings settings,
                                                           double mainAspect,double sideAspect,boolean front){
        if(!settings.hasSideFinish()&&!settings.hasDisplay()){
            return faces(source,settings.shade,settings.tint,settings.material,settings.textureLayout(),mainAspect);
        }
        Key key=new Key(source,settings.shade+":"+settings.tint+":"+settings.material+":"+settings.textureLayout()+":"+mainAspect
                +":side:"+settings.shade(true)+":"+settings.tint(true)+":"+settings.material(true)+":"+settings.textureLayout(true)+":"+sideAspect+":"+front);
        List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        DisplayProjection projection=settings.hasDisplay()?new DisplayProjection(source,front):null;
        for(CanopyMesh.Face face:source){
            boolean side=face.baseMaterial.equals("glass")&&isSideGlass(face);
            result.add(finish(face,settings.shade(side),settings.tint(side),settings.material(side),
                    settings.textureLayout(side),side?sideAspect:mainAspect,side,projection));
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

    private static CanopyMesh.Face finish(CanopyMesh.Face face,int shade,int tint,int material,int layout,double aspect,boolean side,
                                          DisplayProjection projection){
        if(!face.baseMaterial.equals("glass"))return face;
        CanopyMesh.Face copy=new CanopyMesh.Face();
        copy.vertices=face.vertices;copy.joinedStretchUv=face.joinedStretchUv;
        copy.display=VehicleInstrument.forChoice(material);copy.sideGlass=side;
        copy.uv=copy.display!=null&&projection!=null?projection.coordinates(face,side):surfaceUv(face,layout,material,aspect);
        copy.tiled=material>=0&&!CanopyMaterialUv.fits(face,layout,aspect);
        copy.glazing=true;copy.baseMaterial=material<0?face.baseMaterial:"panel";
        copy.group=face.group;copy.doubleSided=face.doubleSided;
        copy.material=material<0?GlassTextures.texture(shade,1,tint):com.vandorlabs.tiles.ScreenHousingTextures.texture(material);
        return copy;
    }

    /** One picture per main pane and per physical side. Facets share logical pixels at every seam. */
    private static final class DisplayProjection {
        private final Pane[] panes={new Pane(),new Pane(),new Pane()};
        private final boolean front;
        private final double centerX,centerY;

        DisplayProjection(List<CanopyMesh.Face> source,boolean front){
            this.front=front;
            Pane bounds=new Pane();
            for(CanopyMesh.Face face:source)if(face.baseMaterial.equals("glass")){
                for(Vec3d point:face.vertices)bounds.include(point.x,point.y);
            }
            centerX=(bounds.minU+bounds.maxU)*.5;centerY=(bounds.minV+bounds.maxV)*.5;
            for(CanopyMesh.Face face:source)if(face.baseMaterial.equals("glass")){
                boolean side=isSideGlass(face);
                Pane pane=pane(face,side);
                for(Vec3d point:face.vertices)pane.include(u(point,side),v(point,side));
            }
        }

        double[][] coordinates(CanopyMesh.Face face,boolean side){
            if(!side&&face.joinedStretchUv!=null)return face.joinedStretchUv;
            Pane pane=pane(face,side);
            if(pane.maxU-pane.minU<1e-9||pane.maxV-pane.minV<1e-9)
                return surfaceUv(face,TextureLayoutMode.STRETCH,0,1);
            double[][] result=new double[face.vertices.length][2];
            for(int i=0;i<result.length;i++){
                Vec3d point=face.vertices[i];
                result[i][0]=(u(point,side)-pane.minU)/(pane.maxU-pane.minU);
                result[i][1]=(v(point,side)-pane.minV)/(pane.maxV-pane.minV);
            }
            return result;
        }

        private Pane pane(CanopyMesh.Face face,boolean side){
            if(!side)return panes[0];
            double x=0;
            for(Vec3d point:face.vertices)x+=point.x;
            return panes[x<face.vertices.length*centerX?1:2];
        }

        private static double u(Vec3d point,boolean side){return side?-point.z:point.x;}
        private double v(Vec3d point,boolean side){
            if(side)return point.y;
            if(front)return point.y+(point.y-centerY)*point.z*.12;
            return -(point.z+point.y);
        }
    }
    private static final class Pane {
        double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=-minU;
        void include(double u,double v){
            minU=Math.min(minU,u);maxU=Math.max(maxU,u);
            minV=Math.min(minV,v);maxV=Math.max(maxV,v);
        }
    }

    public static double[][] surfaceUv(CanopyMesh.Face face,int layout,int material,double aspect) {
        if (face.joinedStretchUv!=null && layout==com.vandorlabs.render.TextureLayoutMode.STRETCH)
            return face.joinedStretchUv;
        return material<0?face.uv:CanopyMaterialUv.coordinates(face,layout,aspect);
    }
    private CanopyFinishes(){}
}
