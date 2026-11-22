package com.vandorlabs.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
final class CornerGlassIconChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(256,128,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,256,128,0,1000,3000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.translate(0,0,-2000);
        try{
            frame.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.12549F,.188235F,.25098F,1);GlStateManager.clear(16640);RenderHelper.enableGUIStandardItemLighting();int cases=0;
            String[] ids={"programmable_glass","programmable_corner_glass","programmable_diagonal_corner_glass","programmable_corner_wall","programmable_diagonal_corner_wall"};
            for(int i=0;i<ids.length;i++)mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(net.minecraft.block.Block.getBlockFromName("vandorlabs:"+ids[i])),20+i*40,16);
            RenderHelper.disableStandardItemLighting();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(256,128,frame);
            javax.imageio.ImageIO.write(image,"png",new java.io.File(output,"corner-glass-icons.png"));
            int background=image.getRGB(0,0);
            for(int i=0;i<ids.length;i++){
                int ink=0;for(int x=0;x<16;x++)for(int y=0;y<16;y++)if(image.getRGB(20+i*40+x,16+y)!=background){ink++;if(x==0||y==0||x==15||y==15)throw new IllegalStateException("Corner icon touches slot edge id="+ids[i]+" x="+x+" y="+y);}
                if(ink<25)throw new IllegalStateException("Barely visible glass icon id="+ids[i]+" pixels="+ink);cases++;
            }
            System.out.println("[vandorlabs][reprolab] corner-glass-icons PASS padded variants="+cases);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.enableCull();}
    }
}
