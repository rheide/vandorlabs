package com.vandorlabs.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;

/** The verified G5 block-entity uniform, read without retaining shader-pack aliases. */
final class GlassShaderMaterial {
    static final int UNAVAILABLE=Integer.MIN_VALUE;
    private MethodHandle lookup;
    GlassShaderMaterial(MethodHandle lookup){this.lookup=lookup;}
    static GlassShaderMaterial detect(){
        MethodHandle lookup=null;
        try{
            String version=(String)Class.forName("Config").getField("VERSION").get(null);
            lookup=find(version,Class.forName("net.optifine.shaders.Shaders"));
        }catch(ReflectiveOperationException|LinkageError|SecurityException unavailable){/* Keep state-separated batches. */}
        return new GlassShaderMaterial(lookup);
    }
    static MethodHandle find(String version,Class<?> shaders){
        // In G5 nextBlockEntity changes only ProgramBlock and this material uniform.
        // Unknown versions retain the existing full-state boundary.
        if(!"OptiFine_1.12.2_HD_U_G5".equals(version))return null;
        try{
            Method method=shaders.getDeclaredMethod("getBlockEntityId",TileEntity.class);
            if(method.getReturnType()!=int.class||!Modifier.isStatic(method.getModifiers()))return null;
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(int.class,TileEntity.class));
        }catch(ReflectiveOperationException|LinkageError|SecurityException unavailable){return null;}
    }
    int key(TileEntity tile){
        MethodHandle method=lookup;if(method==null)return UNAVAILABLE;
        try{return (int)method.invokeExact(tile);}
        catch(Throwable failure){
            if(failure instanceof VirtualMachineError)throw (VirtualMachineError)failure;
            if(failure instanceof ThreadDeath)throw (ThreadDeath)failure;
            // One failed access retires the optional hook, including for later batches.
            lookup=null;
            if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Glass shader material lookup unavailable; retaining state-separated batches",failure);
            return UNAVAILABLE;
        }
    }
    static boolean split(IBlockState before,int beforeKey,IBlockState after,int afterKey){
        if(beforeKey==UNAVAILABLE||afterKey==UNAVAILABLE)return beforeKey!=afterKey||before!=after;
        return beforeKey!=afterKey;
    }
}
