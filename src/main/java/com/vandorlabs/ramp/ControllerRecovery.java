package com.vandorlabs.ramp;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityControlledRamp;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.ArrayList;

/** Chunk-load notifications wake orphan checks; there is no background scanner. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class ControllerRecovery {
    private static final Set<TileEntityControlledRamp> LOADED=Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private ControllerRecovery() { }
    public static void register(TileEntityControlledRamp part) { LOADED.add(part); }
    public static void unregister(TileEntityControlledRamp part) {
        if (part.getWorld()!=null && !part.getWorld().isRemote) LOADED.remove(part);
    }
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        World world=event.getWorld();
        if (world.isRemote) return;
        for (TileEntityControlledRamp part:new ArrayList<>(LOADED)) {
            if (part.getWorld()==world && !part.isInvalid()
                    && (part.controller.getX()>>4)==event.getChunk().x
                    && (part.controller.getZ()>>4)==event.getChunk().z)
                world.scheduleUpdate(part.getPos(),world.getBlockState(part.getPos()).getBlock(),1);
        }
    }
}
