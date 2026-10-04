package com.vandorlabs.client;

import com.vandorlabs.render.*;
import java.util.*;

/** Compare owned-result APIs as well as reusable render working storage. */
public final class TrapdoorCoordinateBenchmark {
    private static volatile double consumed;
    public static void main(String[] args) {
        System.out.println("family,algorithm,leaves,median_ms,median_allocated_bytes");
        for(boolean diagonal:new boolean[]{false,true}) {
            double[][] times=new double[3][31];long[][] bytes=new long[3][31];double[][] out=new double[8][3];
            for(int batch=-15;batch<31;batch++)for(int order=0;order<3;order++) {
                int algorithm=Math.floorMod(batch+order,3);double sum=0;
                long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                for(int i=0;i<4096;i++) {
                    int mode=i%3,turns=i&3;boolean sliding=(i&4)!=0,inverted=(i&8)!=0,reverse=(i&16)!=0;
                    double pose=(i%9)/8D;
                    double[][] corners;
                    if(diagonal) {
                        if(algorithm==0)corners=ReferenceDiagonalTrapdoorGeometry.corners(mode,inverted,turns,sliding,reverse,pose);
                        else if(algorithm==1)corners=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,sliding,reverse,pose);
                        else {DiagonalTrapdoorGeometry.writeCorners(mode,inverted,turns,sliding,reverse,pose,reverse?15/16D:1/16D,15/16D,1,false,out);corners=out;}
                    } else {
                        if(algorithm==0)corners=ReferenceFlatTrapdoorGeometry.corners(mode,sliding,turns,pose);
                        else if(algorithm==1)corners=TrapdoorGeometry.corners(mode,sliding,turns,pose);
                        else {TrapdoorGeometry.writeCorners(mode,sliding,turns,pose,TrapdoorGeometry.OPEN_HINGE,15/16D,out);corners=out;}
                    }
                    for(double[] corner:corners)sum+=corner[0]+corner[1]+corner[2];
                }
                consumed=sum;long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
                if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
            }
            for(int algorithm=0;algorithm<3;algorithm++) {
                Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
                System.out.printf(Locale.ROOT,"%s,%s,4096,%.6f,%d%n",diagonal?"diagonal":"flat",new String[]{"reference","owned_result","reused"}[algorithm],times[algorithm][15],bytes[algorithm][15]);
            }
        }
    }
}
