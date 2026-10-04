package com.vandorlabs.client;

import com.vandorlabs.tiles.OffsetTrapdoorInteractions;
import net.minecraft.util.math.*;
import java.util.*;

/** Full empty-world picking/collision scans, including current loaded-world lookups. */
public final class OffsetInteractionBenchmark {
    private static volatile Object consumed;
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        Vec3d start=new Vec3d(8.5,100.5,8.5),end=start.addVector(3.8,-1.3,2.4);
        AxisAlignedBB box=new AxisAlignedBB(8.2,100,8.2,8.8,101.8,8.8);
        List<AxisAlignedBB> boxes=new ArrayList<>();
        System.out.println("operation,algorithm,queries,median_ms,median_allocated_bytes");
        for(int operation=0;operation<2;operation++) {
            double[][] times=new double[2][31];long[][] bytes=new long[2][31];
            for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
                int algorithm=(batch+order)&1;
                long allocation=BenchmarkAllocations.currentThreadBytes(),time=System.nanoTime();
                for(int i=0;i<256;i++) {
                    if(operation==0)consumed=algorithm==0?ReferenceOffsetTrapdoorInteractions.trace(world,start,end):OffsetTrapdoorInteractions.trace(world,start,end);
                    else {
                        boxes.clear();
                        if(algorithm==0)ReferenceOffsetTrapdoorInteractions.addCollisions(world,box,boxes);
                        else OffsetTrapdoorInteractions.addCollisions(world,box,boxes);
                        consumed=boxes;
                    }
                }
                long elapsed=System.nanoTime()-time,used=BenchmarkAllocations.currentThreadBytes()-allocation;
                if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
            }
            for(int algorithm=0;algorithm<2;algorithm++) {
                Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
                System.out.printf(Locale.ROOT,"%s,%s,256,%.6f,%d%n",operation==0?"trace":"collision",algorithm==0?"reference":"current",times[algorithm][15],bytes[algorithm][15]);
            }
        }
    }
}
