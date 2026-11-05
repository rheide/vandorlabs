package com.vandorlabs.redstone;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Arrays;
import com.vandorlabs.redstone.ChannelRecipientIndexTest.*;

/** Explicit warmed comparison against the frozen old recipient engine. */
final class ChannelRecipientIndexAllocationCheck {
    private static volatile int sink;
    /** Java 8 compatible thread identity; threadId() is unavailable on that target. */
    @SuppressWarnings("deprecation")
    private static long currentThreadId(){return Thread.currentThread().getId();}
    static void run()throws Exception{
        Object bean=ManagementFactory.getThreadMXBean();Class<?> allocation=Class.forName("com.sun.management.ThreadMXBean");
        Method enabled=allocation.getMethod("setThreadAllocatedMemoryEnabled",boolean.class),bytes=allocation.getMethod("getThreadAllocatedBytes",long.class);enabled.invoke(bean,true);long thread=currentThreadId();
        Stats stats=new Stats();Index current=new Index(stats);FrozenChannelRecipientIndex old=new FrozenChannelRecipientIndex(new Stats());
        for(int i=0;i<2049;i++){Member member=new Member(i);Position snapshot=new Position(i==0?0:10000+i*32,0,i==0?0:10000,i,false);old.put(member,snapshot);current.put(member,snapshot);}
        Position query=new Position(0,0,0,-1,false);ChannelRecipientIndexTest.trace=null;
        for(int range:new int[]{16,0,-1}){
            for(int i=0;i<20000;i++){sink=old.find(query,range).size();sink=current.find(query,range).size();}
            int queries=range==0?1000:20000;long[] oldBytes=new long[5],newBytes=new long[5],oldNanos=new long[5],newNanos=new long[5];
            for(int round=0;round<5;round++){
                long startBytes=(Long)bytes.invoke(bean,thread),start=System.nanoTime();int sum=0;for(int i=0;i<queries;i++)sum+=old.find(query,range).size();oldNanos[round]=System.nanoTime()-start;oldBytes[round]=(Long)bytes.invoke(bean,thread)-startBytes;sink=sum;
                startBytes=(Long)bytes.invoke(bean,thread);start=System.nanoTime();sum=0;for(int i=0;i<queries;i++)sum+=current.find(query,range).size();newNanos[round]=System.nanoTime()-start;newBytes[round]=(Long)bytes.invoke(bean,thread)-startBytes;sink=sum;
            }
            Arrays.sort(oldBytes);Arrays.sort(newBytes);Arrays.sort(oldNanos);Arrays.sort(newNanos);
            if(newBytes[2]>oldBytes[2]+queries/100)throw new AssertionError("recipient allocation regression range="+range+" old="+oldBytes[2]+" new="+newBytes[2]);
            if(stats.buckets!=old.stats.buckets||stats.candidates!=old.stats.candidates)throw new AssertionError("counter regression");
            System.out.println("ChannelRecipientIndex allocation PASS range="+range+" recipients=2049 queries="+queries+" rounds=5 old-bytes/query="+oldBytes[2]/(double)queries+" new-bytes/query="+newBytes[2]/(double)queries+" old-median-ns/query="+oldNanos[2]/(double)queries+" new-median-ns/query="+newNanos[2]/(double)queries+" checksum="+sink);
        }
    }
}
