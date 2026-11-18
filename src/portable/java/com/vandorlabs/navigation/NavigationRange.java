package com.vandorlabs.navigation;

/** Observation radius in blocks; independent of transmitter connection range. */
public final class NavigationRange {
    public static final int SENTINEL_BASE=500, SURVEY_BASE=2000;
    public static final int SENTINEL_RADAR_RADIUS=128, SURVEY_RADAR_RADIUS=256;

    public static int radarRadius(String design){
        return "standalone_antenna".equals(design)?SENTINEL_RADAR_RADIUS:
                "standalone_dish".equals(design)?SURVEY_RADAR_RADIUS:0;
    }
    public static boolean detects(double seaLevel,double x,double y,double z,boolean crouched,int centerX,int centerZ,int radius){
        double dx=x-centerX,dz=z-centerZ;
        return radius>0&&!crouched&&y>seaLevel&&dx*dx+dz*dz<=radius*(double)radius;
    }
    private NavigationRange(){}
    public static int radius(int base,double height){return radius(base,height,1);}
    public static int radius(int base,double height,double linearScale){
        if(base<=0||!Double.isFinite(height)||!Double.isFinite(linearScale)||linearScale<1)return 0;
        double altitude=Math.max(.125,Math.min(1,height/128)),bonus=Math.min(1,linearScale-1)*Math.max(0,Math.min(1,(height-64)/64));
        return (int)Math.round(base*(altitude+bonus));
    }
}
