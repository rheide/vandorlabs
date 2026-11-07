package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Installed-thread local preparation; pairing and reservation transactions are separate stages. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCanopyLifecycle {
    private NativeCanopyLifecycle(){}
    private static final Map<Level,NativeCanopyGeometry.Cache> PHYSICAL=Collections.synchronizedMap(new WeakHashMap<>());
    public static NativeCanopyEntity at(Level level,BlockPos pos){
        if(level==null)return null;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Canopy preparation requires owning thread");
        var chunk=level instanceof ServerLevel server?server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4):level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if(!(chunk instanceof LevelChunk full))return null;var entity=full.getBlockEntities().get(pos);return entity instanceof NativeCanopyEntity host&&!host.isRemoved()?host:null;
    }
    public static boolean installed(NativeCanopyEntity host){return at(host.getLevel(),host.getBlockPos())==host;}
    /** Called after chunk installation, never from onLoad or a render/shape query. */
    public static void prepare(NativeCanopyEntity host){
        if(!host.pending()||!installed(host))return;var previous=host.publication();
        if(!host.available()||!previous.draws()){host.prepared(new NativeCanopyEntity.Publication(previous.visual(),null,previous.progress(),previous.targetOpen(),previous.waiting(),previous.draws(),true,host.inert()));return;}
        var pos=host.getBlockPos();var cache=PHYSICAL.computeIfAbsent(host.getLevel(),unused->new NativeCanopyGeometry.Cache(16*1024*1024));
        var geometry=cache.prepare(previous.visual(),previous.progress(),pos.getX(),pos.getY(),pos.getZ());
        host.prepared(new NativeCanopyEntity.Publication(previous.visual(),geometry,previous.progress(),previous.targetOpen(),previous.waiting(),true,true,false));
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){var cache=PHYSICAL.remove(event.getLevel());if(cache!=null)cache.clear();}
}
