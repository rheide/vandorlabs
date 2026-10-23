package com.vandorlabs.client;

import java.lang.reflect.Field;

/** Optional shader pass state; shadow draws do not satisfy main-view visibility. */
final class VehicleRenderPass {
    private static final Field SHADOW=shadowField();
    private static boolean warned;
    private static Field shadowField(){
        try{return Class.forName("net.optifine.shaders.Shaders",false,VehicleRenderPass.class.getClassLoader()).getField("isShadowPass");}
        catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException|LinkageError incompatible){com.vandorlabs.VandorLabs.logger.warn("Optional shader pass detection unavailable",incompatible);return null;}
    }
    static boolean shadow(){
        if(SHADOW==null)return false;
        try{return SHADOW.getBoolean(null);}
        catch(ReflectiveOperationException|LinkageError unavailable){if(!warned){warned=true;com.vandorlabs.VandorLabs.logger.warn("Cannot read optional shader pass state",unavailable);}return false;}
    }
    private VehicleRenderPass(){}
}
