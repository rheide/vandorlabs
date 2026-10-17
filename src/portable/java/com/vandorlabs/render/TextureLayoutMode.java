package com.vandorlabs.render;

/** Stable saved layout IDs; legacy Fit means unrestricted Stretch. */
public final class TextureLayoutMode {
    public static final int STRETCH=0,TILE=1,FIT=2;
    public static boolean valid(int mode){return mode>=STRETCH && mode<=FIT;}
    public static String label(int mode){return mode==TILE?"Tile":mode==FIT?"Fit (aspect)":"Stretch";}
    public static int next(int mode){return (mode+1)%3;}
    public static boolean fit(int mode,double width,double height,double imageAspect){
        if(mode==STRETCH)return true;
        return mode==FIT && width>0 && height>0 && imageAspect>0 && Math.abs(width/height/imageAspect-1)<.005;
    }
    public static double tileU(double u,double imageAspect){return u/Math.max(1,imageAspect);}
    public static double tileV(double v,double imageAspect){return v*Math.min(1,imageAspect);}
    private TextureLayoutMode(){}
}
