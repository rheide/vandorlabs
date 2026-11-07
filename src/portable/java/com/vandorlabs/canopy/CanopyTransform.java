package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;

/** One released rigid transform per pose; no trigonometry or settings lookup per vertex. */
public final class CanopyTransform {
    public static final double FRONT_MOUNT_CLEARANCE=1D/16+.004;
    private final boolean large,opening,slide;
    private final int width,mount,hinge,depth;
    private final double cos,sin,lift,travel,clearance;
    public CanopyTransform(String kind,int width,CanopySettingsData settings,double progress) {
        this.width=width;mount=settings.mount;hinge=settings.hinge;
        large=CanopySettingsData.supports(kind)&&settings.openable;
        opening="canopy".equals(kind)||"long_canopy".equals(kind);slide=settings.motion==0;
        depth="long_canopy".equals(kind)?3:2;
        // Java 8 divides before multiplying by PI; newer Math.toRadians changes the rounded result.
        double pose=progress*progress*(3-2*progress),angle=(105*pose)/180D*Math.PI;
        if(large&&(hinge==0||hinge==2))angle=-angle;
        cos=large||opening&&!slide?Math.cos(angle):1;sin=large||opening&&!slide?Math.sin(angle):0;
        lift=Math.min(1,pose*8)/16D;travel=(settings.slideForward?-depth:depth)*pose;
        clearance=CanopySettingsData.supports(kind)?FRONT_MOUNT_CLEARANCE:0;
    }
    public CanopyPoint apply(CanopyPoint vertex,String group) {
        double x=vertex.x,y=vertex.y,z=vertex.z;
        if(large) {
            if(hinge<2){double pivot=hinge==0?0:width,dx=x-pivot;double nextX=pivot+dx*cos+z*sin;z=-dx*sin+z*cos;x=nextX;}
            else{double pivot=hinge==2?width:0,dy=y-pivot;double nextY=pivot+dy*cos-z*sin;z=dy*sin+z*cos;y=nextY;}
        }else if(opening&&"shell".equals(group)) {
            if(slide){y+=lift;z+=travel;}
            else{double dy=y-1,dz=z-depth;double nextY=1+dy*cos-dz*sin;z=depth+dy*sin+dz*cos;y=nextY;}
        }
        if(mount==1)y=1-y;
        else if(mount==2){double nextY=z+clearance;z=width-y;y=nextY;}
        else if(mount==3){double nextY=1-z-clearance;z=y;y=nextY;}
        return x==vertex.x&&y==vertex.y&&z==vertex.z?vertex:new CanopyPoint(x,y,z);
    }
    /** Source local +Z is rear; rotation is around the placement cell center. */
    public static CanopyPoint rotate(CanopyPoint vertex,int facing) {
        double x=vertex.x-.5,z=vertex.z-.5;
        switch(facing){case 3:return new CanopyPoint(.5-x,vertex.y,.5-z);case 5:return new CanopyPoint(.5-z,vertex.y,.5+x);case 4:return new CanopyPoint(.5+z,vertex.y,.5-x);case 2:return vertex;default:throw new IllegalArgumentException("Canopy horizontal facing");}
    }
}
