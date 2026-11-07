package com.vandorlabs.client;
import com.vandorlabs.programmer.ProgrammerGeometry;
import java.lang.management.ManagementFactory;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
/** Reproducible current-thread allocation probe; CPU evidence, not a hardware frame-rate claim. */
public final class ProgrammerPickingCostChecks {
 private static volatile Object sink;
 private static void batch(int count,boolean miss){BlockPos pos=new BlockPos(0,0,0);Vec3d start=new Vec3d(.5,miss?2.5:.5,-1),end=new Vec3d(.5,miss?2.5:.5,2);for(int i=0;i<count;i++)sink=ProgrammerGeometry.trace(pos,pos,EnumFacing.NORTH,start,end);}
 public static void main(String[] args){com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemorySupported())throw new AssertionError("Thread allocation measurement unavailable");bean.setThreadAllocatedMemoryEnabled(true);for(boolean miss:new boolean[]{false,true}){batch(12000,miss);long id=Thread.currentThread().getId(),allocated=bean.getThreadAllocatedBytes(id),start=System.nanoTime();batch(20000,miss);long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-allocated;System.out.println("Programmer authored picker cost "+(miss?"miss":"hit")+" calls=20000 bytes="+bytes+" ns="+elapsed+" bytesPerCall="+(bytes/20000.0));if((sink==null)!=miss)throw new AssertionError("Probe did not exercise expected hit branch");}}
}
