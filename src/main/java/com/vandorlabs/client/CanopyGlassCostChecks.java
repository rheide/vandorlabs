package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Actual cached TESR submissions versus one prebuilt chunk-layer VBO.
 * Small offscreen targets measure CPU submission, not hardware frame rate. */
final class CanopyGlassCostChecks {
    private static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final TECanopy RENDERER=new TECanopy();
    private static final Field CACHE=field(TECanopy.class,"CACHE");
    private static long bytes(){return ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId());}
    private static Field field(Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(Exception e){throw new IllegalStateException(e);}}
    private static final class Scene implements AutoCloseable {
        final VehicleWorld world;final int side,count;final List<TileEntityCanopy> tiles=new ArrayList<>();final IBlockState state;
        VertexBuffer vanilla;int vertices,calls;
        Scene(Minecraft mc,int side,Block block,int shade,int tint,int material){
            this.side=side;count=side*side;state=block.getDefaultState();List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int x=0;x<side;x++)for(int z=0;z<side;z++){
                BlockPos pos=new BlockPos(x,0,z);NBTTagCompound data=null;
                if(block instanceof BlockCanopy){TileEntityCanopy t=new TileEntityCanopy();t.setPos(pos);t.large.shade=shade;t.large.tint=tint;t.large.material=material;t.large.textureLayout(0);data=t.writeToNBT(new NBTTagCompound());t.large.write(data);}
                cells.add(new VehicleStructure.Cell(pos,state,data));
            }
            world=new VehicleWorld(mc.world,VehicleStructure.display(cells));world.origin=new BlockPos(0,80,0);
            for(net.minecraft.tileentity.TileEntity tile:world.tiles.values())if(tile instanceof TileEntityCanopy)tiles.add((TileEntityCanopy)tile);
            if(block instanceof BlockCanopy){
                if(tiles.size()!=count)throw new IllegalStateException("Incomplete canopy scene");
                for(TileEntityCanopy tile:tiles)if(tile.large.shade!=shade||tile.large.tint!=tint||tile.large.material!=material)throw new IllegalStateException("Canopy benchmark lost requested finish");
            }
        }
        void prepare(Minecraft mc){
            if(!tiles.isEmpty()){draw();return;}
            BufferBuilder buffer=new BufferBuilder(1<<20);buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
            net.minecraft.util.BlockRenderLayer previous=net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
            try{ForgeHooksClient.setRenderLayer(state.getBlock().getBlockLayer());
                for(int x=0;x<side;x++)for(int z=0;z<side;z++)mc.getBlockRendererDispatcher().renderBlock(state,new BlockPos(x,0,z),world,buffer);
            }finally{ForgeHooksClient.setRenderLayer(previous);}
            buffer.finishDrawing();vertices=buffer.getVertexCount();if(vertices==0)throw new IllegalStateException("Empty vanilla control mesh");vanilla=new VertexBuffer(new VertexFormat(DefaultVertexFormats.BLOCK));vanilla.bufferData(buffer.getByteBuffer());calls=1;
        }
        void draw(){
            if(tiles.isEmpty()){BlockMeshVbo.draw(vanilla,DefaultVertexFormats.BLOCK,state.getBlock().getBlockLayer());return;}
            for(int pass=0;pass<2;pass++)for(TileEntityCanopy tile:tiles)if(tile.shouldRenderInPass(pass))RENDERER.renderPass(tile,tile.getPos().getX(),0,tile.getPos().getZ(),1,-1,1,pass);
        }
        @SuppressWarnings("unchecked") void stats()throws Exception{
            if(tiles.isEmpty())return;vertices=0;calls=0;Map<TileEntityCanopy,?> cache=(Map<TileEntityCanopy,?>)CACHE.get(null);
            for(TileEntityCanopy tile:tiles){Object p=cache.get(tile);if(p==null)throw new IllegalStateException("Missing prepared canopy");
                for(String name:new String[]{"opaque","glass"}){
                    List<?> faces=(List<?>)field(p.getClass(),name).get(p);if(!faces.isEmpty())calls++;
                    for(Object draw:faces){CanopyMesh.Face face=(CanopyMesh.Face)field(draw.getClass(),"source").get(draw);vertices+=(face.vertices.length==4?4:(face.vertices.length-2)*4)*(face.doubleSided?2:1);}
                }
            }
        }
        public void close(){if(vanilla!=null)vanilla.deleteGlBuffers();TECanopy.invalidate();TEAnimatedScreenSelector.unloadGroups(world);}
    }
    private static long[] sample(Runnable work,int loops){
        long[] times=new long[11],alloc=new long[11];
        for(int i=0;i<times.length;i++){GL11.glFinish();long b=bytes(),start=System.nanoTime();for(int k=0;k<loops;k++)work.run();times[i]=(System.nanoTime()-start)/loops;alloc[i]=(bytes()-b)/loops;}
        Arrays.sort(times);Arrays.sort(alloc);return new long[]{times[5],alloc[5]};
    }
    static void run(Minecraft mc,File output)throws Exception{
        if(!ALLOC.isThreadAllocatedMemoryEnabled())ALLOC.setThreadAllocatedMemoryEnabled(true);
        if(!OpenGlHelper.useVbo())throw new IllegalStateException("Canopy comparison requires VBOs enabled");
        RENDERER.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;net.minecraft.world.World prior=dispatcher.world;double ex=dispatcher.entityX,ey=dispatcher.entityY,ez=dispatcher.entityZ;
        Framebuffer target=new Framebuffer(64,64,true);int cases=0;
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(PrintWriter report=new PrintWriter(new File(output,"canopy-glass-costs.csv"));TurntableRenderState saved=new TurntableRenderState()){
            report.println("kind,blocks,shade,tint,material,draw_calls,vertices,vbo_bytes,cold_prepare_ms,hot_submit_ms,hot_alloc_bytes,base_faces_ns_per_block,base_faces_alloc_per_block,shade_change_ms,opacity_change_ms");
            try(Scene warm=new Scene(mc,8,Block.getBlockFromName("vandorlabs:regular_canopy_glass"),0,1,-1)){
                dispatcher.world=warm.world;target.bindFramebuffer(true);warm.prepare(mc);for(int i=0;i<160;i++)warm.draw();GL11.glFinish();
            }
            for(int side:new int[]{1,8,16})for(int variant=0;variant<11;variant++){
                boolean canopy=variant>=2;int shade=variant==6?12:0,tint=variant<2?1:variant<=5?variant-2:variant==6?1:variant-7,material=variant>=7?0:-1;
                Block block=variant==0?Blocks.GLASS:variant==1?Blocks.STAINED_GLASS:Block.getBlockFromName("vandorlabs:regular_canopy_glass");
                try(Scene scene=new Scene(mc,side,block,shade,tint,material)){
                    dispatcher.world=scene.world;dispatcher.entityX=side/2D;dispatcher.entityY=8;dispatcher.entityZ=-8;
                    target.bindFramebuffer(true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GlStateManager.ortho(-1,side+1,-side-1,1,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();GlStateManager.rotate(90,1,0,0);
                    GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                    GL11.glFinish();long start=System.nanoTime();scene.prepare(mc);double cold=(System.nanoTime()-start)/1e6;
                    for(int warm=0;warm<24;warm++)scene.draw();
                    long[] hot=sample(scene::draw,3),base={0,0};scene.stats();
                    if(scene.calls==0||scene.vertices==0)throw new IllegalStateException("Empty measured canopy mesh");
                    if(canopy){base=sample(()->{for(TileEntityCanopy tile:scene.tiles)tile.baseFaces();},Math.max(3,1000/scene.count));}
                    int vertices=scene.vertices,calls=scene.calls;double shadeChange=-1,opacityChange=-1;
                    if(canopy){
                        long[] shades=new long[5],tints=new long[5];
                        for(int i=0;i<5;i++){
                            for(TileEntityCanopy tile:scene.tiles)tile.large.shade=(i&1)==0?12:0;
                            GL11.glFinish();start=System.nanoTime();scene.draw();shades[i]=System.nanoTime()-start;
                            for(TileEntityCanopy tile:scene.tiles)tile.large.tint=(i&1)==0?GlassTextures.OPAQUE:1;
                            GL11.glFinish();start=System.nanoTime();scene.draw();tints[i]=System.nanoTime()-start;
                        }
                        Arrays.sort(shades);Arrays.sort(tints);shadeChange=shades[2]/1e6;opacityChange=tints[2]/1e6;
                    }
                    String kind=variant==0?"vanilla_glass":variant==1?"vanilla_stained_glass":"canopy_glass";int stride=canopy?DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL.getNextOffset():DefaultVertexFormats.BLOCK.getNextOffset();
                    report.printf(java.util.Locale.ROOT,"%s,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%d,%d,%d,%.6f,%.6f%n",kind,scene.count,shade,tint,material,calls,vertices,vertices*stride,cold,hot[0]/1e6,hot[1],base[0]/scene.count,base[1]/scene.count,shadeChange,opacityChange);report.flush();cases++;
                }
            }
        }finally{dispatcher.world=prior;dispatcher.entityX=ex;dispatcher.entityY=ey;dispatcher.entityZ=ez;TECanopy.invalidate();GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        System.out.println("[vandorlabs][reprolab] canopy-glass-cost PASS cases="+cases+" 1/64/256 blocks clear/stained vanilla chunk VBO versus real canopy TESR native shades four tints replacement material cold/warm/edit costs CPU allocation geometry counts; not hardware FPS");
    }
    private CanopyGlassCostChecks(){}
}
