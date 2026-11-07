package com.vandorlabs.motion;

/** Released support tolerances and axis clipping, independent of entities, levels and rendering. */
public final class PlatformSupportPolicy {
    public static boolean available(boolean removed,boolean noClip,boolean riding,boolean flying){return !removed&&!noClip&&!riding&&!flying;}
    public static boolean eligible(boolean available,double motionY){return available&&motionY<=.1;}
    /** NaN removes a support entirely above the feet; otherwise retain or clip its top. */
    public static double supportTop(double minY,double maxY,double feet){
        if(maxY>feet&&maxY-feet<=.5)return minY>=feet?Double.NaN:feet;
        return maxY;
    }
    public static boolean recent(boolean sameDimension,long age,double x,double z,double oldX,double oldZ){return sameDimension&&!(age<0||age>20||Math.abs(x-oldX)>.75||Math.abs(z-oldZ)>.75);}
    public static boolean provenFloor(double minX,double maxX,double minZ,double maxZ,double top,double oldX,double oldZ,double height){return !(Math.abs(top-height)>.001||oldX<minX-.3||oldX>maxX+.3||oldZ<minZ-.3||oldZ>maxZ+.3);}
    /** Exact old AABB offset rule; touching only on another axis is not overlap. */
    public static double axisOffset(double obstacleMin,double obstacleMax,double bodyMin,double bodyMax,double delta,boolean overlap){
        if(overlap){if(delta>0&&bodyMax<=obstacleMin){double gap=obstacleMin-bodyMax;if(gap<delta)delta=gap;}else if(delta<0&&bodyMin>=obstacleMax){double gap=obstacleMax-bodyMin;if(gap>delta)delta=gap;}}
        return delta;
    }
    private PlatformSupportPolicy(){}
}
