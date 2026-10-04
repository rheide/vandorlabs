package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

final class ScreenTextureLocationChecks {
    static void run() {
        for(boolean framed:new boolean[]{false,true}) {
            ResourceLocation expected=new ResourceLocation(VandorLabs.MODID,"textures/blocks/"
                    +(framed?"sequence_border_off":"screen_off")+".png");
            require(expected.equals(ScreenTextureLocations.off(framed)),"off texture changed");
            require(ScreenTextureLocations.off(framed)==ScreenTextureLocations.off(framed),"off identifier not reused");
        }
        for(int i=0;i<1024;i++) {
            String id="screen_"+i;
            ResourceLocation expected=new ResourceLocation(VandorLabs.MODID,"textures/blocks/"+id+"_static.png");
            ResourceLocation actual=ScreenTextureLocations.stationary(id);
            require(expected.equals(actual),"static texture changed");
            require(actual==ScreenTextureLocations.stationary(new String(id)),"equal screen name not reused");
        }
        try {
            java.lang.reflect.Field field=ScreenTextureLocations.class.getDeclaredField("STATIC");
            field.setAccessible(true);
            require(((java.util.Map<?,?>)field.get(null)).size()==256,"screen name cache exceeded limit");
        } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        require(ScreenTextureLocations.stationary("screen_0").equals(new ResourceLocation(VandorLabs.MODID,
                "textures/blocks/screen_0_static.png")),"eviction changed identifier");
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setWorld(world);
        int cases=0;
        for(boolean limit:new boolean[]{false,true}) {
            world.chunkLimit=limit;
            for(int x:new int[]{-1,0,1,14,15,16})for(int z:new int[]{-1,0,1,14,15,16}) {
                tile.setPos(new BlockPos(x,100,z));int sky=0,block=0;
                for(EnumFacing side:EnumFacing.values()) {
                    BlockPos neighbor=tile.getPos().offset(side);
                    if(!world.isBlockLoaded(neighbor))continue;
                    int combined=world.getCombinedLight(neighbor,0);
                    sky=Math.max(sky,combined>>>16);block=Math.max(block,combined&65535);
                }
                require(TEAnimatedScreenSelector.neighborLight(tile)==((sky<<16)|block),"neighbor lighting changed");
                cases++;
            }
        }
        System.out.println("PASS: bounded screen identifier reuse/eviction and "+cases+" loaded-neighbor lighting comparisons");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
