package com.vandorlabs.vehicle;

import java.util.concurrent.*;

/** Collision workers receive copied coordinates, never a World, tile or entity. */
final class VehiclePreparation {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(16),task->{Thread t=new Thread(task,"Vandor transfer geometry");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final VehicleCollision.DeferredRaw captured;
    private Future<VehicleCollision.Geometry> future;
    VehiclePreparation(VehicleWorld view){
        captured=VehicleCollision.defer(view,VehicleCollisionCache.MAX_RAW_BOXES);
        if(captured==null)throw new IllegalArgumentException("Craft geometry cannot be prepared within the transfer budget.");
    }
    VehicleCollision poll() throws Exception {
        if(future==null){try{future=WORKER.submit(()->VehicleCollision.prepare(captured,VehicleCollisionCache.MAX_RAW_BOXES));}catch(RejectedExecutionException busy){return null;}}
        if(!future.isDone())return null;
        return new VehicleCollision(future.get());
    }
    void cancel(){if(future!=null)future.cancel(false);}
}
