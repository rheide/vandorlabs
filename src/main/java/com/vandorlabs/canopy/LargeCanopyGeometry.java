package com.vandorlabs.canopy;
import java.util.*;
import net.minecraft.util.math.Vec3d;
import com.vandorlabs.tiles.GlassTextures;
/** Deforms the original chamfered frame and its material details, rather than replacing the kit. */
public final class LargeCanopyGeometry {
    private static final Map<String,List<CanopyMesh.Face>> CACHE=new LinkedHashMap<String,List<CanopyMesh.Face>>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<String,List<CanopyMesh.Face>> entry){return size()>32;}};
    public static List<CanopyMesh.Face> faces(int width,LargeCanopySettings settings){return faces(width,settings,false);}
    public static synchronized List<CanopyMesh.Face> faces(int width,LargeCanopySettings settings,boolean backAnchored){
        String key=width+":"+backAnchored+":"+settings.key();List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(CanopyMesh.Face original:CanopyMesh.MODELS.get("front_canopy_glass")){
            CanopyMesh.Face face=new CanopyMesh.Face();face.vertices=new Vec3d[original.vertices.length];face.uv=original.uv;face.baseMaterial=original.baseMaterial;face.material=face.baseMaterial.equals("glass")?GlassTextures.texture(settings.shade,1,settings.tint):original.material;face.group=original.group;face.doubleSided=original.doubleSided;
            List<String> points=new ArrayList<>();
            for(int i=0;i<face.vertices.length;i++){
                Vec3d v=original.vertices[i];double t=1-Math.min(1,v.z/.96875);double factor=width/3D*(1-t+t*settings.scale/100D/(2.125/3D));
                double x=Math.max(0,Math.min(width,width/2D+(v.x-1.5)*factor)),y=Math.max(0,Math.min(width,width/2D+(v.y-1.5)*factor));
                // A zero-depth frame retains a thin physical shell; it never becomes a zero-volume collider.
                face.vertices[i]=new Vec3d(x,y,(backAnchored?1-v.z:v.z)*Math.max(1D/64,settings.depth/16D));
                Vec3d p=face.vertices[i];points.add(Math.round(p.x*1048576)+","+Math.round(p.y*1048576)+","+Math.round(p.z*1048576));
            }
            if(backAnchored){
                Vec3d[] reversed=new Vec3d[face.vertices.length];double[][] uv=new double[face.vertices.length][];
                for(int i=0;i<reversed.length;i++){reversed[i]=face.vertices[reversed.length-1-i];uv[i]=original.uv[reversed.length-1-i];}
                face.vertices=reversed;face.uv=uv;
            }
            double area=0;for(int i=1;i<face.vertices.length-1;i++)area+=face.vertices[i].subtract(face.vertices[0]).crossProduct(face.vertices[i+1].subtract(face.vertices[0])).lengthSquared();
            Collections.sort(points);if(area>1e-16 && seen.add(String.join(";",points)))result.add(face);
        }
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private LargeCanopyGeometry(){}
}
