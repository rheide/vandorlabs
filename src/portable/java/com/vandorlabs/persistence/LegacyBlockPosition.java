package com.vandorlabs.persistence;

/** Released 1.12 position layout: X26/Y12/Z26. Modern BlockPos uses a different bit order. */
public final class LegacyBlockPosition {
    private LegacyBlockPosition(){}
    public static long pack(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)y&0xfffL)<<26|((long)z&0x3ffffffL);}
    public static int x(long value){return (int)(value>>38);}
    public static int y(long value){return (int)(value<<26>>52);}
    public static int z(long value){return (int)(value<<38>>38);}
}
