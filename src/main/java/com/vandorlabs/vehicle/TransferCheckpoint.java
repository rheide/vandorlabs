package com.vandorlabs.vehicle;

import com.vandorlabs.VandorLabs;
import net.minecraft.nbt.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.*;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A transfer retires only after its affected chunks can be read back from region storage. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class TransferCheckpoint implements AutoCloseable {
    private static final String KEY="VandorTransferCheckpoints";
    private static final Map<WorldServer,Map<ChunkPos,Set<String>>> WATCHES=new IdentityHashMap<>();
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"Vandor checkpoint verification");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final WorldServer world;
    private final File directory;
    private final String token=UUID.randomUUID().toString();
    private final Map<ChunkPos,Chunk> chunks=new LinkedHashMap<>();
    private final ArrayDeque<Chunk> saves=new ArrayDeque<>();
    private Set<ChunkPos> missing;
    private Future<Set<ChunkPos>> verification;
    private int polls;
    private boolean closed;

    public TransferCheckpoint(WorldServer world,Collection<Chunk> affected)throws IOException {
        if(!(world.getChunkProvider().chunkLoader instanceof AnvilChunkLoader))throw new IOException("Chunk loader does not support verified transfer checkpoints");
        this.world=world;directory=((AnvilChunkLoader)world.getChunkProvider().chunkLoader).chunkSaveLocation;
        Map<ChunkPos,Set<String>> watches=WATCHES.computeIfAbsent(world,w->new HashMap<>());
        for(Chunk chunk:affected){ChunkPos at=chunk.getPos();chunks.put(at,chunk);watches.computeIfAbsent(at,p->new HashSet<>()).add(token);}
        saves.addAll(chunks.values());missing=new HashSet<>(chunks.keySet());
    }
    @SubscribeEvent public static void saving(ChunkDataEvent.Save event){
        if(!(event.getWorld() instanceof WorldServer))return;
        Map<ChunkPos,Set<String>> watches=WATCHES.get(event.getWorld());
        Set<String> tokens=watches==null?null:watches.get(event.getChunk().getPos());
        if(tokens==null)return;
        NBTTagCompound markers=new NBTTagCompound();for(String token:tokens)markers.setBoolean(token,true);
        event.getData().setTag(KEY,markers);
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){WATCHES.remove(event.getWorld());}

    /** Main-thread serialization is limited to one affected chunk per call. No disk wait occurs here. */
    public boolean step()throws Exception {
        if(closed)throw new IllegalStateException("Closed transfer checkpoint");
        if(world.disableLevelSaving)throw new IOException("World saving is disabled; transfer recovery record retained");
        if(!saves.isEmpty()){
            Chunk chunk=saves.remove();
            if(world.getChunkProvider().getLoadedChunk(chunk.x,chunk.z)!=chunk)throw new IOException("Transfer chunk unloaded before checkpoint; recovery record retained");
            world.getChunkProvider().chunkLoader.saveChunk(world,chunk);return false;
        }
        if(verification!=null){
            if(!verification.isDone())return false;
            missing=verification.get();verification=null;
            if(missing.isEmpty()){close();return true;}
            // Anvil may drop an enqueue while that same chunk is already being written.
            // Retry using current live data; never drain or race the vanilla writer ourselves.
            if(++polls%20==0)for(ChunkPos at:missing)saves.add(chunks.get(at));
            if(polls>=600)throw new IOException("Transfer chunks could not be verified on disk; recovery record retained");
        }
        if(verification==null&&saves.isEmpty()){
            final Set<ChunkPos> pending=new HashSet<>(missing);
            try{verification=IO.submit(()->verify(directory,token,pending));}catch(RejectedExecutionException busy){/* Bounded backpressure; retry next tick. */}
        }
        return false;
    }
    private static Set<ChunkPos> verify(File directory,String token,Set<ChunkPos> pending)throws IOException {
        Set<ChunkPos> missing=new HashSet<>();
        for(ChunkPos at:pending)try(DataInputStream input=RegionFileCache.getChunkInputStream(directory,at.x,at.z)){
            if(input==null||!CompressedStreamTools.read(input,new NBTSizeTracker(128L*1024*1024)).getCompoundTag(KEY).getBoolean(token))missing.add(at);
        }
        return missing;
    }
    @Override public void close(){
        if(closed)return;closed=true;
        Map<ChunkPos,Set<String>> watches=WATCHES.get(world);if(watches==null)return;
        for(ChunkPos at:chunks.keySet()){Set<String> tokens=watches.get(at);if(tokens!=null){tokens.remove(token);if(tokens.isEmpty())watches.remove(at);}}
        if(watches.isEmpty())WATCHES.remove(world);
    }
}
