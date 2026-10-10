package com.vandorlabs.vehicle;

import net.minecraft.block.Block;
import java.util.*;

/** Bounded diagnostics for optional mod presentation; never modifies stored block data. */
public final class VehicleCompatibility {
    private static final Set<String> reported=Collections.synchronizedSet(new HashSet<>());
    private VehicleCompatibility(){}
    public static void warn(Block block,String operation,Throwable error) {
        String key=block.getRegistryName()+":"+operation;
        if(reported.size()<256 && reported.add(key))
            com.vandorlabs.VandorLabs.logger.warn("Vehicle uses a best-effort {} for {}; parked data is unchanged",operation,block.getRegistryName(),error);
    }
}
