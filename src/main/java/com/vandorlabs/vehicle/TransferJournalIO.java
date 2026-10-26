package com.vandorlabs.vehicle;

import java.util.concurrent.*;

/** Bounded disk worker for already-copied transaction records. Never inspect a world here. */
public final class TransferJournalIO {
    @FunctionalInterface public interface Write {void run() throws Exception;}
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(32),r->{Thread thread=new Thread(r,"Vandor transaction journals");thread.setDaemon(true);return thread;},
            new ThreadPoolExecutor.AbortPolicy());
    public static Future<?> submit(Write write){return IO.submit(()->{write.run();return null;});}
    public static void barrier() throws Exception {submit(()->{}).get();}
    private TransferJournalIO(){}
}
