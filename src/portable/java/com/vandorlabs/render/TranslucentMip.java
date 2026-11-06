package com.vandorlabs.render;

/** Existing door-glass mip average; alpha stays linear and RGB is weighted by coverage. */
public final class TranslucentMip {
    private TranslucentMip(){}
    public static int average(int first,int second,int third,int fourth){
        int a=0,r=0,g=0,b=0;
        int alpha=first>>>24;a+=alpha;r+=((first>>16)&255)*alpha;g+=((first>>8)&255)*alpha;b+=(first&255)*alpha;
        alpha=second>>>24;a+=alpha;r+=((second>>16)&255)*alpha;g+=((second>>8)&255)*alpha;b+=(second&255)*alpha;
        alpha=third>>>24;a+=alpha;r+=((third>>16)&255)*alpha;g+=((third>>8)&255)*alpha;b+=(third&255)*alpha;
        alpha=fourth>>>24;a+=alpha;r+=((fourth>>16)&255)*alpha;g+=((fourth>>8)&255)*alpha;b+=(fourth&255)*alpha;
        return a==0?0:(a/4<<24)|(r/a<<16)|(g/a<<8)|b/a;
    }
}
