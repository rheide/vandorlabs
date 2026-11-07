package com.vandorlabs.motion;

/** Released correction admission, with ordering deliberately checked before physical eligibility. */
public final class PlatformMotionPolicy {
    public static boolean header(boolean sameDimension,long sequence,double x,double z,double before,double after){return sameDimension&&Double.isFinite(x)&&Double.isFinite(z)&&Double.isFinite(before)&&Double.isFinite(after-before)&&sequence>=0;}
    public static boolean ordered(long sequence,boolean hasPrevious,long previous){return sequence<=0||!hasPrevious||sequence>previous;}
    public static boolean physical(boolean available,double velocityY,double currentX,double currentZ,double feet,double x,double z,double before,double after,double dx,double dz){
        return available&&!(velocityY>.1||!Double.isFinite(dx)||!Double.isFinite(dz)||Math.abs(dx)>4||Math.abs(dz)>4||!Double.isFinite(after)||Math.abs(currentX-x)>.75||Math.abs(currentZ-z)>.75||Math.min(Math.abs(feet-before),Math.abs(feet-after))>.5);
    }
    private PlatformMotionPolicy(){}
}
