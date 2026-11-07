package com.vandorlabs.animation;

/** Linear sixteen-tick progress. The authored canopy transform supplies its own easing. */
public final class CanopyAnimation {
    private boolean initialized,open,waiting;
    private double pose,time;
    public double sample(boolean target,boolean blocked,double observed,double now){
        if(!Double.isFinite(observed)||observed<0||observed>1||!Double.isFinite(now))throw new IllegalArgumentException("Canopy visual sample");
        if(!initialized){initialized=true;open=target;waiting=blocked;pose=observed;time=now;return pose;}
        if(waiting!=blocked){pose=observed;waiting=blocked;open=target;time=Math.max(time,now);return pose;}
        if(open!=target){open=target;time=Math.max(time,now);return pose;}
        if(!waiting){double elapsed=Math.max(0,now-time);pose=Math.max(0,Math.min(1,pose+(open?elapsed:-elapsed)/16));}
        time=Math.max(time,now);return pose;
    }
}
