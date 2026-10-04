package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.tiles.TileEntityControlledRamp;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Paired CPU-only work; excludes world lookup, GL submission and ramp motion geometry. */
public final class RampWorkBenchmark {
    private static volatile int consumed;
    private static final EnumFacing[] FACES=EnumFacing.values();
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        ModBlocks.PROGRAMMABLE_BLOCK=new BlockProgrammableBlock();
        TileEntityControlledRamp tile=new TileEntityControlledRamp();
        tile.setPos(new BlockPos(2,60,4));tile.source=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState();
        for(int i=0;i<8;i++) {
            BlockPos origin=tile.getPos().add(i-2,-1,0);tile.origins.add(origin);
            NBTTagCompound saved=new NBTTagCompound();saved.setInteger(SaveSchema.Screen.HOUSING_TEXTURE,4);
            saved.setBoolean("FaceTexturesEnabled",true);saved.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});
            tile.sourceTileTags.put(origin,saved);
        }
        RampGeometry.Box box=new RampGeometry.Box(.1,-.5,.2,.9,.25,.95);
        TextureAtlasSprite sprite=new TextureAtlasSprite("ramp-benchmark"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
        BufferBuilder buffer=new BufferBuilder(1024*1024);
        TEControlledRamp.FaceEmitter emitter=new TEControlledRamp.FaceEmitter(buffer);
        System.out.println("family,algorithm,slices,median_ms,median_allocated_bytes");
        for(boolean vertices:new boolean[]{false,true}) {
            double[][] times=new double[2][31];long[][] bytes=new long[2][31];
            for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
                int algorithm=(batch+order)&1,sum=0;
                long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                if(vertices)buffer.begin(7,BlockSurfaceFormat.get());
                for(int i=0;i<1024;i++) {
                    TileEntityControlledRamp.SourceMaterial material=!vertices && algorithm==1?tile.sourceMaterial(box,.5):null;
                    for(EnumFacing face:FACES) {
                        if(vertices) {
                            if(algorithm==0)ReferenceRampEmitter.emitFace(buffer,box,face,sprite,.17,.83,.23,.11,0x7BAF53);
                            else emitter.emit(box,face,sprite,.17,.83,.23,.11,0x7BAF53);
                        } else sum+=algorithm==0?RampMaterialChecks.reference(tile,box,.5,face):material.texture(face);
                    }
                }
                if(vertices){buffer.finishDrawing();sum=buffer.getByteBuffer().getInt(0);}
                consumed=sum;long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
                if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
            }
            for(int algorithm=0;algorithm<2;algorithm++) {
                Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
                System.out.printf(Locale.ROOT,"%s,%s,1024,%.6f,%d%n",vertices?"vertices":"saved_material",algorithm==0?"reference":"slice",times[algorithm][15],bytes[algorithm][15]);
            }
        }
    }
}
