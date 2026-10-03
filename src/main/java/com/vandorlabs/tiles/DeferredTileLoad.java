package com.vandorlabs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Runs tile load callbacks after Chunk.onLoad finishes iterating its tile map. */
public final class DeferredTileLoad {
    private static final Map<World, Map<TileEntity, Runnable>> PENDING = new IdentityHashMap<>();

    private DeferredTileLoad() { }

    public static void install() {
        DeferredTileLoad handler = new DeferredTileLoad();
        FMLCommonHandler.instance().bus().register(handler);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(handler);
    }

    public static void schedule(TileEntity tile, Runnable action) {
        World world = tile.getWorld();
        if (world == null || world.isRemote) return;
        synchronized (PENDING) {
            Map<TileEntity, Runnable> actions = PENDING.computeIfAbsent(world,
                    unused -> new IdentityHashMap<>());
            Runnable previous = actions.get(tile);
            actions.put(tile, previous == null ? action : () -> {
                previous.run();
                action.run();
            });
        }
    }

    @SubscribeEvent public void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        flush(event.world);
    }

    /** Same settlement boundary used by load lifecycle runtime checks. */
    public static void flush(World world) {
        if (world.isRemote) return;
        Map<TileEntity, Runnable> ready;
        synchronized (PENDING) { ready = PENDING.remove(world); }
        if (ready == null) return;
        for (Map.Entry<TileEntity, Runnable> entry : ready.entrySet()) {
            TileEntity tile = entry.getKey();
            if (!tile.isInvalid() && tile.getWorld() == world && tile.getPos() != null
                    && world.isBlockLoaded(tile.getPos())
                    && world.getChunkFromBlockCoords(tile.getPos())
                            .getTileEntityMap().get(tile.getPos()) == tile)
                entry.getValue().run();
        }
    }

    @SubscribeEvent public void worldUnload(WorldEvent.Unload event) {
        synchronized (PENDING) { PENDING.remove(event.getWorld()); }
    }
}
