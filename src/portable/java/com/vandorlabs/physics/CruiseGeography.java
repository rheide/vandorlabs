package com.vandorlabs.physics;

/** Fractional geographic samples shared by the ground image and the moving map. */
public final class CruiseGeography {
    private boolean initialized;
    private double fromX,fromZ,toX,toZ,arrival,duration;

    public static int fractions(double x,double z){
        return fraction(x)|(fraction(z)<<8);
    }
    private static int fraction(double value){return (int)((value-Math.floor(value))*256);}
    public void clear(){initialized=false;}
    public void accept(int blockX,int blockZ,int fractions,double tick){
        double x=blockX+(fractions&255)/256.0,z=blockZ+((fractions>>>8)&255)/256.0;
        if(initialized&&x==toX&&z==toZ)return;
        if(!initialized||Math.abs(toX-x)>512||Math.abs(toZ-z)>512){
            fromX=x;fromZ=z;duration=1;
        }else{
            fromX=at(true,tick);fromZ=at(false,tick);
            duration=Math.max(1,Math.min(4,tick-arrival));
        }
        toX=x;toZ=z;arrival=tick;initialized=true;
    }
    public double at(boolean east,double tick){
        double t=Math.max(0,Math.min(1,(tick-arrival)/duration));
        return east?fromX+(toX-fromX)*t:fromZ+(toZ-fromZ)*t;
    }
}
