package com.vandorlabs.world;

/** Released eight-block index addressing and strict owner overlap; worlds remain platform-owned. */
public final class SpatialBuckets {
    public static final int MAX_ENUMERATED_BUCKETS=32768;
    private SpatialBuckets(){}
    public static long key(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)y&0xfffL)<<26|((long)z&0x3ffffffL);}
    /** Division checks prevent overflow from turning enormous queries into unbounded empty loops. */
    public static boolean excessive(int x0,int y0,int z0,int x1,int y1,int z1){
        long x=(long)x1-x0+1,y=(long)y1-y0+1,z=(long)z1-z0+1;
        return x<=0||y<=0||z<=0||x>MAX_ENUMERATED_BUCKETS||y>MAX_ENUMERATED_BUCKETS/x||z>MAX_ENUMERATED_BUCKETS/(x*y);
    }
    public static boolean intersectsTile(int x,int y,int z,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){return x+1>minX&&x<maxX&&y+1>minY&&y<maxY&&z+1>minZ&&z<maxZ;}
}
