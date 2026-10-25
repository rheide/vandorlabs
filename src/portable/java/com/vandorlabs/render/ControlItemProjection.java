package com.vandorlabs.render;

/** Legacy GUI rotations and padded control footprint, independent of the native model API. */
public final class ControlItemProjection {
    private ControlItemProjection(){}
    public static ControlMount.Point project(ControlMount.Point p,double rx,double ry,double rz,double sx,double sy,double sz){double x=(p.x-.5)*sx,y=(p.y-.5)*sy,z=(p.z-.5)*sz;rz=Math.toRadians(rz);rx=Math.toRadians(rx);ry=Math.toRadians(ry);double xx=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=xx;double xxY=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xxY;return new ControlMount.Point(x,y*Math.cos(rx)-z*Math.sin(rx),0);}
    public static float scale(double x0,double y0,double x1,double y1){return (float)Math.min(.75/Math.max(x1-x0,y1-y0),.85);}
}
