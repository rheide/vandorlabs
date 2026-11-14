package com.vandorlabs.render;

/** Exact 8-bit source-over compensation; the authored RGB artwork is untouched. */
public final class GlassPanePixels {
    public static int singleAlpha(int alpha){
        if(alpha<0||alpha>255)throw new IllegalArgumentException("Alpha outside byte range");
        return 255-((255-alpha)*(255-alpha)+127)/255;
    }
    public static int single(int pixel){return (singleAlpha(pixel>>>24)<<24)|(pixel&0xffffff);}
    public static int dominant(int[] pixels){
        if(pixels.length==0)return 0;
        java.util.Map<Integer,Integer> counts=new java.util.HashMap<>();int best=pixels[0],count=0;
        for(int pixel:pixels){int next=counts.getOrDefault(pixel,0)+1;counts.put(pixel,next);if(next>count){best=pixel;count=next;}}
        return best;
    }
    private GlassPanePixels(){}
}
