package com.vandorlabs.client;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.opengl.GL11;
final class StairIconChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(256,128,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,256,128,0,1000,3000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.translate(0,0,-2000);
        try{
            frame.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.12549F,.188235F,.25098F,1);GlStateManager.clear(16640);RenderHelper.enableGUIStandardItemLighting();int cases=0;
            for(int roll=0;roll<3;roll++)for(int i=0;i<5;i++){
                ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_STAIRS);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("StairTread",1<<i);tag.setInteger("StairRoll",roll);stack.setTagInfo("BlockEntityTag",tag);
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack,20+i*40,16+roll*32);
            }
            RenderHelper.disableStandardItemLighting();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(256,128,frame);
            int background=image.getRGB(0,0);
            for(int roll=0;roll<3;roll++)for(int i=0;i<5;i++){
                int ink=0;for(int x=0;x<16;x++)for(int y=0;y<16;y++)if(image.getRGB(20+i*40+x,16+roll*32+y)!=background){ink++;if(x==0||y==0||x==15||y==15)throw new IllegalStateException("Stair icon touches slot edge tread="+(1<<i)+" roll="+roll+" x="+x+" y="+y);}
                if(ink<20)throw new IllegalStateException("Empty stair icon");cases++;
            }
            javax.imageio.ImageIO.write(image,"png",new java.io.File(output,"stair-step-orientation-icons.png"));System.out.println("[vandorlabs][reprolab] stair-icons PASS padded variants="+cases);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.enableCull();}
    }
}
