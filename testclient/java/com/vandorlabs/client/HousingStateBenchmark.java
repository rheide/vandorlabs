package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;
import java.util.Locale;

/** Alternating paired CPU measurements; each snapshot escapes as it does during meshing. */
public final class HousingStateBenchmark {
    private static volatile IBlockState consumed;
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        System.out.println("family,algorithm,snapshots,median_ms,median_allocated_bytes");
        for(boolean slab:new boolean[]{false,true}) {
            Block block=slab?new BlockProgrammableSlab():new BlockProgrammableBlock();
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            BlockPos pos=new BlockPos(8,100,8);IBlockState state=block.getDefaultState();world.setBlockState(pos,state,2);
            double[][] times=new double[2][31];long[][] bytes=new long[2][31];
            for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
                int algorithm=(batch+order)&1;
                long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                for(int i=0;i<512;i++)consumed=algorithm==0?ReferenceHousingState.extend(state,world,pos,slab)
                        :block.getExtendedState(state,world,pos);
                long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
                if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
            }
            for(int algorithm=0;algorithm<2;algorithm++) {
                Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
                System.out.printf(Locale.ROOT,"%s,%s,512,%.6f,%d%n",slab?"slab":"block",algorithm==0?"reference":"current",times[algorithm][15],bytes[algorithm][15]);
            }
        }
    }
}
