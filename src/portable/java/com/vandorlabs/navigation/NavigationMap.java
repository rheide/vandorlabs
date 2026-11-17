package com.vandorlabs.navigation;

/** Version-neutral map bounds, coverage and controls. Coordinates are world X/Z, north up. */
public final class NavigationMap {
    public static final int SURFACE=-5, SIZE=96, LIMIT=29999984;
    public static final int OUTSIDE=0xff121d29, ERROR=0xffb746b8;
    public static final double MAP_TOP=.10, MAP_BOTTOM=.84;
    public static final int[] SPANS={128,256,512,1024,2048,4096,8192,16384,32768};
    private NavigationMap(){}
    public static int bound(long value){return (int)Math.max(-LIMIT,Math.min(LIMIT,value));}
    public static boolean covers(int x,int z,int cx,int cz,int radius){long dx=(long)x-cx,dz=(long)z-cz;return dx*dx+dz*dz<=(long)radius*radius;}
    public static int action(double u,double v){return u>=0&&u<1&&v>=MAP_BOTTOM&&v<=1?Math.min(6,(int)(u*7))+1:0;}
    public static int x(int center,int zoom,int pixel){return bound(Math.round(center+(pixel+.5-SIZE/2.0)*SPANS[zoom]/SIZE));}
    public static int z(int center,int zoom,int pixel,double aspect){return bound(Math.round(center+(pixel+.5-SIZE/2.0)*SPANS[zoom]*(MAP_BOTTOM-MAP_TOP)/(SIZE*aspect)));}
}
