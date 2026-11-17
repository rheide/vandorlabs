package com.vandorlabs.appliance;

import java.util.HashMap;
import java.util.Map;

/** Nearest available vanilla map color, cached for the lifetime of one print. */
public final class PrinterPalette {
    private final int[] colors;
    private final Map<Integer,Byte> matches=new HashMap<>();

    public PrinterPalette(int[] colors){
        if(colors.length!=256)throw new IllegalArgumentException("Map palette length");
        this.colors=colors.clone();
    }

    public byte pixel(int rgb){
        Byte cached=matches.get(rgb);
        if(cached!=null)return cached;
        byte index=nearest(colors,rgb);
        if(matches.size()<4096)matches.put(rgb,index);
        return index;
    }

    public static byte nearest(int[] colors,int rgb){
        int best=0,score=Integer.MAX_VALUE;
        for(int i=4;i<colors.length;i++){
            int candidate=colors[i];
            if(candidate==0)continue;
            int red=(rgb>>16&255)-(candidate>>16&255);
            int green=(rgb>>8&255)-(candidate>>8&255);
            int blue=(rgb&255)-(candidate&255);
            int distance=red*red+green*green+blue*blue;
            if(distance<score){score=distance;best=i;}
        }
        return (byte)best;
    }
}
