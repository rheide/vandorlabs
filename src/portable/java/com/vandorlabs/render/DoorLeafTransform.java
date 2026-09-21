package com.vandorlabs.render;

/** Renderer-neutral transform for one detailed door leaf. Units are blocks/degrees. */
public final class DoorLeafTransform {
    public final double translateX,pivotX,pivotZ,angleDegrees;
    private DoorLeafTransform(double translateX,double pivotX,double pivotZ,double angleDegrees) {
        this.translateX=translateX; this.pivotX=pivotX; this.pivotZ=pivotZ; this.angleDegrees=angleDegrees;
    }
    public static DoorLeafTransform calculate(boolean sliding,double slide,double pivotX,
            double pivotZ,double angle,double progress) {
        double p=Math.max(0,Math.min(1,progress));
        return sliding ? new DoorLeafTransform(slide*p/16,0,0,0)
                : new DoorLeafTransform(0,pivotX/16,pivotZ/16,angle*p);
    }
}
