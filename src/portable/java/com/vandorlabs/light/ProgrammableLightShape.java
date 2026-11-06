package com.vandorlabs.light;

/** Exact one-box Light housing. Family ordinals are full, frame, slab; facing uses saved six-face order. */
public final class ProgrammableLightShape {
    private ProgrammableLightShape(){}
    public static double[] bounds(int family,int facing,boolean upper,boolean small){
        if(family<0||family>2||facing<0||facing>5)throw new IllegalArgumentException("Light shape");
        double x0=0,x1=1,y0=0,y1=1,z0=family==1?15/16D:0,z1=1;
        if(family==2){if(facing==1){z0=upper?0:.5;z1=upper?.5:1;}else if(facing==0){z0=upper?.5:0;z1=upper?1:.5;}else{y0=upper?.5:0;y1=upper?1:.5;}}
        if(family==1&&small){x0=.25;x1=.75;y0=.25;y1=.75;}
        switch(facing){case 0:return new double[]{x0,z0,1-y1,x1,z1,1-y0};case 1:return new double[]{x0,1-z1,y0,x1,1-z0,y1};case 3:return new double[]{1-x1,y0,1-z1,1-x0,y1,1-z0};case 4:return new double[]{z0,y0,1-x1,z1,y1,1-x0};case 5:return new double[]{1-z1,y0,x0,1-z0,y1,x1};default:return new double[]{x0,y0,z0,x1,y1,z1};}
    }
}
