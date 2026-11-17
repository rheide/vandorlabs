package com.vandorlabs.navigation;

/** Observation radius in blocks; independent of transmitter connection range. */
public final class NavigationRange {
    private NavigationRange(){}
    public static int radius(int base,double height){return radius(base,height,1);}
    public static int radius(int base,double height,double linearScale){
        if(base<=0||!Double.isFinite(height)||!Double.isFinite(linearScale)||linearScale<1)return 0;
        double altitude=Math.max(.125,Math.min(1,height/128)),bonus=Math.min(1,linearScale-1)*Math.max(0,Math.min(1,(height-64)/64));
        return (int)Math.round(base*(altitude+bonus));
    }
}
