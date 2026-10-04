package com.vandorlabs.client;

import com.vandorlabs.render.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Same closed/moving mesh rays, alternating released and scalar implementations. */
public final class DiagonalRayBenchmark {
    private static volatile int consumed;
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        Random random=new Random(0x524159);BlockPos pos=new BlockPos(-17,64,16);
        double[][][] meshes=new double[64][][];Vec3d[] starts=new Vec3d[64],ends=new Vec3d[64];
        for(int i=0;i<64;i++) {
            meshes[i]=DiagonalTrapdoorGeometry.corners(i%3,(i&1)!=0,i&3,(i&2)!=0,(i&4)!=0,(i%5)/4D);
            Vec3d center=new Vec3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            Vec3d offset=new Vec3d(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
            starts[i]=center.add(offset);ends[i]=center.subtract(offset);
        }
        double[][] times=new double[2][31];long[][] bytes=new long[2][31];
        for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
            int algorithm=(batch+order)&1,sum=0;
            long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(int i=0;i<4096;i++) {
                int j=i&63;RayTraceResult hit=algorithm==0?ReferenceDiagonalRayTrace.trace(meshes[j],pos,starts[j],ends[j])
                        :DiagonalTrapdoorRayTrace.trace(meshes[j],pos,starts[j],ends[j]);
                if(hit!=null)sum+=hit.sideHit.getIndex();
            }
            consumed=sum;long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
            if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
        }
        System.out.println("algorithm,rays,median_ms,median_allocated_bytes");
        for(int algorithm=0;algorithm<2;algorithm++) {
            Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
            System.out.printf(Locale.ROOT,"%s,4096,%.6f,%d%n",algorithm==0?"reference":"scalar",times[algorithm][15],bytes[algorithm][15]);
        }
    }
}
