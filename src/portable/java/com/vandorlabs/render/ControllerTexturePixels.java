package com.vandorlabs.render;

/** Solid controller art must not punch holes through the opaque cube. */
public final class ControllerTexturePixels {
    private ControllerTexturePixels() { }
    public static void seal(int[] pixels,int width,int height) {
        if (pixels.length != width*height) throw new IllegalArgumentException("Pixel dimensions");
        int[] queue=new int[pixels.length];boolean[] filled=new boolean[pixels.length];
        int read=0,write=0;
        for (int i=0;i<pixels.length;i++) if ((pixels[i]>>>24)>=128) {
            filled[i]=true;queue[write++]=i;
        }
        if (write==0) { for (int i=0;i<pixels.length;i++) pixels[i]|=0xFF000000; return; }
        while (read<write) {
            int at=queue[read++],x=at%width,y=at/width;
            for (int direction=0;direction<4;direction++) {
                int next=direction==0?at-1:direction==1?at+1:direction==2?at-width:at+width;
                if (direction==0 && x==0 || direction==1 && x==width-1
                        || direction==2 && y==0 || direction==3 && y==height-1 || filled[next]) continue;
                filled[next]=true;pixels[next]=pixels[at];queue[write++]=next;
            }
        }
        for (int i=0;i<pixels.length;i++) pixels[i]|=0xFF000000;
    }
}
