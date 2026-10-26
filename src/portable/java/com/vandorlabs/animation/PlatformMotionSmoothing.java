package com.vandorlabs.animation;

/** Visual correction only: retain continuity when support packets arrive between client ticks. */
public final class PlatformMotionSmoothing {
    private double x,y,z,start,duration=1,last=Double.NaN;
    private final double maximum;
    public PlatformMotionSmoothing(){this(12);}
    public PlatformMotionSmoothing(int maximum){if(maximum<1||maximum>40)throw new IllegalArgumentException("Invalid smoothing duration");this.maximum=maximum;}
    private double remaining(double now){return Math.max(0,Math.min(1,1-(now-start)/duration));}
    public void accept(double dx,double dy,double dz,double now){
        double remaining=remaining(now);x=x*remaining-dx;y=y*remaining-dy;z=z*remaining-dz;
        if(Double.isNaN(last)||now-last>maximum*2)duration=1;else if(now-last>=.5)duration=Math.max(1,Math.min(maximum,now-last));start=last=now;
    }
    public double x(double now){return x*remaining(now);}
    public double y(double now){return y*remaining(now);}
    public double z(double now){return z*remaining(now);}
    public void clear(){x=y=z=0;last=Double.NaN;}
}
