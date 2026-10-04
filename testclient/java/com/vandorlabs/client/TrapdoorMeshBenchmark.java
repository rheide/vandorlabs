package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import com.vandorlabs.render.TrapdoorGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.Arrays;
import java.util.Locale;

/** Paired CPU/allocation measurement; no world traversal, draw calls or GPU timing. */
public final class TrapdoorMeshBenchmark {
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        TextureAtlasSprite upper=new TextureAtlasSprite("upper"){},lower=new TextureAtlasSprite("lower"){};
        upper.setIconWidth(16);upper.setIconHeight(16);upper.initSprite(256,256,32,16,false);
        lower.setIconWidth(16);lower.setIconHeight(16);lower.initSprite(256,256,64,16,false);
        System.out.println("variant,algorithm,draws,median_ms,median_allocated_bytes");
        for(int variant=0;variant<3;variant++) {
            double[][] uv=new double[8][3];
            for(int i=0;i<8;i++)uv[i]=new double[]{((i&1)==0?0:1)+(variant==1?.25:0),((i&2)==0?0:1),((i&4)==0?0:1)};
            double[][][] poses=new double[16][][];
            for(int i=0;i<poses.length;i++)poses[i]=variant==0?TrapdoorGeometry.corners(0,false,0,i/15D)
                    :DiagonalTrapdoorGeometry.corners(0,false,0,false,false,i/15D);
            for(boolean cached:new boolean[]{false,true}) {
                BufferBuilder buffer=new BufferBuilder(16384);
                double[] times=new double[31];long[] bytes=new long[31];
                for(int batch=-15;batch<31;batch++) {
                    long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                    for(int draw=0;draw<256;draw++) {
                        buffer.begin(7,BlockSurfaceFormat.get());
                        if(cached)TrapdoorSurfaceMesh.draw(buffer,upper,lower,poses[draw%16],uv,0xD00070,variant==0?0:2,variant==0?2:4,true,true,variant==2);
                        else ReferenceTrapdoorSurfaceMesh.draw(buffer,upper,lower,poses[draw%16],uv,0xD00070,variant==0?0:2,variant==0?2:4,true,true,variant==2);
                        buffer.finishDrawing();buffer.reset();
                    }
                    long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
                    if(batch>=0){times[batch]=elapsed/1E6;bytes[batch]=used;}
                }
                Arrays.sort(times);Arrays.sort(bytes);
                System.out.printf(Locale.ROOT,"%s,%s,256,%.6f,%d%n",new String[]{"flat","diagonal_clipped","diagonal_custom"}[variant],cached?"cached":"reference",times[15],bytes[15]);
            }
        }
    }
}
