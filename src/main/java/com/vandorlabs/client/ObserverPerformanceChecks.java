package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.navigation.ObserverScale;
import com.vandorlabs.client.constructors.TEConstructor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.util.*;

/** Exclusive actual FastTESR diagnostic, with equal projected size across footprints. */
final class ObserverPerformanceChecks {
    private static final BlockPos POS=new BlockPos(24,100,24);
    private static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    private static int ticks;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks==1){mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(24,103,24,0,0)).get();return false;}
        if(ticks<40||!mc.world.isBlockLoaded(POS,false))return false;
        output.mkdirs();Framebuffer fb=new Framebuffer(512,512,true);TEConstructor renderer=new TEConstructor();
        try(PrintWriter csv=new PrintWriter(new File(output,"observer-performance.csv"));TurntableRenderState saved=new TurntableRenderState()){
            csv.println("kind,width,height,inverted,quads,median_emit_ns,p95_emit_ns,median_frame_ns,median_alloc_bytes");
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-1,1,-1,1,-10,10);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
            GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.enableDepth();GlStateManager.depthMask(true);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            for(String kind:new String[]{"standalone_dish","standalone_antenna"})for(int width:kind.equals("standalone_dish")?new int[]{3,6,16}:new int[]{2,4,8})for(boolean inverted:new boolean[]{false,true}){
                BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+kind+"_core");mc.world.setBlockToAir(POS);mc.world.setBlockState(POS,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH).withProperty(BlockConstructorCore.FORMED,true),2);TileEntityConstructor tile=(TileEntityConstructor)mc.world.getTileEntity(POS);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger(ObserverScale.WIDTH_TAG,width);tag.setBoolean("ObserverInverted",inverted);tag.setBoolean("NavigationScreenOn",true);tag.setInteger("MachineActivity",2);tile.readFromNBT(tag);
                int height=tile.structureHeight(),expected=kind.equals("standalone_dish")?580:456;double scale=1.2/Math.max(width,height);GlStateManager.loadIdentity();GlStateManager.rotate(15,1,0,0);GlStateManager.rotate(-25,0,1,0);GlStateManager.scale(scale,scale,scale);
                mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();
                for(int n=0;n<189;n++){
                    fb.bindFramebuffer(true);GlStateManager.clearColor(.06F,.08F,.1F,1);GlStateManager.clear(16640);GL11.glFinish();long frameStart=System.nanoTime();buffer.begin(7,DefaultVertexFormats.BLOCK);long allocated=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();renderer.renderTileEntityFast(tile,-width/2D,inverted?height/2D-1:-height/2D,-width/2D,0,-1,1,buffer);long elapsed=System.nanoTime()-start,bytes=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated;
                    if(buffer.getVertexCount()!=expected)throw new IllegalStateException("Observer geometry grew with footprint "+kind+" width="+width+" vertices="+buffer.getVertexCount());tess.draw();GL11.glFinish();long frame=System.nanoTime()-frameStart;
                    if(n>=128){int sample=n-128;CPU[sample]=elapsed;BYTES[sample]=bytes;FRAME[sample]=frame;}
                }
                Arrays.sort(CPU);Arrays.sort(FRAME);Arrays.sort(BYTES);csv.println(kind+","+width+","+height+","+inverted+","+(expected/4)+","+CPU[30]+","+CPU[57]+","+FRAME[30]+","+BYTES[30]);csv.flush();
                java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(512,512,fb);int bright=0;for(int py=0;py<512;py++)for(int px=0;px<512;px++){int rgb=image.getRGB(px,py);if(Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))>48)bright++;}if(bright<1000)throw new IllegalStateException("Observer benchmark lost atlas/visible geometry "+kind+" width="+width+" bright="+bright);javax.imageio.ImageIO.write(image,"png",new File(output,"observer-"+kind+"-"+width+"-"+inverted+".png"));
            }
        }finally{mc.world.setBlockToAir(POS);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);fb.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        System.out.println("[vandorlabs] observer-performance PASS base/double/max upright/inverted actual FastTESR constant145/114quads same-screen-footprint CPU/allocation/GPU-completion software renderer");return true;
    }
    private static final long[] CPU=new long[61],FRAME=new long[61],BYTES=new long[61];
}
