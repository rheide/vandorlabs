package com.vandorlabs.redstone;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Arrays;
import com.vandorlabs.redstone.ChannelSourceIndexTest.*;

/** Explicit warmed comparison; not part of default portable fixtures and not a hardware FPS claim. */
final class ChannelSourceIndexAllocationCheck {
    private static volatile int sink;
    // getId is the Java 8 thread identifier API; threadId is unavailable on that target.
    @SuppressWarnings("deprecation")
    private static long currentThreadId(){return Thread.currentThread().getId();}
    static void run()throws Exception{
        Object bean=ManagementFactory.getThreadMXBean();Class<?> allocation=Class.forName("com.sun.management.ThreadMXBean");
        Method enabled=allocation.getMethod("setThreadAllocatedMemoryEnabled",boolean.class),bytes=allocation.getMethod("getThreadAllocatedBytes",long.class);enabled.invoke(bean,true);long thread=currentThreadId();
        Context context=new Context();ChannelSourceIndex<Member,Snapshot> current=new ChannelSourceIndex<>(context);FrozenChannelSourceIndex old=new FrozenChannelSourceIndex(new FrozenChannelSourceIndex.Stats());
        for(int i=0;i<1024;i++){Member member=new Member(i);Snapshot snapshot=new Snapshot(i%5,0,(i/5)%5,256,8);old.put(member,snapshot);current.put(member,snapshot);}
        Member far=new Member(1024);Snapshot distant=new Snapshot(1024,0,0,256,15);old.put(far,distant);current.put(far,distant);ChannelSourceIndexTest.trace=null;
        for(int i=0;i<100000;i++){sink=old.at(i%7,0,(i/7)%7);sink=current.at(i%7,0,(i/7)%7);}
        int queries=100000;long[] beforeBytes=new long[5],afterBytes=new long[5],beforeNanos=new long[5],afterNanos=new long[5];
        for(int round=0;round<5;round++){
            long startBytes=(Long)bytes.invoke(bean,thread),start=System.nanoTime();int sum=0;
            for(int i=0;i<queries;i++)sum+=old.at(i%7,0,(i/7)%7);
            beforeNanos[round]=System.nanoTime()-start;beforeBytes[round]=(Long)bytes.invoke(bean,thread)-startBytes;sink=sum;
            startBytes=(Long)bytes.invoke(bean,thread);start=System.nanoTime();sum=0;
            for(int i=0;i<queries;i++)sum+=current.at(i%7,0,(i/7)%7);
            afterNanos[round]=System.nanoTime()-start;afterBytes[round]=(Long)bytes.invoke(bean,thread)-startBytes;sink=sum;
        }
        Arrays.sort(beforeBytes);Arrays.sort(afterBytes);Arrays.sort(beforeNanos);Arrays.sort(afterNanos);
        if(afterBytes[2]>beforeBytes[2]+queries/100)throw new AssertionError("source-index allocation regression old="+beforeBytes[2]+" new="+afterBytes[2]);
        if(context.sourceCandidates!=old.stats.sourceCandidates||context.sourceBuckets!=old.stats.sourceBuckets)throw new AssertionError("benchmark query counters changed");
        ChannelSourceIndexTest.buffersCleared(context);
        System.out.println("ChannelSourceIndex allocation PASS sources=1024+far15 queries="+queries+" rounds=5 old-bytes/query="+beforeBytes[2]/(double)queries+" new-bytes/query="+afterBytes[2]/(double)queries+" old-median-ns/query="+beforeNanos[2]/(double)queries+" new-median-ns/query="+afterNanos[2]/(double)queries+" sourceCandidates="+context.sourceCandidates+" sourceBuckets="+context.sourceBuckets+" checksum="+sink);
    }
}
