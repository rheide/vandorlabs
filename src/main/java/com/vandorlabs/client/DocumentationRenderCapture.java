package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import java.io.File;

/** Deterministic in-game renderer captures; no model screenshots are assembled by hand. */
final class DocumentationRenderCapture {
    static void capture(File output,String name,int count,double halfWidth,float pitch,float yaw,double cx,double cy,double cz,java.util.function.IntConsumer draw)throws Exception{
        File folder=new File(output,"animation-"+name);if(!folder.mkdirs()&&!folder.isDirectory())throw new IllegalStateException("Cannot create animation directory");
        Minecraft mc=Minecraft.getMinecraft();Framebuffer frame=new Framebuffer(640,420,true);
        GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-halfWidth,halfWidth,-halfWidth*420/640,halfWidth*420/640,-20,20);
        GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            int[] first=null;boolean changed=false;
            for(int i=0;i<count;i++){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.rotate(pitch,1,0,0);GlStateManager.rotate(yaw,0,1,0);GlStateManager.translate(-cx,-cy,-cz);
                GlStateManager.clearColor(.06F,.09F,.12F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);
                mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);draw.accept(i);
                java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(640,420,frame);int[] pixels=image.getRGB(0,0,640,420,null,0,640);if(first==null)first=pixels;else changed|=!java.util.Arrays.equals(first,pixels);
                javax.imageio.ImageIO.write(image,"png",new File(folder,String.format(java.util.Locale.ROOT,"frame-%03d.png",i)));
            }
            if(!changed)throw new IllegalStateException("Animation did not change: "+name);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        System.out.println("[vandorlabs][reprolab] documentation-render PASS "+name+" frames="+count);
    }
}
