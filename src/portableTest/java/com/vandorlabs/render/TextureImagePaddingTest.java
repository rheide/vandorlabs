package com.vandorlabs.render;

import java.awt.image.BufferedImage;

public final class TextureImagePaddingTest {
    private static int assertions;
    private static void check(boolean value){assertions++;if(!value)throw new AssertionError("Authored texture padding contract");}
    public static void main(String[] args){
        BufferedImage source=new BufferedImage(32,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<32;x++)source.setRGB(x,y,0xff000000|x<<8|y);
        TextureImagePadding.Result whole=TextureImagePadding.pad(source,false,false);
        check(whole.image.getWidth()==64&&whole.width==32&&whole.height==64&&whole.usedU==.5f&&whole.usedV==1);
        check(whole.image.getRGB(63,63)==source.getRGB(31,63));
        TextureImagePadding.Result half=TextureImagePadding.pad(source,true,true);
        check(half.width==30&&half.height==30&&half.image.getWidth()==32&&half.usedU==.9375f&&half.usedV==.9375f);
        check(half.image.getRGB(0,0)==source.getRGB(2,32));check(half.image.getRGB(31,31)==source.getRGB(31,61));
        TextureImagePadding.Result cropped=TextureImagePadding.pad(source,true,false);
        check(cropped.width==30&&cropped.height==60&&cropped.image.getWidth()==64);
        check(cropped.image.getRGB(0,0)==source.getRGB(2,2)&&cropped.image.getRGB(63,63)==source.getRGB(31,61));
        boolean rejected=false;try{TextureImagePadding.pad(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),false,true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
        System.out.println("TextureImagePaddingTest PASS "+assertions+" crop, half-face, rectangular aspect and repeated-edge checks");
    }
}
