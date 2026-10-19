package com.vandorlabs.client;

import net.minecraft.util.EnumFacing;

/** Shared regular-hex profile and gravity-aligned orientation for storage shells. */
final class AxialShellGeometry {
    private static final double H=Math.sqrt(3)/4;
    static final double[][] HEX={{.25,.5-H},{.75,.5-H},{1,.5},{.75,.5+H},{.25,.5+H},{0,.5}};
    static final double[][] BLOCK={{0,0},{1,0},{1,1},{0,1}};
    static double[] orient(double[] p,EnumFacing f){return f.getAxis()==EnumFacing.Axis.X?new double[]{p[1],p[2],p[0]}:f.getAxis()==EnumFacing.Axis.Z?new double[]{p[0],p[2],p[1]}:p;}
    static float[] normal(double[][] outline,int edge,EnumFacing f){double[] a=outline[edge],b=outline[(edge+1)%outline.length];double length=Math.hypot(b[0]-a[0],b[1]-a[1]);double[] n=orient(new double[]{(b[1]-a[1])/length,0,(a[0]-b[0])/length},f);return new float[]{(float)n[0],(float)n[1],(float)n[2]};}
}
