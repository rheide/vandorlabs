package com.vandorlabs.client;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Optional copied-save diagnostic for side effects during vanilla chunk serialization. */
public final class ChunkTileTrace {
    private static final java.lang.reflect.Field FIELD=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.world.chunk.Chunk.class,"tileEntities","field_150816_i");
    @SubscribeEvent public void load(ChunkEvent.Load event)throws IllegalAccessException {
        if(!event.getWorld().isRemote)FIELD.set(event.getChunk(),new TraceMap(event.getChunk().getTileEntityMap()));
    }
    private static final class TraceMap extends HashMap<BlockPos,TileEntity> {
        TraceMap(Map<BlockPos,TileEntity> original){super(original);}
        private void trace(Object key) {
            Exception trace=new Exception("Chunk tile map changed while serializing: "+key);
            for(StackTraceElement frame:trace.getStackTrace())if(frame.getClassName().endsWith("SPacketChunkData")) {
                com.vandorlabs.VandorLabs.logger.error(trace.getMessage(),trace);break;
            }
        }
        @Override public TileEntity put(BlockPos key,TileEntity value){if(!containsKey(key))trace(key);return super.put(key,value);}
        @Override public TileEntity remove(Object key){if(containsKey(key))trace(key);return super.remove(key);}
        @Override public void clear(){if(!isEmpty())trace("clear");super.clear();}
    }
}
