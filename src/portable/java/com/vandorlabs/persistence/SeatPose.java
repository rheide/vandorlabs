package com.vandorlabs.persistence;

/** Authored cushion markers and yaw. Native riding offsets remain platform-specific. */
public final class SeatPose {
    public final double x,y,z,yaw;
    public SeatPose(double y,double facingYaw,int angle){this.x=.5;this.y=y;this.z=.5;this.yaw=yaw(facingYaw,angle);}
    public static int angleDegrees(int angle){return ChairSettings.angle(angle)*45;}
    public static double yaw(double facingYaw,int angle){return facingYaw+angleDegrees(angle);}
}

