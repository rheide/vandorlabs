package com.vandorlabs.vehicle;

import net.minecraft.world.WorldServer;

/** Used only by the opt-in client suite to advance deferred transfers in synchronous fixtures. */
public final class VehicleTransferRuntimeChecks {
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
        long deadline=System.nanoTime()+15_000_000_000L;
        while(VehicleTransfer.busy(world)&&System.nanoTime()<deadline){VehicleTransfer.tick(world);Thread.sleep(2);}
        if(VehicleTransfer.busy(world))throw new IllegalStateException("Deferred vehicle transfer did not finish.");
    }
    private VehicleTransferRuntimeChecks(){}
}
