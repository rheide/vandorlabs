package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableTransactions;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import java.io.IOException;
import java.util.concurrent.*;

/** Drive the production queue in synchronous fixtures and inject slow journal I/O. */
final class TurntableTransactionsChecks {
    static void scan(TileEntityTurntableController tile,EntityPlayerMP player)throws Exception{tile.requestScan(player);finish(tile);}
    static void restore(TileEntityTurntableController tile,EntityPlayerMP player)throws Exception{tile.requestRestore(player);finish(tile);}
    private static void finish(TileEntityTurntableController tile)throws Exception{TurntableTransactions.drainForChecks((WorldServer)tile.getWorld());if(!tile.lastError.isEmpty())throw new IOException(tile.lastError);}
    static void slowDisk(TileEntityTurntableController tile,EntityPlayerMP player,TileEntityChest chest)throws Exception{
        WorldServer world=(WorldServer)tile.getWorld();
        java.lang.reflect.Field field=TurntableTransactions.class.getDeclaredField("IO");field.setAccessible(true);
        ExecutorService io=(ExecutorService)field.get(null);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> blocker=io.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        int original=chest.getStackInSlot(0).getCount();
        try{
            if(!entered.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Turntable persistence worker unavailable");
            tile.requestScan(player);TurntableTransactions.tick(world);
            net.minecraft.nbt.NBTTagList savedEntities=new net.minecraft.nbt.NBTTagList();
            for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(entity.updateBlocked)savedEntities.appendTag(entity.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            net.minecraft.nbt.NBTTagCompound savedChunk=new net.minecraft.nbt.NBTTagCompound(),level=new net.minecraft.nbt.NBTTagCompound();level.setTag("Entities",savedEntities);savedChunk.setTag("Level",level);
            TurntableTransactions.saving(new net.minecraftforge.event.world.ChunkDataEvent.Save(world.getChunkFromBlockCoords(chest.getPos()),savedChunk));
            if(savedEntities.tagCount()==0)throw new IllegalStateException("Missing frozen entity fixture");
            for(int i=0;i<savedEntities.tagCount();i++)if(savedEntities.getCompoundTagAt(i).getBoolean("UpdateBlocked"))throw new IllegalStateException("Temporary transfer pause escaped into a chunk save");
            for(int i=0;i<40;i++){
                long start=System.nanoTime();TurntableTransactions.tick(world);
                if(System.nanoTime()-start>50_000_000L)throw new IllegalStateException("Turntable journal blocked the server tick");
                if(tile.captureId!=null||world.getTileEntity(chest.getPos())!=chest||chest.getStackInSlot(0).getCount()!=original)throw new IllegalStateException("Turntable changed before durable preparation");
            }
            chest.getStackInSlot(0).grow(1);chest.markDirty();release.countDown();blocker.get();TurntableTransactions.drainForChecks(world);
            if(tile.captureId!=null||chest.getStackInSlot(0).getCount()!=original+1||!tile.lastError.contains("changed while preparing"))throw new IllegalStateException("Concurrent inventory edit was not preserved and rejected");
        }finally{release.countDown();blocker.get();}
        chest.getStackInSlot(0).setCount(original);chest.markDirty();tile.clearError();scan(tile,player);
        System.out.println("[vandorlabs][reprolab] turntable-transfer-backpressure PASS blocked-disk ticks=40 source-intact changed-inventory-abort retry");
    }
}
