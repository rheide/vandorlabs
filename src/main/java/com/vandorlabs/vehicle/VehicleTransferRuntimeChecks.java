package com.vandorlabs.vehicle;

import net.minecraft.world.WorldServer;

/** Used only by the opt-in client suite to advance deferred transfers in synchronous fixtures. */
public final class VehicleTransferRuntimeChecks {
    public static final class LostMarker {
        int saves;
        final net.minecraft.world.chunk.Chunk chunk;
        LostMarker(net.minecraft.world.chunk.Chunk chunk){this.chunk=chunk;}
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
        public void save(net.minecraftforge.event.world.ChunkDataEvent.Save event){
            if(event.getChunk()==chunk&&event.getData().hasKey("VandorTransferCheckpoints")){
                if(saves++==0)event.getData().removeTag("VandorTransferCheckpoints");
            }
        }
    }
    public static void checkpointIsolation(WorldServer world)throws Exception{
        net.minecraft.world.chunk.Chunk chunk=world.getChunkFromChunkCoords(0,0);
        java.lang.reflect.Field field=TransferCheckpoint.class.getDeclaredField("IO");field.setAccessible(true);
        java.util.concurrent.ExecutorService io=(java.util.concurrent.ExecutorService)field.get(null);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Future<?> blocker=io.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        LostMarker missing=new LostMarker(chunk);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(missing);
        try(TransferCheckpoint checkpoint=new TransferCheckpoint(world,java.util.Collections.singleton(chunk))){
            if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Checkpoint worker unavailable");
            if(checkpoint.step())throw new IllegalStateException("Checkpoint completed before disk verification");
            for(int i=0;i<40;i++){long start=System.nanoTime();if(checkpoint.step())throw new IllegalStateException("Blocked verifier retired a checkpoint");if(System.nanoTime()-start>50_000_000L)throw new IllegalStateException("Checkpoint verification blocked the server thread");}
            release.countDown();blocker.get();
            long deadline=System.nanoTime()+15_000_000_000L;boolean complete=false;
            while(System.nanoTime()<deadline){if(checkpoint.step()){complete=true;break;}Thread.sleep(2);}
            if(!complete||missing.saves<2)throw new IllegalStateException("Missing durable marker was not retried and verified");
        }finally{release.countDown();blocker.get();net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(missing);}
        System.out.println("[vandorlabs][reprolab] transfer-checkpoint PASS blocked-verifier nonblocking-ticks missing-marker retry affected-chunk-readback");
    }
    public static void slowDisk(WorldServer world,Runnable request,java.util.function.BooleanSupplier unchanged)throws Exception{
        java.lang.reflect.Field field=VehicleTransfer.class.getDeclaredField("IO");field.setAccessible(true);
        java.util.concurrent.ExecutorService io=(java.util.concurrent.ExecutorService)field.get(null);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Future<?> blocker=io.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        long maximum=0;
        try{
            if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Persistence worker unavailable.");
            request.run();long deadline=System.nanoTime()+10_000_000_000L;
            while(!VehicleTransfer.waitingForPersistence(world)&&System.nanoTime()<deadline){VehicleTransfer.tick(world);Thread.sleep(2);}
            if(!VehicleTransfer.waitingForPersistence(world))throw new IllegalStateException("Transfer did not reach the durable preparation gate.");
            for(int i=0;i<40;i++){long start=System.nanoTime();VehicleTransfer.tick(world);maximum=Math.max(maximum,System.nanoTime()-start);if(!unchanged.getAsBoolean())throw new IllegalStateException("Transfer mutated before its journal was durable.");}
            if(maximum>50_000_000L)throw new IllegalStateException("A blocked disk stalled a transfer tick: "+maximum);
        }finally{release.countDown();blocker.get();}
        drain(world);
        System.out.println("[vandorlabs][reprolab] vehicle-transfer-backpressure PASS blocked-disk ticks=40 max-us="+maximum/1000+" original-state-intact deferred-commit");
    }
    public static void drain(WorldServer world)throws Exception{
        // Advance persistence at the normal 20 Hz server cadence. Fast polling
        // would exhaust checkpoint retry counts before vanilla flushes its queue.
        long deadline=System.nanoTime()+45_000_000_000L;
        while(VehicleTransfer.busy(world)&&System.nanoTime()<deadline){VehicleTransfer.tick(world);Thread.sleep(50);}
        if(VehicleTransfer.busy(world))throw new IllegalStateException("Deferred vehicle transfer did not finish.");
    }
    private VehicleTransferRuntimeChecks(){}
}
