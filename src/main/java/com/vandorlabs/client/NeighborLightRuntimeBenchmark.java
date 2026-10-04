package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

/** Paired sampling against the released renderer loop in the same live client world. */
final class NeighborLightRuntimeBenchmark {
    private static volatile long sink;
    static void run(Minecraft client,File output) throws Exception {
        TileEntityAnimatedScreenSelector[] tiles=new TileEntityAnimatedScreenSelector[64];
        for(int i=0;i<tiles.length;i++) {
            TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();
            tile.setWorld(client.world);tile.setPos(new BlockPos((i%8)*4-16,80,(i/8)*4-16));tiles[i]=tile;
            if(reference(tile)!=TEAnimatedScreenSelector.neighborLight(tile))throw new IllegalStateException("neighbor light differs");
        }
        double[][] times=new double[2][31];long[][] bytes=new long[2][31];
        for(int sample=-31;sample<31;sample++)for(int order=0;order<2;order++) {
            int algorithm=(sample+order)&1;long sum=0;
            long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(int i=0;i<4096;i++)sum+=algorithm==0?reference(tiles[i&63]):TEAnimatedScreenSelector.neighborLight(tiles[i&63]);
            long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;sink=sum;
            if(sample>=0){times[algorithm][sample]=elapsed/1E6;bytes[algorithm][sample]=used;}
        }
        try(PrintWriter csv=new PrintWriter(new File(output,"neighbor-light-benchmark.csv"))) {
            csv.println("algorithm,queries,median_ms,median_allocated_bytes");
            for(int algorithm=0;algorithm<2;algorithm++) {
                Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
                csv.printf(Locale.ROOT,"%s,4096,%.6f,%d%n",algorithm==0?"released":"shared_mutable",times[algorithm][15],bytes[algorithm][15]);
            }
        }
        System.out.println("[vandorlabs][reprolab] neighbor-light-benchmark PASS (exact live samples and paired measurements)");
    }
    private static int reference(TileEntityAnimatedScreenSelector tile) {
        int sky=0,block=0;
        for(EnumFacing side:EnumFacing.values()) {
            BlockPos neighbor=tile.getPos().offset(side);
            if(!tile.getWorld().isBlockLoaded(neighbor))continue;
            int combined=tile.getWorld().getCombinedLight(neighbor,0);
            sky=Math.max(sky,combined>>>16);block=Math.max(block,combined&65535);
        }
        return (sky<<16)|block;
    }
}
