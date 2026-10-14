package com.vandorlabs.canopy;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Modular opening shells use the same noses and seam heights as fixed glazing. */
public final class OpeningCanopyGeometry {
    private static final Map<String,List<CanopyMesh.Face>> CACHE=new HashMap<>();
    public static synchronized List<CanopyMesh.Face> faces(int width,int angle,boolean rear) {
        String key=width+":"+angle+":"+rear;
        List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>();
        for(int x=0;x<width;x++) {
            int sides=(x>0?1:0)|(x+1<width?2:0);
            if(angle==2)append(result,model("angled_front_1x2",sides|(rear?8:0)),x,0);
            else {
                append(result,angle==0?regular(sides|8):model("angled_front_1x1",sides|8),x,0);
                append(result,regular(sides|4|(rear?8:0)),x,1,angle==1);
            }
        }
        String mounts=width==1?"canopy":"canopy_mounts_double";
        for(CanopyMesh.Face source:CanopyMesh.MODELS.get(mounts))if(source.group.equals("mount")) {
            CanopyMesh.Face face=copy(source);face.vertices=new Vec3d[source.vertices.length];
            for(int i=0;i<face.vertices.length;i++)face.vertices[i]=CanopyMesh.pose(source.vertices[i],"slide",width,0,false);
            result.add(face);
        }
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private static List<CanopyMesh.Face> regular(int mask){return CanopyMesh.MODELS.get(String.format(Locale.ROOT,"regular_1x1_%02x",mask));}
    private static List<CanopyMesh.Face> model(String id,int mask){return CanopyMesh.MODELS.get(mask==0?id:String.format(Locale.ROOT,"%s_%02x",id,mask));}
    private static void append(List<CanopyMesh.Face> result,List<CanopyMesh.Face> source,int x,int z){
        append(result,source,x,z,false);
    }
    private static void append(List<CanopyMesh.Face> result,List<CanopyMesh.Face> source,int x,int z,boolean raisedSill){
        // The short angled nose has a one-pixel raised sill. Carry that sill through the rear panel.
        for(CanopyMesh.Face original:source){CanopyMesh.Face face=copy(original);face.group="shell";face.vertices=new Vec3d[original.vertices.length];for(int i=0;i<face.vertices.length;i++){Vec3d v=original.vertices[i];face.vertices[i]=v.addVector(x,raisedSill && v.y<.5?1D/16:0,z);}result.add(face);}
    }
    private static CanopyMesh.Face copy(CanopyMesh.Face source){CanopyMesh.Face face=new CanopyMesh.Face();face.uv=source.uv;face.material=source.material;face.baseMaterial=source.baseMaterial;face.group=source.group;face.doubleSided=source.doubleSided;return face;}
    private OpeningCanopyGeometry(){}
}
