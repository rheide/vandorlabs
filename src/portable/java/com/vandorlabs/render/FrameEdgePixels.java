package com.vandorlabs.render;

import java.awt.image.BufferedImage;

/** Authored frame border inspection/crops, shared across resource reload adapters. */
public final class FrameEdgePixels {
    public static final int TOP=0,BOTTOM=1,LEFT=2,RIGHT=3;
    private FrameEdgePixels() { }
    public static int extent(BufferedImage image,int edge,int width,int height){
        int along=edge<2?height:width,depth=edge<2?width:height,extent=0;
        for(int at=along/4;at<along-along/4;at++)for(int d=0;d<depth/3;d++){
            int x=edge==0?d:edge==1?width-1-d:at,y=edge==2?d:edge==3?height-1-d:at;
            if((image.getRGB(x,y)>>>24)>=32)extent=Math.max(extent,d+1);
        }
        return extent;
    }
    public static BufferedImage crop(BufferedImage image,int edge){
        int w=image.getWidth(),h=image.getHeight();boolean vertical=edge==LEFT||edge==RIGHT;
        int along=vertical?h:w,depth=vertical?w:h;
        // Keep the complete authored edge, including its end details. The former
        // largest-rectangle search silently cropped away directional decorations.
        int band=Math.max(1,extent(image,edge==TOP?2:edge==BOTTOM?3:edge==LEFT?0:1,w,h));
        int start=band;
        for(int at=along/4;at<along-along/4;at++)for(int d=0;d<band;d++)
            if((pixel(image,edge,at,d)>>>24)>=128)start=Math.min(start,d);
        start=Math.min(start,band-1);
        BufferedImage out=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<64;x++){
            int at=Math.min(along-1,x*along/64),fallback=0;
            for(int d=0;d<depth/3;d++)if((pixel(image,edge,at,d)>>>24)>=240){fallback=pixel(image,edge,at,d);break;}
            if(fallback==0){for(int a=0;a<along&&fallback==0;a++)for(int d=0;d<depth/3;d++)if((pixel(image,edge,a,d)>>>24)>=240){fallback=pixel(image,edge,a,d);break;}}
            if(fallback==0)throw new IllegalArgumentException("Frame artwork contains no opaque material");
            for(int y=0;y<64;y++){int color=pixel(image,edge,at,Math.min(band-1,start+y*(band-start)/64));if((color>>>24)>=240)fallback=color;out.setRGB(x,y,fallback|0xff000000);}
        }
        return out;
    }
    private static int pixel(BufferedImage image,int edge,int along,int depth){
        int w=image.getWidth(),h=image.getHeight();return edge==TOP?image.getRGB(along,depth):edge==BOTTOM?image.getRGB(along,h-1-depth):edge==LEFT?image.getRGB(depth,along):image.getRGB(w-1-depth,along);
    }
}
