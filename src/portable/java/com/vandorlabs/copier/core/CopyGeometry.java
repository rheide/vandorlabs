package com.vandorlabs.copier.core;

/** Blueprint centering, independent of world/vehicle implementations. */
public final class CopyGeometry {
    public static int[] center(int[] sourceBounds,int[] volume,int normalAxis){
        if(sourceBounds.length!=6||volume.length!=6||normalAxis<0||normalAxis>2)throw new IllegalArgumentException("Copy centering schema");
        int[] shift=new int[3];for(int axis=0;axis<3;axis++){long available=(long)volume[axis+3]-volume[axis]+1,span=(long)sourceBounds[axis+3]-sourceBounds[axis]+1;if(span<1||available<1||(normalAxis==axis?(long)sourceBounds[axis+3]+1:span)>available)throw new IllegalArgumentException("Design does not fit the output Surface area and height");if(normalAxis!=axis)shift[axis]=(int)((available-span)/2-sourceBounds[axis]);}return shift;
    }
    private CopyGeometry(){}
}
