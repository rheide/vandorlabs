package com.vandorlabs.navigation;

import java.util.*;

/** Bounded square broad-phase index. Partial edge tiles retain exact block-coordinate circle tests. */
public final class NavigationCoverage {
    public static final int TILE=1024,MAX_BYTES=1024*1024;
    private final int[] circles;private final boolean[] fallback;private final Map<Long,Bucket> tiles=new HashMap<>();
    private int retained,hotX,hotZ;private boolean hotValid;private Bucket hot;public long exactTests;public long tileHits;
    private static final class Bucket{boolean full;int size;int[] candidates=new int[2];}
    public NavigationCoverage(int[] circles){
        if(circles.length%3!=0)throw new IllegalArgumentException("Circle triples required");this.circles=circles.clone();fallback=new boolean[circles.length/3];retained=128+circles.length*4+fallback.length;
        for(int i=0;i<fallback.length;i++){
            int x=circles[i*3],z=circles[i*3+1],r=circles[i*3+2];if(r<0||Math.abs((long)x)>NavigationMap.LIMIT||Math.abs((long)z)>NavigationMap.LIMIT||r>10000)throw new IllegalArgumentException("Invalid coverage circle");
            int x0=Math.floorDiv(x-r,TILE),x1=Math.floorDiv(x+r,TILE),z0=Math.floorDiv(z-r,TILE),z1=Math.floorDiv(z+r,TILE);
            boolean limited=false;
            for(int tx=x0;tx<=x1&&!limited;tx++)for(int tz=z0;tz<=z1;tz++){
                long key=key(tx,tz);Bucket b=tiles.get(key);if(b!=null&&b.full)continue;
                if(b==null){if(retained+192>MAX_BYTES){limited=true;break;}b=new Bucket();tiles.put(key,b);retained+=192;}
                int loX=tx*TILE,loZ=tz*TILE,hiX=loX+TILE-1,hiZ=loZ+TILE-1;
                if(NavigationMap.covers(loX,loZ,x,z,r)&&NavigationMap.covers(hiX,loZ,x,z,r)&&NavigationMap.covers(loX,hiZ,x,z,r)&&NavigationMap.covers(hiX,hiZ,x,z,r)){b.full=true;retained-=b.candidates.length*4;b.candidates=new int[0];b.size=0;continue;}
                if(b.size==b.candidates.length){int growth=Math.max(2,b.size);if(retained+growth*4>MAX_BYTES){limited=true;break;}b.candidates=Arrays.copyOf(b.candidates,b.size+growth);retained+=growth*4;}b.candidates[b.size++]=i;
            }
            // Any partially indexed circle is also retained in the exact fallback.
            // Hitting a budget never loses coverage or grants an entire boundary tile.
            fallback[i]=limited;
        }
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private boolean exact(int i,int x,int z){int cx=circles[i*3],cz=circles[i*3+1],r=circles[i*3+2];if(x<(long)cx-r||x>(long)cx+r||z<(long)cz-r||z>(long)cz+r)return false;exactTests++;return NavigationMap.covers(x,z,cx,cz,r);}
    public boolean contains(int x,int z){int tx=Math.floorDiv(x,TILE),tz=Math.floorDiv(z,TILE);Bucket b;if(hotValid&&hotX==tx&&hotZ==tz)b=hot;else{b=tiles.get(key(tx,tz));hotX=tx;hotZ=tz;hot=b;hotValid=true;}if(b!=null){tileHits++;if(b.full)return true;for(int n=0;n<b.size;n++)if(exact(b.candidates[n],x,z))return true;}for(int i=0;i<fallback.length;i++)if(fallback[i]&&exact(i,x,z))return true;return false;}
    public int retainedBytes(){return retained;}
}
