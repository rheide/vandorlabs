package com.vandorlabs.client;

import com.vandorlabs.blocks.ProgrammableHousingState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Preserve loaded-neighbour filtering and the separate per-face sample. */
public final class HousingLightAdapterChecks {
    private static final class LightWorld extends NonRenderingChecks.MemoryWorld {
        int samples;
        LightWorld(){super(true);chunkLimit=true;}
        @Override public int getCombinedLight(BlockPos pos,int minimum){
            if(!isBlockLoaded(pos))throw new AssertionError("unloaded light read "+pos);
            samples++;return super.getCombinedLight(pos,minimum);
        }
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();LightWorld world=new LightWorld();int cases=0;
        for(int x:new int[]{0,1,14,15})for(int z:new int[]{0,1,14,15}){
            BlockPos pos=new BlockPos(x,100,z);int sky=0,block=0,loaded=0;
            for(EnumFacing side:EnumFacing.values())if(world.isBlockLoaded(pos.offset(side))){
                int sample=world.getCombinedLight(pos.offset(side),0);sky=Math.max(sky,sample>>>16);block=Math.max(block,sample&65535);loaded++;
            }
            world.samples=0;
            if(ProgrammableHousingState.neighborLight(world,pos)!=((sky<<16)|block)||world.samples!=loaded)throw new AssertionError("loaded neighbour sampling "+pos);
            int own=world.getCombinedLight(pos,0);world.samples=0;
            if(ProgrammableHousingState.light(null,world,pos)!=own||world.samples!=1)throw new AssertionError("visible face sample replaced by neighbour maximum "+pos);
            cases++;
        }
        System.out.println("Housing light adapter PASS cases="+cases+" loaded-only neighbours exact-sample-count independent-visible-face");
    }
}
