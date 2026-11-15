package com.vandorlabs.persistence;

import java.util.concurrent.*;

/** Bounded serial disk work. Tasks receive already-copied records and must never retain/read a world. */
public final class JournalWorker {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),r->{Thread thread=new Thread(r,"Vandor transaction journals");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    public static <T> Future<T> submit(Callable<T> task){return IO.submit(task);}
    public static void barrier()throws Exception {submit(()->null).get();}
    private JournalWorker(){}
}
