package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import com.vandorlabs.render.DiagonalTrapdoorCollision;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Paired collision-query timings; retained boxes escape through the consumed list. */
public final class TrapdoorCollisionBenchmark {
    private static volatile List<AxisAlignedBB> consumed;
    public static void main(String[] args) {
        System.out.println("pose,query,algorithm,queries,median_ms,median_allocated_bytes");
        BlockPos pos=new BlockPos(8,100,8);
        for(int pose=0;pose<3;pose++)for(int query=0;query<3;query++) {
            double[][] v=DiagonalTrapdoorGeometry.corners(1,false,0,pose==2,false,pose==0?0:1);
            int across=pose==1?16:1;
            AxisAlignedBB box=(query==0?new AxisAlignedBB(-2,-2,-2,2,2,2)
                    :query==1?new AxisAlignedBB(0,.3,0,.4,1.2,.4):new AxisAlignedBB(3,0,3,4,1,4)).offset(pos);
            List<AxisAlignedBB> boxes=new ArrayList<>(256);
            double[][] times=new double[2][31];long[][] bytes=new long[2][31];
            for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
                int algorithm=(batch+order)&1;
                long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                for(int i=0;i<64;i++) {
                    boxes.clear();
                    if(algorithm==0)ReferenceDiagonalTrapdoorCollision.add(v,2,across,pos,box,boxes);
                    else DiagonalTrapdoorCollision.add(v,2,across,pos,box,boxes);
                    consumed=boxes;
                }
                long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
                if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
            }
            for(int algorithm=0;algorithm<2;algorithm++) {
                Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
                System.out.printf(Locale.ROOT,"%s,%s,%s,64,%.6f,%d%n",new String[]{"closed","rotated","sliding"}[pose],
                        new String[]{"enclosing","partial","miss"}[query],algorithm==0?"reference":"current",times[algorithm][15],bytes[algorithm][15]);
            }
        }
    }
}
