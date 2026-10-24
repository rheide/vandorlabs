package com.vandorlabs.render;

import java.awt.image.BufferedImage;

/** Authored crop and edge padding used by square atlas sprites on both targets. */
public final class TextureImagePadding {
    public static final class Result {
        public final BufferedImage image;
        public final int width,height;
        public final float usedU,usedV;
        private Result(BufferedImage image,int width,int height){this.image=image;this.width=width;this.height=height;usedU=(float)width/image.getWidth();usedV=(float)height/image.getHeight();}
    }
    public static Result pad(BufferedImage source,boolean crop,boolean half){
        int x0=crop?source.getWidth()/16:0,y0=crop?source.getHeight()/32:0;
        int width=source.getWidth()-x0,height=source.getHeight()-2*y0;
        if(half){height/=2;y0+=height;}
        if(width<1||height<1)throw new IllegalArgumentException("Texture crop has no pixels");
        int canvas=1;while(canvas<Math.max(width,height))canvas<<=1;
        int[] pixels=new int[canvas*canvas];
        for(int y=0;y<canvas;y++)for(int x=0;x<canvas;x++)pixels[y*canvas+x]=source.getRGB(x0+Math.min(x,width-1),y0+Math.min(y,height-1));
        BufferedImage padded=new BufferedImage(canvas,canvas,BufferedImage.TYPE_INT_ARGB);padded.setRGB(0,0,canvas,canvas,pixels,0,canvas);
        return new Result(padded,width,height);
    }
    private TextureImagePadding(){}
}
