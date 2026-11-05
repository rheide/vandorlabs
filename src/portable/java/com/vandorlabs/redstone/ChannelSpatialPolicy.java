package com.vandorlabs.redstone;

/** Primitive geometry for the existing range index; membership and scan loops stay in adapters. */
public final class ChannelSpatialPolicy {
    private ChannelSpatialPolicy(){}
    /** Finite sphere only. The host handles range zero as unbounded and disabled negative ranges. */
    public static boolean finiteContains(int sourceX,int sourceY,int sourceZ,int targetX,int targetY,int targetZ,int range){
        double x=(double)sourceX-targetX,y=(double)sourceY-targetY,z=(double)sourceZ-targetZ;
        return x*x+y*y+z*z<=(double)range*range;
    }
    public static int tier(int range){return range<=16?0:range<=64?1:2;}
    public static int cellSize(int tier){return tier==0?16:tier==1?64:256;}
    public static long packedCell(int x,int y,int z){return ((long)x&67108863L)<<38|((long)y&4095L)<<26|((long)z&67108863L);}
    public static long key(int blockX,int blockY,int blockZ,int size){return packedCell(Math.floorDiv(blockX,size),Math.floorDiv(blockY,size),Math.floorDiv(blockZ,size));}
}
