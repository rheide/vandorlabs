package com.vandorlabs.render;

/** Exact 8-bit source-over compensation; the authored RGB artwork is untouched. */
public final class GlassPanePixels {
    public static int singleAlpha(int alpha){
        if(alpha<0||alpha>255)throw new IllegalArgumentException("Alpha outside byte range");
        return 255-((255-alpha)*(255-alpha)+127)/255;
    }
    public static int single(int pixel){return (singleAlpha(pixel>>>24)<<24)|(pixel&0xffffff);}
    /** Lowest visible alpha that survives Minecraft's GL_GREATER 0.1 terrain test with a one-byte margin. */
    public static final int NATIVE_ALPHA_FLOOR=27;
    /** Raise faint visible pixels to the native floor; fully transparent pixels stay transparent. */
    public static int floor(int pixel){int alpha=pixel>>>24;return alpha>0&&alpha<NATIVE_ALPHA_FLOOR?(NATIVE_ALPHA_FLOOR<<24)|(pixel&0xffffff):pixel;}
    /** The stored pixel of a native chunk pane: optional single-sheet compensation, then the visibility floor. */
    public static int nativePixel(int pixel,boolean single){return floor(single?single(pixel):pixel);}
    public static int dominant(int[] pixels){
        if(pixels.length==0)return 0;
        java.util.Map<Integer,Integer> counts=new java.util.HashMap<>();int best=pixels[0],count=0;
        for(int pixel:pixels){int next=counts.getOrDefault(pixel,0)+1;counts.put(pixel,next);if(next>count){best=pixel;count=next;}}
        return best;
    }
    private GlassPanePixels(){}
}
