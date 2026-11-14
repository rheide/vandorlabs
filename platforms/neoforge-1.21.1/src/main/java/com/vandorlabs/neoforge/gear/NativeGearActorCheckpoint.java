package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.mixin.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.*;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/** Asynchronous receipt for the normal native loaded-entity save path. Callers retain ownership until READY. */
public final class NativeGearActorCheckpoint {
    private static final ThreadLocal<Capture> CAPTURE=new ThreadLocal<>();
    private static final class Capture {final SimpleRegionStorage storage;final ChunkPos chunk;CompletableFuture<Void> result;Capture(SimpleRegionStorage storage,ChunkPos chunk){this.storage=storage;this.chunk=chunk;}}
    /** Mixin bridge; outside an explicitly scoped checkpoint the native write is unchanged. */
    public static CompletableFuture<Void> write(SimpleRegionStorage storage,ChunkPos chunk,CompoundTag tag){
        var capture=CAPTURE.get();if(capture!=null&&(capture.storage!=storage||!capture.chunk.equals(chunk)||capture.result!=null))throw new IllegalStateException("Unexpected arm actor checkpoint write");
        if(capture!=null&&tag!=null&&tag.sizeInBytes()>4*1024*1024)throw new IllegalStateException("Arm actor checkpoint byte bound");
        var result=storage.write(chunk,tag);if(capture!=null)capture.result=result;return result;
    }
    private final ServerLevel level;
    private final List<ChunkPos> chunks;
    private final Runnable requireAuthority;
    private final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private int index;private CompletableFuture<Void> durable;
    NativeGearActorCheckpoint(ServerLevel level,Collection<ChunkPos> chunks,Runnable requireAuthority){
        this.level=Objects.requireNonNull(level);this.requireAuthority=Objects.requireNonNull(requireAuthority);this.chunks=List.copyOf(new LinkedHashSet<>(chunks));if(this.chunks.isEmpty()||this.chunks.size()>16)throw new IllegalArgumentException("Arm actor checkpoint chunk bound");
    }
    @SuppressWarnings("unchecked")
    private PersistentEntitySectionManager<Entity> manager(){return (PersistentEntitySectionManager<Entity>)((NativeRampEntityLevelAccess)(Object)level).vandorlabs$rampActorManager();}
    /** Starts native entity reads if needed. Pending work is retried without blocking the server thread. */
    boolean loaded(){
        thread();boolean ready=true;var manager=manager();var access=(NativeGearEntityManagerAccess)(Object)manager;var states=((NativeRampEntityManagerAccess)(Object)manager).vandorlabs$rampActorLoads();
        for(var chunk:chunks)if(!manager.areEntitiesLoaded(chunk.toLong())){ready=false;Object state=states.get(chunk.toLong());if(state instanceof Enum<?> value&&value.name().equals("FRESH"))access.vandorlabs$gearLoad(chunk.toLong());}
        // Startup recovery has no world ticks to drain the native asynchronous inbox.
        if(!ready&&level.getServer().getTickCount()==0)access.vandorlabs$gearInbox();return ready;
    }
    /** Loaded actor sections also contain actors not yet admitted to the visible-world lookup. */
    Entity actor(UUID identity){
        thread();var sections=((NativeRampEntityManagerAccess)(Object)manager()).vandorlabs$rampActorSections();Entity found=null;
        for(var chunk:chunks){var actors=sections.getExistingSectionsInChunk(chunk.toLong()).flatMap(section->section.getEntities()).iterator();while(actors.hasNext()){var actor=actors.next();if(actor.getUUID().equals(identity)){if(found!=null&&found!=actor)throw new IllegalStateException("Duplicate arm escrow actor identity");found=actor;}}}return found;
    }
    boolean poll(){
        thread();requireAuthority.run();if(level.noSave||!loaded())return false;var manager=manager();var storage=((NativeRampEntityManagerAccess)(Object)manager).vandorlabs$rampActorStorage();if(!(storage instanceof EntityStorage entityStorage))throw new IllegalStateException("Unsupported native actor storage");var regions=((NativeRampEntityStorageAccess)(Object)entityStorage).vandorlabs$rampActorRegions();
        if(index<chunks.size()){
            var chunk=chunks.get(index);var capture=new Capture(regions,chunk);if(CAPTURE.get()!=null)throw new IllegalStateException("Nested arm actor checkpoint");CAPTURE.set(capture);
            try{if(!((NativeGearEntityManagerAccess)(Object)manager).vandorlabs$gearStore(chunk.toLong(),entity->{}))return false;}finally{CAPTURE.remove();}
            if(capture.result!=null)writes.add(capture.result);index++;return false;
        }
        for(var write:writes){if(!write.isDone())return false;write.join();}if(durable==null){durable=regions.synchronize(true);return false;}if(!durable.isDone())return false;durable.join();requireAuthority.run();return true;
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("Arm actor checkpoint requires server thread");}
}
