package com.vandorlabs.vehicle;

import net.minecraft.world.WorldServer;
import java.util.IdentityHashMap;
import java.util.Map;

/** Serialize vehicle and turntable commits within each dimension, including checkpoint retirement. */
public final class TransferCoordinator {
    private static final Map<WorldServer,Object> OWNERS=new IdentityHashMap<>();
    private TransferCoordinator(){}
    public static boolean acquire(WorldServer world,Object owner){Object current=OWNERS.get(world);if(current!=null&&current!=owner)return false;OWNERS.put(world,owner);return true;}
    public static void release(WorldServer world,Object owner){if(OWNERS.get(world)==owner)OWNERS.remove(world);}
}
