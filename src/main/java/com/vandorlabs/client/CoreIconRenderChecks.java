package com.vandorlabs.client;

import com.vandorlabs.geometry.CoreMesh;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Actual item-renderer coverage for every registered Source Core, including projected icon padding. */
final class CoreIconRenderChecks {
    static void capture(Minecraft mc,File directory)throws Exception{capture(mc,directory,4);capture(mc,directory,1);}
    private static void capture(Minecraft mc,File directory,int scale)throws Exception{
        int cell=20*scale,height=20*scale,width=CoreMesh.IDS.size()*cell;Framebuffer target=new Framebuffer(width,height,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width/(double)scale,20,0,-1000,1000);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(13/255F,17/255F,24/255F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);RenderHelper.enableGUIStandardItemLighting();
            for(int i=0;i<CoreMesh.IDS.size();i++){
                String id=CoreMesh.IDS.get(i),registry="vandorlabs:"+com.vandorlabs.blocks.FrameMachineDesign.get(id).block;
                net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName(registry);
                need(block instanceof com.vandorlabs.blocks.BlockConstructorCore&&((com.vandorlabs.blocks.BlockConstructorCore)block).design.equals(id),"registered Core block for design "+id+" registry="+registry);
                ItemStack stack=new ItemStack(block);need(!stack.isEmpty()&&stack.getItem()!=net.minecraft.init.Items.AIR,"registered nonempty Core item for design "+id+" registry="+registry);
                need(mc.getRenderItem().getItemModelWithOverrides(stack,null,null).getQuads(null,null,0).size()==CoreMesh.faces(id).size(),"actual item uses complete shared Core scaffold "+registry);
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack,i*20+2,2);
            }
            GL11.glFinish();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(width,height,target);javax.imageio.ImageIO.write(image,"png",new File(directory,"core-icons-all13-"+scale+"x.png"));
            for(int i=0;i<CoreMesh.IDS.size();i++){int count=0,minX=cell,maxX=-1,minY=height,maxY=-1;for(int y=0;y<height;y++)for(int x=0;x<cell;x++)if((image.getRGB(i*cell+x,y)&0xffffff)!=0x0d1118){count++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}need(count>4*scale*scale,"nonempty Core icon "+i);need(minX>=3*scale&&maxX<17*scale&&minY>=3*scale&&maxY<17*scale,"all sides retain projected inventory padding "+i+" bounds="+minX+","+minY+".."+maxX+","+maxY);need(maxX-minX+1>11*scale,"recognizable inventory-width Core "+i+" scale="+scale+" width="+(maxX-minX+1));need(count<(maxX-minX+1)*(maxY-minY+1)*.7,"actual hollow scaffold gaps remain visible "+i);}
            need(GL11.glGetError()==GL11.GL_NO_ERROR,"Core item renderer GL state");System.out.println("[vandorlabs] core-icons PASS scale="+scale+" all13 actual ItemBlock rendering projected padding and negative space");
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void need(boolean value,String text){if(!value)throw new IllegalStateException(text);}
}
