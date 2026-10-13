package com.vandorlabs.canopy;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** One local pose for rendering, collision, picking and transported canopies. */
public final class CanopyPose {
    public static List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,String kind,int width,
            LargeCanopySettings settings,double progress) {
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        boolean large=LargeCanopySettings.supports(kind);
        for(CanopyMesh.Face original:source) {
            CanopyMesh.Face face=new CanopyMesh.Face();
            face.vertices=new Vec3d[original.vertices.length];face.uv=original.uv;
            face.glazing=original.glazing || original.baseMaterial.equals("glass");face.baseMaterial=original.baseMaterial;face.material=original.material;
            face.tiled=original.tiled;face.doubleSided=original.doubleSided;face.group="posed";
            for(int i=0;i<face.vertices.length;i++) {
                Vec3d v=original.vertices[i];
                if(large && settings.openable) v=hinge(v,width,settings.hinge,progress);
                else if(kind.equals("canopy"))
                    v=CanopyMesh.pose(v,settings.motion==0?"slide":"hinge",1,progress,original.group.equals("shell"),settings.slideForward);
                if(settings.mount==1) v=new Vec3d(v.x,1-v.y,v.z);
                else if(settings.mount==2) v=new Vec3d(v.x,v.z,width-v.y);
                else if(settings.mount==3) v=new Vec3d(v.x,1-v.z,v.y);
                face.vertices[i]=v;
            }
            if(settings.mount==1) {
                Vec3d[] transformed=face.vertices.clone();face.uv=new double[original.uv.length][];
                for(int i=0;i<face.vertices.length;i++){int j=face.vertices.length-1-i;face.vertices[i]=transformed[j];face.uv[i]=original.uv[j];}
            }
            result.add(face);
        }
        return Collections.unmodifiableList(result);
    }
    private static Vec3d hinge(Vec3d v,int width,int hinge,double progress) {
        double p=progress*progress*(3-2*progress),a=Math.toRadians(105*p);
        if(hinge<2) {
            double pivot=hinge==0?0:width,x=v.x-pivot,z=v.z;
            if(hinge==0)a=-a;
            return new Vec3d(pivot+x*Math.cos(a)+z*Math.sin(a),v.y,-x*Math.sin(a)+z*Math.cos(a));
        }
        double pivot=hinge==2?width:0,y=v.y-pivot,z=v.z;
        if(hinge==2)a=-a;
        return new Vec3d(v.x,pivot+y*Math.cos(a)-z*Math.sin(a),y*Math.sin(a)+z*Math.cos(a));
    }
    private CanopyPose() {}
}
