package com.vandorlabs.client;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import java.io.PrintWriter;
import java.util.*;

/** Warm chunk-mesh construction, separate from steady VBO submission and driver cost. */
final class ProgrammableMeshBuildBenchmark {
    static void measure(Minecraft mc,PrintWriter csv,Block block,List<BlockPos> positions,String variant,int count,int expectedVertices) {
        BufferBuilder buffer=new BufferBuilder(65536);
        double[] times=new double[31];long[] allocations=new long[31];
        try {
            for(int sample=-15;sample<31;sample++) {
                long bytes=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                for(int repeat=0;repeat<8;repeat++) {
                    int vertices=0;
                    for(BlockRenderLayer layer:BlockRenderLayer.values()) {
                        ForgeHooksClient.setRenderLayer(layer);buffer.begin(7,DefaultVertexFormats.BLOCK);
                        for(BlockPos pos:positions) {
                            IBlockState state=mc.world.getBlockState(pos);
                            if(state.getRenderType()==EnumBlockRenderType.MODEL && block.canRenderInLayer(state,layer))
                                mc.getBlockRendererDispatcher().renderBlock(state,pos,mc.world,buffer);
                        }
                        vertices+=buffer.getVertexCount();buffer.finishDrawing();buffer.reset();
                    }
                    if(vertices!=expectedVertices)throw new IllegalStateException("unstable mesh fixture: "+block.getRegistryName());
                }
                long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-bytes;
                if(sample>=0){times[sample]=elapsed/8E6;allocations[sample]=used/8;}
            }
        } finally { ForgeHooksClient.setRenderLayer(null); }
        Arrays.sort(times);Arrays.sort(allocations);
        csv.printf(Locale.ROOT,"%s,%s,%d,%d,%.6f,%.6f,%d%n",block.getRegistryName(),variant,count,expectedVertices,times[15],times[29],allocations[15]);
        csv.flush();
    }
}
