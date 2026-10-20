package com.vandorlabs.network;

import java.io.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;

/** Only bounded binary NBT parsing runs here; registry resolution and world installation remain on the client. */
public final class SnapshotReader {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),r->{Thread t=new Thread(r,"Vandor snapshot reader");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private SnapshotReader(){}
    public static Future<NBTTagCompound> read(byte[] bytes,int maximum){
        if(bytes.length>maximum)throw new IllegalArgumentException("Oversize snapshot");
        try{return WORKER.submit(()->CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),new NBTSizeTracker(maximum*4L)));}
        catch(RejectedExecutionException busy){return null;}
    }
}
