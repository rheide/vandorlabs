package com.vandorlabs.client;

import java.util.*;
import java.awt.image.BufferedImage;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

/** Paired actual driver submission timing plus exact visible pixel parity. */
final class VehicleOpaqueBatchChecks {
    static void run(Minecraft mc,VehicleStructure structure,VehicleWorld view){
        view.origin=mc.player.getPosition();
        List<TileEntity> tiles=new ArrayList<>(view.tiles.values());VehicleOpaqueTileCache cache=new VehicleOpaqueTileCache();VehicleOpaqueTileBatch batch=new VehicleOpaqueTileBatch(tiles);long[][] times=new long[2][24];
        GlStateManager.pushMatrix();GlStateManager.translate(0,-10000,0);
        try{
            for(int mode=0;mode<2;mode++)for(int round=-8;round<24;round++){
                long start=System.nanoTime();draw(cache,batch,tiles,structure,mode==1);if(round>=0)times[mode][round]=System.nanoTime()-start;
            }
        }finally{GlStateManager.popMatrix();}
        try{
            BufferedImage before=capture(mc,cache,batch,tiles,structure,false),after=capture(mc,cache,batch,tiles,structure,true);int changed=0,paint=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;if((before.getRGB(x,y)&0xffffff)!=0x203040)paint++;}
            if(batch.builds!=1||!batch.ready(cache))throw new IllegalStateException("Opaque section rebuilt unchanged geometry: builds="+batch.builds);
            if(changed!=0||paint<100)throw new IllegalStateException("Opaque vehicle batch pixel parity changed="+changed+" visible="+paint);
            lightRefresh(mc,view,tiles,structure,cache,batch);
            for(long[] samples:times)Arrays.sort(samples);
            System.out.println("[vandorlabs][reprolab] vehicle-opaque-batch PASS tiles="+tiles.size()+" samples=24 previous-us="+times[0][12]/1000+" batched-us="+times[1][12]/1000+" driver-submissions/frame="+tiles.size()+"->1 changed-pixels="+changed);
        }finally{batch.delete();cache.delete();}
        if(batch.ready(cache))throw new IllegalStateException("Deleted opaque VBO is still ready");
    }
    private static void lightRefresh(Minecraft mc,VehicleWorld view,List<TileEntity> tiles,VehicleStructure structure,VehicleOpaqueTileCache cache,VehicleOpaqueTileBatch batch){
        List<net.minecraft.util.math.BlockPos> positions=new ArrayList<>();int[] sky=new int[tiles.size()],block=new int[tiles.size()];
        int built=batch.builds;VehicleOpaqueTileBatch fresh=new VehicleOpaqueTileBatch(tiles);
        try{
            for(int i=0;i<tiles.size();i++){
                net.minecraft.util.math.BlockPos p=view.origin.add(tiles.get(i).getPos());positions.add(p);
                sky[i]=mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY,p);block[i]=mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,p);
                mc.world.setLightFor(net.minecraft.world.EnumSkyBlock.SKY,p,sky[i]==0?15:0);mc.world.setLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,p,block[i]==15?0:15);
            }
            if(batch.ready(cache))throw new IllegalStateException("wall light fixture did not change native light");
            batch.draw(cache,structure,0);fresh.draw(cache,structure,0);
            if(batch.builds!=built||batch.relights<1||!Arrays.equals(batch.recordCopy(),fresh.recordCopy()))throw new IllegalStateException("Light-only wall refresh differs from fresh geometry or rebuilt geometry");
            com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long[][] time=new long[2][24],allocated=new long[2][24];
            GlStateManager.pushMatrix();GlStateManager.translate(0,-10000,0);
            try{for(int mode=0;mode<2;mode++)for(int round=-4;round<24;round++){
                for(net.minecraft.util.math.BlockPos p:positions){mc.world.setLightFor(net.minecraft.world.EnumSkyBlock.SKY,p,(round&1)==0?0:15);mc.world.setLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,p,(round&1)==0?15:0);}
                long bytes=bean.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
                if(mode==0){fresh.delete();fresh.draw(cache,structure,0);}else batch.draw(cache,structure,0);
                if(round>=0){time[mode][round]=System.nanoTime()-start;allocated[mode][round]=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;}
            }}finally{GlStateManager.popMatrix();}
            for(long[] row:time)Arrays.sort(row);for(long[] row:allocated)Arrays.sort(row);
            if(batch.builds!=built||!Arrays.equals(batch.recordCopy(),fresh.recordCopy()))throw new IllegalStateException("Repeated light refresh changed geometry or shader records");
            System.out.println("[vandorlabs][reprolab] vehicle-wall-relight PASS retained geometry, exact fresh-build vertex parity tiles="+tiles.size()+" geometry-us="+time[0][12]/1000+" relight-us="+time[1][12]/1000+" geometry-bytes="+allocated[0][12]+" relight-bytes="+allocated[1][12]);
        }finally{
            fresh.delete();for(int i=0;i<positions.size();i++){mc.world.setLightFor(net.minecraft.world.EnumSkyBlock.SKY,positions.get(i),sky[i]);mc.world.setLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,positions.get(i),block[i]);}
        }
    }
    private static void draw(VehicleOpaqueTileCache cache,VehicleOpaqueTileBatch batch,List<TileEntity> tiles,VehicleStructure structure,boolean grouped){
        if(grouped)batch.draw(cache,structure,0);else for(TileEntity tile:tiles){GlStateManager.pushMatrix();try{cache.draw(tile,TileEntityRendererDispatcher.instance.getRenderer(tile),structure,0);}finally{GlStateManager.popMatrix();}}
    }
    private static BufferedImage capture(Minecraft mc,VehicleOpaqueTileCache cache,VehicleOpaqueTileBatch batch,List<TileEntity> tiles,VehicleStructure structure,boolean grouped){
        Framebuffer frame=new Framebuffer(128,128,true);frame.bindFramebuffer(true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,8,0,8,-32,32);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.loadIdentity();GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);mc.entityRenderer.enableLightmap();
            draw(cache,batch,tiles,structure,grouped);GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private VehicleOpaqueBatchChecks(){}
}
