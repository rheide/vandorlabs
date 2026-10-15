package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityControlledRamp;
import com.vandorlabs.ramp.RampGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;

/** Paired CPU submission benchmark with byte-exact legacy material/UV verification. */
final class RampRenderBenchmark {
    private static final EnumFacing[] FACES=EnumFacing.values();
    private static volatile int sink;
    static void run(Minecraft mc,File output)throws IOException{
        BlockPos position=new BlockPos(0,80,0);
        if(!mc.world.isAirBlock(position))throw new IllegalStateException("Ramp benchmark requires an empty fixture cell");
        mc.world.setBlockState(position,com.vandorlabs.blocks.ModBlocks.CONTROLLED_RAMP.getDefaultState(),2);
        try(PrintWriter csv=new PrintWriter(new File(output,"ramp-material-benchmark.csv"))){
            csv.println("source,slices,algorithm,median_us,p95_us,allocated_bytes");
            for(String id:new String[]{"minecraft:stone","minecraft:grass","minecraft:glass","minecraft:oak_stairs","vandorlabs:programmable_block"}){
                TileEntityControlledRamp tile=new TileEntityControlledRamp();tile.setWorld(mc.world);tile.setPos(new BlockPos(0,80,0));
                tile.source=net.minecraft.block.Block.getBlockFromName(id).getDefaultState();tile.sourceY=80;tile.origins.add(tile.getPos());tile.length=1;tile.setHalfOffsets(0,0);
                for(int count:new int[]{1,16,128}){
                    List<RampGeometry.Box> boxes=new ArrayList<>();for(int i=0;i<count;i++)boxes.add(new RampGeometry.Box(0,i/(double)count,0,1,(i+1D)/count,1));
                    BufferBuilder buffer=new BufferBuilder(1<<20);
                    int[] original=emit(buffer,tile,boxes,false),cached=emit(buffer,tile,boxes,true);
                    if(!Arrays.equals(original,cached))throw new IllegalStateException("Ramp material cache changed packed vertices: "+id+" slices="+count);
                    long[][] nanos=new long[2][31],bytes=new long[2][31];
                    for(int sample=-15;sample<31;sample++)for(int order=0;order<2;order++){
                        int algorithm=(sample+order)&1;long before=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                        sink=emit(buffer,tile,boxes,algorithm==1).length;
                        long time=System.nanoTime()-start,allocated=BenchmarkAllocations.currentThreadBytes()-before;
                        if(sample>=0){nanos[algorithm][sample]=time;bytes[algorithm][sample]=allocated;}
                    }
                    for(int i=0;i<2;i++){Arrays.sort(nanos[i]);Arrays.sort(bytes[i]);csv.printf(Locale.ROOT,"%s,%d,%s,%.3f,%.3f,%d%n",id,count,i==0?"reference":"per-draw",nanos[i][15]/1000D,nanos[i][29]/1000D,bytes[i][15]);}csv.flush();
                }
            }
        }finally{mc.world.setBlockToAir(position);}
        System.out.println("[vandorlabs][reprolab] ramp-material-benchmark PASS packed-vertex-equivalence stone grass glass stairs programmable slices1-16-128");
    }
    private static int[] emit(BufferBuilder buffer,TileEntityControlledRamp tile,List<RampGeometry.Box> boxes,boolean cached){
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());TEControlledRamp.FaceEmitter emitter=new TEControlledRamp.FaceEmitter(buffer);
        TEControlledRamp.MaterialFaces faces=cached?new TEControlledRamp.MaterialFaces(tile):null;
        for(RampGeometry.Box box:boxes)if(cached)TEControlledRamp.materialCuboid(emitter,box,tile,.5F,faces);else reference(emitter,box,tile);
        int[] result=buffer.getVertexState().getRawBuffer();buffer.finishDrawing();buffer.reset();return result;
    }
    /** Original per-slice lookups retained only in the opt-in benchmark. */
    private static void reference(TEControlledRamp.FaceEmitter emitter,RampGeometry.Box box,TileEntityControlledRamp tile){
        Minecraft mc=Minecraft.getMinecraft();net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(tile.source);
        double bottom=tile.sideTextureV(box,box.minY,.5),top=tile.sideTextureV(box,box.maxY,.5);double[] shift=tile.textureShift(box,.5);
        TileEntityControlledRamp.SourceMaterial material=tile.sourceMaterial(box,.5);
        for(EnumFacing face:FACES){
            int housing=material.texture(face);List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(tile.source,face,0);
            TextureAtlasSprite sprite=housing>=0?mc.getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(housing)):quads.isEmpty()?model.getParticleTexture():quads.get(0).getSprite();
            int tint=!quads.isEmpty()&&quads.get(0).hasTintIndex()?mc.getBlockColors().colorMultiplier(tile.source,tile.getWorld(),tile.getPos(),quads.get(0).getTintIndex()):0xFFFFFF;
            emitter.emit(box,face,sprite,material.stretchSides()?1:bottom,material.stretchSides()?0:top,face.getAxis()==EnumFacing.Axis.X?shift[1]:shift[0],face.getAxis()==EnumFacing.Axis.Y?shift[1]:0,tint);
        }
    }
}
