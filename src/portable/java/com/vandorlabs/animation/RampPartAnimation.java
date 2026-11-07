package com.vandorlabs.animation;

import com.vandorlabs.ramp.ControllerPlatform;

/** Cosmetic Ramp progress and belt phase driven only by a pause-aware local clock. */
public final class RampPartAnimation {
    private boolean initialized,moving,belt;
    private long revision;
    private int duration;
    private double pose,target,distance,velocity,time;

    public void sample(long nextRevision,boolean nextMoving,double observed,double nextTarget,int nextDuration,
            boolean nextBelt,double observedDistance,double nextVelocity,double now){
        if(!finite(observed)||observed<0||observed>1||!finite(nextTarget)||nextTarget<0||nextTarget>1
                ||nextDuration<1||!finite(observedDistance)||!finite(nextVelocity)||!finite(now))
            throw new IllegalArgumentException("Ramp cosmetic projection");
        if(!initialized){
            initialized=true;revision=nextRevision;pose=observed;target=nextTarget;moving=nextMoving;
            duration=nextDuration;belt=nextBelt;distance=nextBelt?observedDistance:0;velocity=nextVelocity;time=now;
            return;
        }
        advance(now);
        if(nextRevision<=revision)return;
        revision=nextRevision;
        if(!nextMoving)pose=observed;
        target=nextTarget;moving=nextMoving;duration=nextDuration;
        // A stopped/blocked belt must display its authoritative processed endpoint.
        if(!nextBelt)distance=0;
        else if(!belt||nextVelocity==0)distance=observedDistance;
        belt=nextBelt;velocity=nextVelocity;
    }
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
    private void advance(double now){
        double elapsed=Math.max(0,now-time);
        if(moving)pose=ControllerPlatform.pose(pose,target,elapsed,duration);
        if(belt)distance+=elapsed*velocity;
        time=Math.max(time,now);
    }
    public double pose(){return pose;}
    public double distance(){return distance;}
    public long revision(){return revision;}
}
