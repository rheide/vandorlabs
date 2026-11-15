package com.vandorlabs.neoforge.surface;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Local onLoad only enqueues identities. World reads occur after chunk loading, once per dirty component. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeSurfaceUpdates {
    private static final Map<ServerLevel,LinkedHashSet<NativeSurfaceEntity>> PENDING=new IdentityHashMap<>();
    public static void queue(NativeSurfaceEntity tile){if(tile.getLevel() instanceof ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Surface update thread");PENDING.computeIfAbsent(level,unused->new LinkedHashSet<>()).add(tile);}}
    public static void settled(NativeSurfaceEntity tile){if(tile.getLevel() instanceof ServerLevel level){var pending=PENDING.get(level);if(pending!=null)pending.remove(tile);}}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel level))return;var pending=PENDING.get(level);if(pending==null)return;for(int count=0;count<32&&!pending.isEmpty();count++){var tile=pending.iterator().next();pending.remove(tile);if(!tile.isRemoved()&&level.hasChunkAt(tile.getBlockPos())&&level.getBlockEntity(tile.getBlockPos())==tile)tile.refreshGroup();}if(pending.isEmpty())PENDING.remove(level);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)PENDING.remove(level);}
    private NativeSurfaceUpdates(){}
}
