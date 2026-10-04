package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.util.EnumFacing;
import java.util.Arrays;
import java.util.Locale;

/** Alternating name-lookup batches with escaping results, excluding atlas lookup. */
public final class TextureNameBenchmark {
    private static volatile String consumed;
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        int count=ScreenHousingTextures.IDS.length;
        System.out.println("algorithm,lookups,median_ms,median_allocated_bytes");
        double[][] times=new double[2][31];long[][] bytes=new long[2][31];
        for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
            int algorithm=(batch+order)&1;
            long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(int repeat=0;repeat<16;repeat++)for(int choice=0;choice<count;choice++) {
                consumed=algorithm==0?TextureNameChecks.reference(choice):ScreenHousingTextures.texture(choice);
                consumed=algorithm==0?TextureNameChecks.referenceLit(choice,false):ScreenHousingTextures.texture(choice,false);
                consumed=algorithm==0?TextureNameChecks.referenceStorage(choice,EnumFacing.UP):ScreenHousingTextures.storageTexture(choice,EnumFacing.UP);
            }
            long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
            if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
        }
        for(int algorithm=0;algorithm<2;algorithm++) {
            Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
            System.out.printf(Locale.ROOT,"%s,%d,%.6f,%d%n",algorithm==0?"reference":"current",count*48,times[algorithm][15],bytes[algorithm][15]);
        }
    }
}
