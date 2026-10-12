package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

/** Rectangular pane/arm union, including the two-pixel overhang at slope ends. */
public final class DiagonalCornerOutline {
    private DiagonalCornerOutline() {}
    public static double left(double edge,double near,double frontArm,double backArm) {
        if(!Double.isNaN(frontArm) && near>0)edge=Math.min(edge,frontArm);
        if(!Double.isNaN(backArm) && near+4<16)edge=Math.min(edge,backArm);
        return edge;
    }
    public static double right(double edge,double near,double frontArm,double backArm) {
        if(!Double.isNaN(frontArm) && near>0)edge=Math.max(edge,frontArm+4);
        if(!Double.isNaN(backArm) && near+4<16)edge=Math.max(edge,backArm+4);
        return edge;
    }
    public static double[][] polygon(double left,double right,double front,double back,double frontArm,double backArm) {
        List<double[]> points=new ArrayList<>();points.add(new double[]{left,front});
        if(!Double.isNaN(frontArm)) {
            double a=Math.max(left,frontArm),b=Math.min(right,frontArm+4),end=Math.min(0,front);
            points.add(new double[]{a,front});points.add(new double[]{a,end});
            points.add(new double[]{b,end});points.add(new double[]{b,front});
        }
        points.add(new double[]{right,front});points.add(new double[]{right,back});
        if(!Double.isNaN(backArm)) {
            double a=Math.max(left,backArm),b=Math.min(right,backArm+4),end=Math.max(16,back);
            points.add(new double[]{b,back});points.add(new double[]{b,end});
            points.add(new double[]{a,end});points.add(new double[]{a,back});
        }
        points.add(new double[]{left,back});return points.toArray(new double[points.size()][]);
    }
}
