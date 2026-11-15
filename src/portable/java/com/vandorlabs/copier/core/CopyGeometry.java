package com.vandorlabs.copier.core;

import com.vandorlabs.turntable.TurntableGeometry;

/** Source copy prisms and blueprint centering, independent of world/vehicle implementations. */
public final class CopyGeometry {
    public static int[] volume(TurntableGeometry.Face face,int minU,int minV,int plane,int width,int height,int depth){
        if(width<1||width>32||height<1||height>32||depth<1||depth>16)throw new IllegalArgumentException("Copy volume bounds");
        int u2=Math.addExact(minU,width-1),v2=Math.addExact(minV,height-1),a=Math.addExact(plane,1),b=Math.addExact(plane,depth);
        int[] first={face.ux*minU+face.vx*minV+face.x*a,face.uy*minU+face.vy*minV+face.y*a,face.uz*minU+face.vz*minV+face.z*a};
        int[] last={face.ux*u2+face.vx*v2+face.x*b,face.uy*u2+face.vy*v2+face.y*b,face.uz*u2+face.vz*v2+face.z*b};
        return new int[]{Math.min(first[0],last[0]),Math.min(first[1],last[1]),Math.min(first[2],last[2]),Math.max(first[0],last[0]),Math.max(first[1],last[1]),Math.max(first[2],last[2])};
    }
    public static int[] center(int[] sourceBounds,int[] volume,int normalAxis){
        if(sourceBounds.length!=6||volume.length!=6||normalAxis<0||normalAxis>2)throw new IllegalArgumentException("Copy centering schema");
        int[] shift=new int[3];for(int axis=0;axis<3;axis++){long available=(long)volume[axis+3]-volume[axis]+1,span=(long)sourceBounds[axis+3]-sourceBounds[axis]+1;if(span<1||available<1||(normalAxis==axis?(long)sourceBounds[axis+3]+1:span)>available)throw new IllegalArgumentException("Design does not fit the output Surface area and height");if(normalAxis!=axis)shift[axis]=(int)((available-span)/2-sourceBounds[axis]);}return shift;
    }
    private CopyGeometry(){}
}
