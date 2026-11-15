package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/** At most one main-thread chunk snapshot per poll; region writes/flush remain asynchronous. No transfer authority. */
public final class NativeTransferCheckpoint {
    private final List<ChunkPos> chunks;
    private final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private int cursor;private CompletableFuture<Void> flush;private boolean startVerified,complete;
    public NativeTransferCheckpoint(Collection<BlockPos> positions){
        if(positions.isEmpty()||positions.size()>65536)throw new IllegalArgumentException("Transfer checkpoint cell bound");
        var unique=new LinkedHashSet<ChunkPos>();for(var pos:positions)unique.add(new ChunkPos(pos));if(unique.size()>256)throw new IllegalArgumentException("Transfer checkpoint chunk bound");chunks=List.copyOf(unique);
    }
    public List<ChunkPos> chunks(){return chunks;}
    public boolean poll(ServerLevel world,BooleanSupplier unchanged){
        if(!world.getServer().isSameThread())throw new IllegalStateException("Transfer checkpoint requires its server thread");
        if(complete)return true;
        if(world.noSave)return false;for(var pos:chunks)if(world.getChunkSource().getChunkNow(pos.x,pos.z)==null)return false;
        if(!startVerified){if(!unchanged.getAsBoolean())throw new IllegalStateException("Transfer ownership changed before chunk checkpoint");startVerified=true;}
        if(cursor<chunks.size()){var pos=chunks.get(cursor);var chunk=world.getChunkSource().getChunkNow(pos.x,pos.z);var saved=ChunkSerializer.write(world,chunk);writes.add(world.getChunkSource().chunkMap.write(pos,saved));cursor++;return false;}
        for(var write:writes){if(!write.isDone())return false;write.join();}
        if(flush==null){flush=((NativeRampChunkStorageAccess)(Object)world.getChunkSource().chunkMap).vandorlabs$rampWorker().synchronize(true);return false;}
        if(!flush.isDone())return false;flush.join();if(!unchanged.getAsBoolean())throw new IllegalStateException("Transfer ownership changed after durable chunk checkpoint");complete=true;return true;
    }
}
