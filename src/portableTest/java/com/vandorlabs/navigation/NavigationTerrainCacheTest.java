package com.vandorlabs.navigation;

public final class NavigationTerrainCacheTest {
    private static int assertions;
    private static void need(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        NavigationTerrainCache cache=new NavigationTerrainCache(32*NavigationTerrainCache.TILE_BYTES);Object overworld=new Object(),nether=new Object();NavigationTerrainCache.Sampler colors=(x,z)->0xff000000|(x*31+z)&0xffffff;
        for(int x=-64;x<64;x++)for(int z=-32;z<32;z++)cache.sample(overworld,x,z,1,colors);
        long generated=cache.samples;
        // Panning far away and back must not recompute retained squares, even via another display.
        for(int x=200;x<216;x++)for(int z=100;z<116;z++)cache.sample(overworld,x,z,1,colors);
        long away=cache.samples;for(int x=-64;x<64;x++)for(int z=-32;z<32;z++)need(cache.sample(overworld,x,z,1,(a,b)->{throw new AssertionError("cached square regenerated");})==colors.sample(x,z),"immutable sample parity");
        need(cache.samples==away&&generated==8192,"pan back and another table reuse geographic cache");
        long before=cache.samples;cache.sample(nether,0,0,1,colors);cache.sample(overworld,0,0,4,colors);need(cache.samples==before+2,"dimension/generator and detail level isolation");
        int coarse=cache.sample(overworld,3,3,4,colors);need(coarse==cache.sample(overworld,0,0,4,colors),"aligned coarse squares");need(cache.sample(overworld,3,3,1,colors)!=cache.sample(overworld,0,0,1,colors),"finest squares retain block boundaries");
        cache.invalidate(overworld);before=cache.samples;cache.sample(overworld,0,0,1,colors);need(cache.samples==before+1,"changed generator invalidates its squares");need(cache.sample(nether,0,0,1,(a,b)->{throw new AssertionError("other dimension invalidated");})==colors.sample(0,0),"other dimension remains cached");
        Object error=new Object();need(cache.sample(error,0,0,1,(a,b)->NavigationMap.ERROR)==NavigationMap.ERROR,"failed samples visible");need(cache.sample(error,0,0,1,colors)!=NavigationMap.ERROR,"failed sample retries instead of becoming permanent terrain");
        NavigationTerrainCache small=new NavigationTerrainCache(NavigationTerrainCache.TILE_BYTES);small.sample(overworld,0,0,1,colors);small.sample(overworld,64,64,1,colors);need(small.evictions==1&&small.retainedBytes()<=NavigationTerrainCache.TILE_BYTES,"weighted memory bound");before=small.samples;small.sample(overworld,0,0,1,colors);need(small.samples==before+1,"evicted square can regenerate");small.clear();need(small.retainedBytes()==0,"world/cache release");
        System.out.println("Navigation terrain cache PASS assertions="+assertions+" geographic squares pan-back cross-display reuse dimensions LOD generator invalidation bounded eviction no expiry");
    }
}
