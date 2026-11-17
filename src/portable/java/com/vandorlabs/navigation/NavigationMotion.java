package com.vandorlabs.navigation;

/** Pause-clock map interpolation; displayed terrain keeps its geographic coordinates while work is pending. */
public final class NavigationMotion {
    public static final double DURATION=12;
    public boolean active;public double targetX,targetZ,targetScale;private double fromX,fromZ,fromScale,start;
    private double blend(double now){double t=Math.max(0,Math.min(1,(now-start)/DURATION));return t*t*(3-2*t);}
    public double x(double now){double t=blend(now);return fromX+(targetX-fromX)*t;}
    public double z(double now){double t=blend(now);return fromZ+(targetZ-fromZ)*t;}
    public double scale(double now){double t=blend(now);return fromScale>0?fromScale*Math.pow(targetScale/fromScale,t):targetScale;}
    public boolean settling(double now){return active&&now-start<DURATION;}
    public void snap(double x,double z,double scale){fromX=targetX=x;fromZ=targetZ=z;fromScale=targetScale=scale;active=false;}
    public void move(double x,double z,double scale,double now){double px=x(now),pz=z(now),ps=scale(now);fromX=px;fromZ=pz;fromScale=ps;targetX=x;targetZ=z;targetScale=scale;start=now;active=true;}
    public static int pulse(double now){int shade=(int)Math.round(56+32*Math.sin(now*Math.PI/40));return 0xff000000|shade<<16|shade<<8|shade;}
}
