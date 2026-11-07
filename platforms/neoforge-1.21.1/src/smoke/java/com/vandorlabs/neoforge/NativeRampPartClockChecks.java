package com.vandorlabs.neoforge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
public final class NativeRampPartClockChecks {
    private static int checks;
    private static void need(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static NativeRampPartVisualClock.State sample(Object world,BlockPos pos,UUID operation,long revision,double observed,double target,double time){return NativeRampPartVisualClock.sample(world,pos,operation,revision,true,observed,target,16,false,0,0,time);}
    public static void main(String[] args){
        Object world=new Object(),other=new Object();UUID operation=UUID.randomUUID(),fresh=UUID.randomUUID();BlockPos pos=new BlockPos(12,80,4);
        var first=sample(world,pos,operation,1,.25,1,0);need(first.pose()==.25,"initial observed");
        var replacement=sample(world,new BlockPos(12,80,4),new UUID(operation.getMostSignificantBits(),operation.getLeastSignificantBits()),1,.1,1,4);
        need(first==replacement&&replacement.pose()==.5,"replacement/equal operation retains progress despite repeated observation");
        need(sample(world,pos,operation,0,0,0,6).pose()==.625,"old revision ignored");
        need(sample(other,pos,operation,1,0,1,6).pose()==0,"world isolation");
        need(sample(world,pos,fresh,1,0,1,6)!=first&&sample(world,pos,fresh,1,0,1,6).pose()==0,"new operation does not inherit old motion");
        for(int i=0;i<2000;i++)sample(world,new BlockPos(i,0,0),operation,1,0,1,6);
        need(NativeRampPartVisualClock.retainedBytes(world)<=NativeRampPartVisualClock.BUDGET,"byte bound");
        sample(world,pos,fresh,1,0,1,6);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();for(int i=0;i<20000;i++)sample(world,pos,fresh,1,0,1,6);long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++)sample(world,pos,fresh,1,0,1,6);long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes==0,"warmed cache sampling allocated "+bytes);
        NativeRampPartVisualClock.clear(world);NativeRampPartVisualClock.clear(other);need(NativeRampPartVisualClock.retainedBytes(world)==0,"world retirement");
        System.out.println("Native Ramp part clock PASS checks="+checks+" samples=100000 allocated="+bytes);
    }
}
