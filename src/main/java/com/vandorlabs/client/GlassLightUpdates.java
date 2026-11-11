package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.util.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Client chunk-tag reads must finish loading before native light propagation. */
public final class GlassLightUpdates {
    private static final Set<TileEntityProgrammableGlass> PENDING=Collections.newSetFromMap(new WeakHashMap<>());
    public static void install(){GlassLightUpdates events=new GlassLightUpdates();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(events);net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(events);}
    public static synchronized void schedule(TileEntityProgrammableGlass tile){PENDING.add(tile);}
    static void drain(){
        List<TileEntityProgrammableGlass> ready;
        synchronized(GlassLightUpdates.class){if(PENDING.isEmpty())return;ready=new ArrayList<>(PENDING);PENDING.clear();}
        for(TileEntityProgrammableGlass tile:ready){
            World world=tile.getWorld();
            if(!tile.isInvalid()&&world!=null&&world.isRemote&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile)tile.refreshPendingLight();
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)drain();}
    @SubscribeEvent public void unload(WorldEvent.Unload event){synchronized(GlassLightUpdates.class){PENDING.removeIf(tile->tile.getWorld()==event.getWorld());}}
    private GlassLightUpdates(){}
}
