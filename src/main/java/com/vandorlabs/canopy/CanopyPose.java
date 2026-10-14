package com.vandorlabs.canopy;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** One local pose for rendering, collision, picking and transported canopies. */
public final class CanopyPose {
    // Clear the one-pixel selection/collision rail and separate the back frame
    // from the supporting floor or ceiling without scaling this gap with depth.
    public static final double FRONT_MOUNT_CLEARANCE=1D/16+.004;
    public static List<CanopyMesh.Face> faces(List<CanopyMesh.Face> source,String kind,int width,
            LargeCanopySettings settings,double progress) {
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());
        for(CanopyMesh.Face original:source) {
            CanopyMesh.Face face=new CanopyMesh.Face();
            face.vertices=new Vec3d[original.vertices.length];face.uv=original.uv;
            face.glazing=original.glazing || original.baseMaterial.equals("glass");face.baseMaterial=original.baseMaterial;face.material=original.material;
            face.tiled=original.tiled;face.doubleSided=original.doubleSided;face.group=original.group;
            for(int i=0;i<face.vertices.length;i++) {
                Vec3d v=original.vertices[i];
                v=vertex(v,original.group,kind,width,settings,progress);
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
    public static Vec3d vertex(Vec3d v,String group,String kind,int width,LargeCanopySettings settings,double progress){
        if(LargeCanopySettings.supports(kind) && settings.openable)v=hinge(v,width,settings.hinge,progress);
        else if(kind.equals("canopy"))v=CanopyMesh.pose(v,settings.motion==0?"slide":"hinge",1,progress,group.equals("shell"),settings.slideForward);
        if(settings.mount==1)return new Vec3d(v.x,1-v.y,v.z);
        double clearance=LargeCanopySettings.supports(kind)?FRONT_MOUNT_CLEARANCE:0;
        if(settings.mount==2)return new Vec3d(v.x,v.z+clearance,width-v.y);
        if(settings.mount==3)return new Vec3d(v.x,1-v.z-clearance,v.y);
        return v;
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
