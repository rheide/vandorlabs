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
        List<CanopyMesh.Face> result=new ArrayList<>(source.size());Transform transform=new Transform(kind,width,settings,progress);
        for(CanopyMesh.Face original:source) {
            CanopyMesh.Face face=new CanopyMesh.Face();
            face.vertices=new Vec3d[original.vertices.length];face.uv=original.uv;
            face.glazing=original.glazing || original.baseMaterial.equals("glass");face.baseMaterial=original.baseMaterial;face.material=original.material;
            face.tiled=original.tiled;face.doubleSided=original.doubleSided;face.group=original.group;
            for(int i=0;i<face.vertices.length;i++) {
                Vec3d v=original.vertices[i];
                v=transform.apply(v,original.group);
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
    /** One rigid transform per rendered pose, rather than trigonometry per vertex. */
    private static final class Transform {
        final boolean large,opening,slide;final int width,mount,hinge,depth;final double cos,sin,lift,travel,clearance;
        Transform(String kind,int width,LargeCanopySettings settings,double progress){
            this.width=width;mount=settings.mount;hinge=settings.hinge;large=LargeCanopySettings.supports(kind)&&settings.openable;opening=kind.equals("canopy")||kind.equals("long_canopy");slide=settings.motion==0;depth=kind.equals("long_canopy")?3:2;
            double pose=progress*progress*(3-2*progress),angle=Math.toRadians(105*pose);if(large&&(hinge==0||hinge==2))angle=-angle;
            cos=large||opening&&!slide?Math.cos(angle):1;sin=large||opening&&!slide?Math.sin(angle):0;
            lift=Math.min(1,pose*8)/16D;travel=(settings.slideForward?-depth:depth)*pose;clearance=LargeCanopySettings.supports(kind)?FRONT_MOUNT_CLEARANCE:0;
        }
        Vec3d apply(Vec3d vertex,String group){
            double x=vertex.x,y=vertex.y,z=vertex.z;
            if(large){
                if(hinge<2){double pivot=hinge==0?0:width,dx=x-pivot;double nextX=pivot+dx*cos+z*sin;z=-dx*sin+z*cos;x=nextX;}
                else{double pivot=hinge==2?width:0,dy=y-pivot;double nextY=pivot+dy*cos-z*sin;z=dy*sin+z*cos;y=nextY;}
            }else if(opening&&group.equals("shell")){
                if(slide){y+=lift;z+=travel;}
                else{double dy=y-1,dz=z-depth;double nextY=1+dy*cos-dz*sin;z=depth+dy*sin+dz*cos;y=nextY;}
            }
            if(mount==1)y=1-y;
            else if(mount==2){double nextY=z+clearance;z=width-y;y=nextY;}
            else if(mount==3){double nextY=1-z-clearance;z=y;y=nextY;}
            return x==vertex.x&&y==vertex.y&&z==vertex.z?vertex:new Vec3d(x,y,z);
        }
    }
    public static Vec3d vertex(Vec3d v,String group,String kind,int width,LargeCanopySettings settings,double progress){
        if(LargeCanopySettings.supports(kind) && settings.openable)v=hinge(v,width,settings.hinge,progress);
        else if(kind.equals("canopy") || kind.equals("long_canopy")) {
            int depth=kind.equals("long_canopy")?3:2;
            if(group.equals("shell")) {
                double p=progress*progress*(3-2*progress);
                if(settings.motion==0)v=v.addVector(0,Math.min(1,p*8)/16D,(settings.slideForward?-depth:depth)*p);
                else {double a=Math.toRadians(105*p),y=v.y-1,z=v.z-depth;v=new Vec3d(v.x,1+y*Math.cos(a)-z*Math.sin(a),depth+y*Math.sin(a)+z*Math.cos(a));}
            }
        }
        if(settings.mount==1)return new Vec3d(v.x,1-v.y,v.z);
        double clearance=LargeCanopySettings.supports(kind)?FRONT_MOUNT_CLEARANCE:0;
        if(settings.mount==2)return new Vec3d(v.x,v.z+clearance,width-v.y);
        if(settings.mount==3)return new Vec3d(v.x,1-v.z-clearance,v.y);
        return v;
    }
    /** Exact coordinate extrema over a motion interval; stationary axes never gain padding. */
    public static net.minecraft.util.math.AxisAlignedBB sweptBounds(CanopyMesh.Face face,String kind,int width,LargeCanopySettings settings,double from,double to){
        boolean rotating=LargeCanopySettings.supports(kind)&&settings.openable
                ||(kind.equals("canopy")||kind.equals("long_canopy"))&&settings.motion!=0&&face.group.equals("shell");
        double angle=Math.toRadians(105),middle=angle/2,c=Math.cos(middle)-1,s=Math.sin(middle),d=Math.cos(angle)-1,t=Math.sin(angle),det=c*t-d*s;
        double low=angle*Math.min(from*from*(3-2*from),to*to*(3-2*to)),high=angle*Math.max(from*from*(3-2*from),to*to*(3-2*to));
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Vec3d vertex:face.vertices){
            Vec3d a=vertex(vertex,face.group,kind,width,settings,from),b=vertex(vertex,face.group,kind,width,settings,to);
            Vec3d zero=rotating?vertex(vertex,face.group,kind,width,settings,0):a,half=rotating?vertex(vertex,face.group,kind,width,settings,.5):a,end=rotating?vertex(vertex,face.group,kind,width,settings,1):b;
            double[] av={a.x,a.y,a.z},bv={b.x,b.y,b.z},zv={zero.x,zero.y,zero.z},hv={half.x,half.y,half.z},ev={end.x,end.y,end.z};
            for(int axis=0;axis<3;axis++){
                min[axis]=Math.min(min[axis],Math.min(av[axis],bv[axis]));max[axis]=Math.max(max[axis],Math.max(av[axis],bv[axis]));
                if(!rotating)continue;
                double ac=((hv[axis]-zv[axis])*t-(ev[axis]-zv[axis])*s)/det,bs=(c*(ev[axis]-zv[axis])-d*(hv[axis]-zv[axis]))/det,constant=zv[axis]-ac;
                if(Math.abs(ac)+Math.abs(bs)<1e-12)continue;
                double extremum=Math.atan2(bs,ac);
                for(int turn=-2;turn<=2;turn++){double theta=extremum+turn*Math.PI;if(theta>low&&theta<high){double value=constant+ac*Math.cos(theta)+bs*Math.sin(theta);min[axis]=Math.min(min[axis],value);max[axis]=Math.max(max[axis],value);}}
            }
        }
        return new net.minecraft.util.math.AxisAlignedBB(min[0],min[1],min[2],max[0],max[1],max[2]);
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
