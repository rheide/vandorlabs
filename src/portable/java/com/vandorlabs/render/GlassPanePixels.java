package com.vandorlabs.render;

import java.util.HashMap;
import java.util.Map;

/** Pixel operations shared by atlas adaptation and its non-GL contracts. */
public final class GlassPanePixels {
    /** One source-over surface with the transmittance of two identical ones. */
    public static int singleAlpha(int alpha){
        if(alpha<0||alpha>255)throw new IllegalArgumentException("Alpha outside byte range");
        return (510*alpha-alpha*alpha+127)/255;
    }
    public static int singlePixel(int argb){return (argb&0x00ffffff)|(singleAlpha(argb>>>24)<<24);}
    public static boolean uniform(int[] pixels){
        if(pixels==null||pixels.length==0)return false;
        int first=pixels[0];for(int value:pixels)if(value!=first)return false;return true;
    }
    /** Preserve the authored base color; do not average shimmer into the tint. */
    public static int dominant(int[] pixels){
        if(pixels==null||pixels.length==0)throw new IllegalArgumentException("Empty glass pixels");
        Map<Integer,Integer> counts=new HashMap<>();int best=pixels[0],bestCount=0;
        for(int value:pixels){int count=counts.getOrDefault(value,0)+1;counts.put(value,count);if(count>bestCount){best=value;bestCount=count;}}
        return best;
    }
    private GlassPanePixels(){}
}
