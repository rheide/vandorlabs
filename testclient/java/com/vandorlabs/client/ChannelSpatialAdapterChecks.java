package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelSpatialPolicy;
import java.lang.reflect.Method;
import net.minecraft.util.math.BlockPos;

/** Actual legacy private call sites against Minecraft's widening and cell geometry. */
public final class ChannelSpatialAdapterChecks {
    private static Method method(Class<?> owner,String name,Class<?>... args)throws Exception{Method value=owner.getDeclaredMethod(name,args);value.setAccessible(true);return value;}
    public static void main(String[] args)throws Exception{
        Class<?> network=Class.forName("com.vandorlabs.redstone.RedstoneChannels$Network"),index=Class.forName("com.vandorlabs.redstone.RedstoneChannels$Network$Index");
        Method inside=method(network,"inside",BlockPos.class,BlockPos.class,int.class),key=method(index,"key",BlockPos.class,int.class),tier=method(index,"tier",int.class),size=method(index,"size",int.class),packed=method(index,"packed",int.class,int.class,int.class);
        if(!((Boolean)inside.invoke(null,new Object[]{null,null,0})))throw new AssertionError("unbounded range lost coordinate-free short circuit");
        int count=0;int[] coordinates={Integer.MIN_VALUE,-30000000,-257,-256,-65,-64,-17,-16,-1,0,1,15,16,17,63,64,65,255,256,257,30000000,Integer.MAX_VALUE};
        for(int x:coordinates)for(int y:new int[]{-4096,-257,-256,-1,0,255,256,4095})for(int z:coordinates){
            BlockPos source=new BlockPos(x,y,z),target=new BlockPos(z,255-y,x);
            for(int range:new int[]{0,4,8,16,32,64,128,256,Integer.MAX_VALUE}){
                boolean expected=range==0||source.distanceSq(target)<=(double)range*range;
                if(((Boolean)inside.invoke(null,source,target,range))!=expected)throw new AssertionError("legacy sphere comparison mismatch");count++;
            }
            for(int width:new int[]{16,64,256}){
                long expected=(((long)Math.floorDiv(x,width)&67108863L)<<38)|(((long)Math.floorDiv(y,width)&4095L)<<26)|((long)Math.floorDiv(z,width)&67108863L);
                if(((Long)key.invoke(null,source,width))!=expected||ChannelSpatialPolicy.key(x,y,z,width)!=expected)throw new AssertionError("legacy signed cell key mismatch");count++;
            }
        }
        for(int value:new int[]{-1,0,4,8,16,17,32,64,65,128,256})if(((Integer)tier.invoke(null,value))!=ChannelSpatialPolicy.tier(value))throw new AssertionError("tier forwarding");
        for(int value=0;value<3;value++)if(((Integer)size.invoke(null,value))!=ChannelSpatialPolicy.cellSize(value))throw new AssertionError("size forwarding");
        if(((Long)packed.invoke(null,-1,-1,-1))!=-1L)throw new AssertionError("packed forwarding");
        System.out.println("Channel spatial adapters PASS cases="+count+" actual-BlockPos-distance signed-key wrappers unbounded-short-circuit");
    }
}
