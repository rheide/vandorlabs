package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.tileentity.TileEntity;
import java.lang.reflect.Method;

/** Optional OptiFine material IDs must be captured per component before merging. */
final class VehicleShaderContext {
    private static final Method PUSH,POP;
    static {
        Method push=null,pop=null;
        try{Class<?> owner=Class.forName("net.optifine.shaders.SVertexBuilder");push=owner.getMethod("pushEntity",net.minecraft.block.state.IBlockState.class,net.minecraft.util.math.BlockPos.class,net.minecraft.world.IBlockAccess.class,BufferBuilder.class);pop=owner.getMethod("popEntity",BufferBuilder.class);}
        catch(ClassNotFoundException absent){}
        catch(ReflectiveOperationException|LinkageError unsupported){com.vandorlabs.VandorLabs.logger.warn("Vehicle shader material capture unavailable; retaining individual wall rendering",unsupported);}
        PUSH=push;POP=pop;
    }
    static boolean available(){return PUSH!=null&&POP!=null;}
    static boolean push(TileEntity tile,BufferBuilder buffer){
        if(!available()||buffer.getVertexFormat().getNextOffset()<=32)return false;
        try{PUSH.invoke(null,tile.getWorld().getBlockState(tile.getPos()),tile.getPos(),tile.getWorld(),buffer);return true;}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot capture vehicle shader material",e);}
    }
    static void pop(BufferBuilder buffer){try{POP.invoke(null,buffer);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot restore vehicle shader material",e);}}
    private VehicleShaderContext(){}
}
