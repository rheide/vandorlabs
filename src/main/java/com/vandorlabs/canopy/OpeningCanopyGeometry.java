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
        String key=width+":"+depth+":"+angle;
        List<CanopyMesh.Face> cached=CACHE.get(key);if(cached!=null)return cached;
        List<CanopyMesh.Face> result=new ArrayList<>();
        for(CanopyFace source:CanopyShapes.opening(OpeningCanopyGeometry::model,width,depth,angle)) {
            CanopyMesh.Face face=new CanopyMesh.Face();
            face.material=source.material;face.baseMaterial=source.baseMaterial;face.group=source.group;
            face.doubleSided=source.doubleSided;
            face.vertices=new Vec3d[source.points()];face.uv=new double[source.points()][];
            for(int i=0;i<source.points();i++){
                CanopyPoint vertex=source.vertex(i);
                face.vertices[i]=new Vec3d(vertex.x,vertex.y,vertex.z);
                face.uv[i]=new double[]{source.u(i),source.v(i)};
            }
            result.add(face);
        }
        cached=Collections.unmodifiableList(result);CACHE.put(key,cached);return cached;
    }
    private static List<CanopyFace> model(String id){
        List<CanopyFace> result=new ArrayList<>();
        for(CanopyMesh.Face face:CanopyMesh.MODELS.get(id)){
            CanopyPoint[] points=new CanopyPoint[face.vertices.length];
            for(int i=0;i<points.length;i++){
                Vec3d vertex=face.vertices[i];points[i]=new CanopyPoint(vertex.x,vertex.y,vertex.z);
            }
            result.add(new CanopyFace(points,face.uv,face.material,face.baseMaterial,face.group,face.doubleSided,false,false));
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
    private OpeningCanopyGeometry(){}
}
