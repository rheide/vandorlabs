package com.vandorlabs.world;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Random;

/** Independent actual Forge index golden plus bounded extreme-query behavior. */
public final class SpatialBucketsTest {
    public static void main(String[] args)throws Exception{
        Random random=new Random(0x4255434b455453L);MessageDigest digest=MessageDigest.getInstance("SHA-256");for(int i=0;i<10000;i++){
            int kx=random.nextInt(),ky=random.nextInt(),kz=random.nextInt();digest.update(ByteBuffer.allocate(8).putLong(SpatialBuckets.key(kx,ky,kz)).array());
            int x=random.nextInt(60000001)-30000000,y=random.nextInt(512)-128,z=random.nextInt(60000001)-30000000;
            double ax=x+random.nextDouble()*4-2,ay=y+random.nextDouble()*4-2,az=z+random.nextDouble()*4-2;boolean inside=SpatialBuckets.intersectsTile(x,y,z,ax,ay,az,ax+random.nextDouble()*3,ay+random.nextDouble()*3,az+random.nextDouble()*3);digest.update((byte)(inside?1:0));
        }
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!hash.toString().equals("1305a57bc0301e85625e928f16d8a8014c1cf35217c10bf8f34a8b3170df1680"))throw new AssertionError("Released bucket address/overlap changed "+hash);
        if(SpatialBuckets.excessive(0,0,0,127,15,15)||!SpatialBuckets.excessive(0,0,0,128,15,15)||!SpatialBuckets.excessive(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE))throw new AssertionError("Empty bucket traversal must be bounded without arithmetic overflow");
        if(SpatialBuckets.intersectsTile(0,0,0,1,0,0,2,1,1)||!SpatialBuckets.intersectsTile(0,0,0,.5,.5,.5,.5,.5,.5))throw new AssertionError("Released strict contact/zero-width ray policy");
        System.out.println("Spatial bucket contracts PASS10000actualForge cases,strictcontacts,overflow-safe finite fallback");
    }
}
