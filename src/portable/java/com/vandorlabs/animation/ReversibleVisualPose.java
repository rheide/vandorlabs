package com.vandorlabs.animation;

/** Client-local eased motion; repeated authoritative targets never restart its clock. */
public final class ReversibleVisualPose {
    private final double duration;
    private boolean initialized,target;
    private double from,started,lastTime;
    public ReversibleVisualPose(double duration){
        if(!Double.isFinite(duration)||duration<=0)throw new IllegalArgumentException("Invalid visual duration");
        this.duration=duration;
    }
    public double sample(boolean next,double now,double offset){
        if(!Double.isFinite(now)||!Double.isFinite(offset))throw new IllegalArgumentException("Invalid visual time");
        if(!initialized){initialized=true;target=next;from=next?1:0;started=now-duration;lastTime=now;}
        now=Math.max(now,lastTime);
        if(next!=target){from=pose(now);target=next;started=now;}
        lastTime=now;
        return pose(now+offset);
    }
    private double pose(double now){
        double t=Math.max(0,Math.min(1,(now-started)/duration));t=t*t*(3-2*t);
        return from+((target?1:0)-from)*t;
    }
}
