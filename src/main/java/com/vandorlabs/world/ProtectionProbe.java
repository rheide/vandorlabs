package com.vandorlabs.world;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/**
 * Break events posted only to ask protection rules whether a moving assembly may take a
 * block. The block is not being broken, so this mod's own break guards ignore them.
 */
public final class ProtectionProbe {
    private static final ThreadLocal<Integer> DEPTH=ThreadLocal.withInitial(()->0);
    private ProtectionProbe(){}
    /** True while a protection probe is being posted on this thread. */
    public static boolean active(){return DEPTH.get()>0;}
    /** Post a protection probe; returns true when a protection rule refused it. */
    public static boolean refused(BlockEvent.BreakEvent event){
        DEPTH.set(DEPTH.get()+1);
        try{return MinecraftForge.EVENT_BUS.post(event);}finally{DEPTH.set(DEPTH.get()-1);}
    }
}
