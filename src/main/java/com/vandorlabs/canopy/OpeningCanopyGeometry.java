package com.vandorlabs.canopy;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Modular opening shells use the same noses and seam heights as fixed glazing. */
public final class OpeningCanopyGeometry {
    private static final Map<String,List<CanopyMesh.Face>> CACHE=new HashMap<>();
    public static synchronized List<CanopyMesh.Face> faces(int width,int angle,boolean rear) {
        return faces(width,2,angle,rear);
    }
    public static synchronized List<CanopyMesh.Face> faces(int width,int depth,int angle,boolean rear) {
        if(width<1 || width>9 || depth<2 || depth>3 || angle<0 || angle>(depth==3?3:2))throw new IllegalArgumentException("Invalid opening canopy dimensions");
        String key=width+":"+depth+":"+angle+":"+rear;
        List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>();
        for(int x=0;x<width;x++) {
            int sides=(x>0?1:0)|(x+1<width?2:0);
            if(angle==3) {
                List<CanopyMesh.Face> diagonal=new ArrayList<>();
                for(CanopyMesh.Face original:rearJoined("angled_front_1x2",sides,rear)) {
                    CanopyMesh.Face face=copy(original);face.vertices=new Vec3d[original.vertices.length];
                    for(int i=0;i<face.vertices.length;i++){Vec3d v=original.vertices[i];face.vertices[i]=new Vec3d(v.x,v.y,v.z*1.5);}
                    diagonal.add(face);
                }
                append(result,diagonal,x,0);
            }else if(angle==2) {
                append(result,rearJoined("angled_front_1x2",sides,depth==3 || rear),x,0);
                if(depth==3)append(result,rearJoined("regular_1x1",sides|4,rear),x,2);
            }else {
                append(result,angle==0?regular(sides|8):model("angled_front_1x1",sides|8),x,0);
                for(int z=1;z<depth;z++)append(result,rearJoined("regular_1x1",sides|4|(z+1<depth?8:0),z+1<depth || rear),x,z,angle==1);
            }
        }
        String mounts=width==1?"canopy":"canopy_mounts_double";
        for(CanopyMesh.Face source:CanopyMesh.MODELS.get(mounts))if(source.group.equals("mount")) {
            CanopyMesh.Face face=copy(source);face.vertices=new Vec3d[source.vertices.length];
            for(int i=0;i<face.vertices.length;i++){Vec3d v=CanopyMesh.pose(source.vertices[i],"slide",width,0,false);face.vertices[i]=new Vec3d(v.x,v.y,v.z*depth/2D);}
            result.add(face);
        }
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private static List<CanopyMesh.Face> regular(int mask){return CanopyMesh.MODELS.get(String.format(Locale.ROOT,"regular_1x1_%02x",mask));}
    private static List<CanopyMesh.Face> rearJoined(String id,int mask,boolean rear){
        List<CanopyMesh.Face> closed=model(id,mask);
        if(!rear)return closed;
        double back=id.equals("angled_front_1x2")?2:1;
        List<CanopyMesh.Face> result=new ArrayList<>();
        // Keep the complete authored rear frame, including its uprights and sill.
        // Only the rear glazing opens into the connected cabin.
        for(CanopyMesh.Face face:closed){
            boolean rearGlass=face.baseMaterial.equals("glass");
            for(Vec3d vertex:face.vertices)if(vertex.z<back-1D/8)rearGlass=false;
            if(!rearGlass)result.add(face);
        }
        return result;
    }
    static List<CanopyMesh.Face> withRoof(List<CanopyMesh.Face> aperture,List<CanopyMesh.Face> roof){
        List<CanopyMesh.Face> result=new ArrayList<>();
        // Retain the fixed roof frame while joining the aperture underneath it.
        // Taking the complete roof from one variant avoids overlapping extended glazing.
        for(CanopyMesh.Face face:aperture)if(!roof(face))result.add(face);
        for(CanopyMesh.Face face:roof)if(roof(face))result.add(face);
        return result;
    }
    private static boolean roof(CanopyMesh.Face face){
        for(Vec3d vertex:face.vertices)if(vertex.y<14D/16)return false;
        return true;
    }
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
