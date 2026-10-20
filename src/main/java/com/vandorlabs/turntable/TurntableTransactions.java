package com.vandorlabs.turntable;

import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Journal first, validate and commit on the server, then verify only the affected chunk saves. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class TurntableTransactions {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"Vandor turntable persistence");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final Map<WorldServer,ArrayDeque<Work>> QUEUES=new IdentityHashMap<>();
    private static final ThreadLocal<Work> ACTIVE=new ThreadLocal<>();
    private static final class Waiting extends RuntimeException {Waiting(){super(null,null,false,false);}}
    private static final Waiting WAITING=new Waiting();
    static final class Work {
        final WorldServer world;final TileEntityTurntableController owner;final EntityPlayerMP player;
        final boolean capture;final Runnable complete;final java.util.function.Consumer<Exception> failed;
        final VehicleTransfer.UpdateBatch updates=new VehicleTransfer.UpdateBatch();
        final Map<Entity,Boolean> frozen=new IdentityHashMap<>();
        final Set<TileEntity> tiles=Collections.newSetFromMap(new IdentityHashMap<>());
        EntityTurntable entity;TurntableSnapshot source;NBTTagCompound undo;Path journal;
        Future<?> io;TransferCheckpoint checkpoint;Exception failure;
        boolean prepared,ready,mutated,committed,retiring,aborting,verified,rollingBack;
        Work(WorldServer w,TileEntityTurntableController owner,EntityTurntable entity,EntityPlayerMP player,boolean capture,Runnable complete,java.util.function.Consumer<Exception> failed){world=w;this.owner=owner;this.entity=entity;this.player=player;this.capture=capture;this.complete=complete;this.failed=failed;}
    }
    private TurntableTransactions(){}
    public static boolean busy(TileEntityTurntableController owner){if(!(owner.getWorld() instanceof WorldServer))return false;ArrayDeque<Work> queue=QUEUES.get(owner.getWorld());if(queue!=null)for(Work work:queue)if(work.owner==owner)return true;return false;}
    static boolean active(EntityTurntable entity){Work current=ACTIVE.get();return current!=null&&current.entity==entity;}
    static boolean queued(WorldServer world,UUID id){ArrayDeque<Work> queue=QUEUES.get(world);if(queue!=null)for(Work work:queue)if(work.entity!=null&&work.entity.getUniqueID().equals(id))return true;return false;}
    static Work current(){return ACTIVE.get();}
    public static void submit(TileEntityTurntableController owner,EntityTurntable entity,EntityPlayerMP player,boolean capture,Runnable complete,java.util.function.Consumer<Exception> failed)throws IOException {
        if(busy(owner))throw new IOException("A turntable transfer is already in progress");
        if(entity!=null&&TurntableTransfer.pending(entity))throw new IOException("A previous transfer requires recovery");
        WorldServer world=(WorldServer)owner.getWorld();ArrayDeque<Work> queue=QUEUES.computeIfAbsent(world,w->new ArrayDeque<>());
        if(queue.size()>=16)throw new IOException("Turntable transfer queue is full; try again");
        queue.add(new Work(world,owner,entity,player,capture,complete,failed));owner.status(capture?"Preparing capture":"Preparing restore");
    }
    private static void freeze(Work work,Entity entity){if(entity instanceof EntityTurntable){((EntityTurntable)entity).pauseForTransfer();return;}if(entity!=null&&!work.frozen.containsKey(entity)){work.frozen.put(entity,entity.updateBlocked);entity.updateBlocked=true;}}
    /** Temporary capture pauses must never become permanent in an ordinary chunk save. */
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void saving(net.minecraftforge.event.world.ChunkDataEvent.Save event){
        ArrayDeque<Work> queue=QUEUES.get(event.getWorld());if(queue==null)return;
        Map<UUID,Boolean> flags=new HashMap<>();for(Work work:queue)for(Map.Entry<Entity,Boolean> entry:work.frozen.entrySet())flags.put(entry.getKey().getUniqueID(),entry.getValue());
        restoreSavedFlags(event.getData().getCompoundTag("Level").getTagList("Entities",10),flags);
    }
    private static void restoreSavedFlags(net.minecraft.nbt.NBTTagList entities,Map<UUID,Boolean> flags){
        for(int i=0;i<entities.tagCount();i++){NBTTagCompound entity=entities.getCompoundTagAt(i);if(entity.hasUniqueId("UUID")){Boolean original=flags.get(entity.getUniqueId("UUID"));if(original!=null)entity.setBoolean("UpdateBlocked",original);}restoreSavedFlags(entity.getTagList("Passengers",10),flags);}
    }
    private static void prepare(Work work)throws Exception {
        if(work.world.disableLevelSaving)throw new IOException("World saving is disabled");
        if(!(work.world.getChunkProvider().chunkLoader instanceof net.minecraft.world.chunk.storage.AnvilChunkLoader))throw new IOException("Chunk loader does not support deferred turntable transfers");
        if(work.owner.isInvalid()||work.world.getTileEntity(work.owner.getPos())!=work.owner)throw new IOException("Turntable controller changed before transfer");
        if(work.capture){
            work.source=work.owner.prepareCapture(work.player);
            for(VehicleStructure.Cell cell:work.source.blocks.cells){TileEntity tile=work.world.getTileEntity(work.source.origin.add(cell.pos));if(tile instanceof net.minecraft.util.ITickable)work.tiles.add(tile);}
            work.world.tickableTileEntities.removeIf(work.tiles::contains);
            for(TurntableSnapshot.FrozenEntity entity:work.source.entities)freeze(work,work.world.getEntityFromUuid(entity.id));
        }else{
            if(work.entity==null||work.entity.isDead||work.owner.display()!=work.entity)throw new IOException("Captured contents are no longer available");
            work.source=work.entity.snapshot;freeze(work,work.entity);
        }
        work.prepared=true;
    }
    static void journal(Path file,NBTTagCompound tag)throws Exception {
        Work work=ACTIVE.get();if(work==null){TurntableTransfer.save(file,tag);return;}
        if(work.undo==null){work.undo=tag.copy();work.journal=file;}
        if(!work.ready)throw WAITING;
    }
    static NBTTagCompound original(Path file)throws IOException {Work work=ACTIVE.get();return work!=null?work.undo:TurntableTransfer.read(file);}
    static void mutated(){Work work=ACTIVE.get();if(work!=null)work.mutated=true;}
    static void retiring(Path file)throws IOException {Work work=ACTIVE.get();if(work==null)Files.delete(file);else work.committed=true;}
    static void rollback(){Work work=ACTIVE.get();if(work!=null){work.updates.clear();work.rollingBack=true;}}
    private static void validate(Work work)throws Exception {
        if(work.owner.isInvalid()||work.world.getTileEntity(work.owner.getPos())!=work.owner)throw new IOException("Turntable controller changed before transfer");
        if(!work.capture)return;
        // Temporarily expose the original update flags to NBT writers, without advancing any entity.
        for(Map.Entry<Entity,Boolean> held:work.frozen.entrySet())held.getKey().updateBlocked=held.getValue();
        try{TurntableSnapshot current=TurntableSnapshot.capture(work.world,work.source.layout,work.source.height,work.player);
            if(!current.write().equals(work.source.write()))throw new IOException("Turntable contents changed while preparing; scan again");
        }finally{for(Entity held:work.frozen.keySet())held.updateBlocked=true;}
    }
    private static void chunks(WorldServer world,TurntableSnapshot snapshot,Set<Chunk> chunks){
        if(snapshot==null)return;
        for(VehicleStructure.Cell cell:snapshot.blocks.cells)chunks.add(world.getChunkFromBlockCoords(snapshot.origin.add(cell.pos)));
        for(BlockPos cell:snapshot.layout.cells)chunks.add(world.getChunkFromBlockCoords(cell));
        for(TurntableSnapshot.FrozenEntity entity:snapshot.entities){net.minecraft.nbt.NBTTagList pos=entity.data().getTagList("Pos",6);chunks.add(world.getChunkFromBlockCoords(new BlockPos(pos.getDoubleAt(0),pos.getDoubleAt(1),pos.getDoubleAt(2))));}
    }
    public static void tick(WorldServer world){
        ArrayDeque<Work> queue=QUEUES.get(world);if(queue==null||queue.isEmpty())return;Work work=queue.peek();
        if(!TransferCoordinator.acquire(world,work))return;
        try{
            if(work.io!=null){if(!work.io.isDone())return;work.io.get();work.io=null;if(work.retiring||work.aborting){finish(queue,work);return;}work.ready=true;}
            if(work.aborting){try{work.io=IO.submit(()->{Files.deleteIfExists(work.journal);return null;});}catch(RejectedExecutionException busy){}return;}
            if(work.committed){
                if(!work.updates.step())return;
                if(work.checkpoint==null){Set<Chunk> affected=new LinkedHashSet<>();chunks(world,work.source,affected);if(work.undo.hasKey("Return",10))chunks(world,TurntableSnapshot.read(work.undo.getCompoundTag("Return")),affected);affected.add(world.getChunkFromBlockCoords(work.owner.getPos()));affected.add(world.getChunkFromBlockCoords(work.entity.getPosition()));work.checkpoint=new TransferCheckpoint(world,affected);}
                if(!work.verified){if(!work.checkpoint.step())return;work.verified=true;}
                try{work.io=IO.submit(()->{Files.deleteIfExists(work.journal);return null;});work.retiring=true;}catch(RejectedExecutionException busy){}return;
            }
            if(!work.prepared)prepare(work);
            if(work.ready)validate(work);
            ACTIVE.set(work);
            try(AutoCloseable notifications=work.updates.collect()){
                if(work.capture)TurntableTransfer.capture(world,work.owner,work.source,work.player);else TurntableTransfer.restore(world,work.entity,work.player);
                freeze(work,work.entity);
            }finally{ACTIVE.remove();}
        }catch(Waiting waiting){
            try{work.io=IO.submit(()->{TurntableTransfer.save(work.journal,work.undo);return null;});}catch(RejectedExecutionException busy){}
        }catch(Exception failure){
            ACTIVE.remove();work.failure=failure;work.io=null;
            if(work.aborting||work.retiring){finish(queue,work);return;}
            if(work.committed&&work.rollingBack){work.rollingBack=false;freeze(work,work.entity);return;} // Atomic rollback succeeded; checkpoint that restored state.
            if(work.mutated){com.vandorlabs.VandorLabs.logger.error("Turntable checkpoint failed; recovery journal retained",failure);finish(queue,work);return;}
            if(work.journal!=null){work.aborting=true;return;}
            finish(queue,work);
        }
    }
    private static void release(Work work){
        if(work.checkpoint!=null)work.checkpoint.close();TransferCoordinator.release(work.world,work);
        for(Map.Entry<Entity,Boolean> held:work.frozen.entrySet())held.getKey().updateBlocked=held.getValue();
        for(TileEntity tile:work.tiles)if(!tile.isInvalid()&&work.world.getTileEntity(tile.getPos())==tile&&!work.world.tickableTileEntities.contains(tile))work.world.tickableTileEntities.add(tile);
    }
    private static void finish(ArrayDeque<Work> queue,Work work){release(work);queue.remove();try{if(work.failure==null)work.complete.run();else work.failed.accept(work.failure);}catch(Exception callback){com.vandorlabs.VandorLabs.logger.error("Turntable completion callback failed",callback);work.owner.failure(callback.getMessage());}}
    public static void unload(WorldServer world){ArrayDeque<Work> queue=QUEUES.remove(world);if(queue!=null)for(Work work:queue)release(work);}
    public static void drainForChecks(WorldServer world)throws Exception {long deadline=System.nanoTime()+15_000_000_000L;while(QUEUES.containsKey(world)&&!QUEUES.get(world).isEmpty()&&System.nanoTime()<deadline){tick(world);Thread.sleep(2);}if(QUEUES.containsKey(world)&&!QUEUES.get(world).isEmpty())throw new IllegalStateException("Deferred turntable transfer did not finish");}
}
