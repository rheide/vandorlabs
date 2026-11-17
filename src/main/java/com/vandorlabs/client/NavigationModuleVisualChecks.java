package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import java.io.File;
import java.awt.image.BufferedImage;
import org.lwjgl.opengl.GL11;

final class NavigationModuleVisualChecks {
    private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Navigation module visual: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean visible:new boolean[]{false,true})for(double offset:new double[]{0,.5}){
            BufferBuilder b=new BufferBuilder(16384);b.begin(7,DefaultVertexFormats.BLOCK);ModuleRackModel.emitNavigationBay(b,face,0,0,0,visible,offset,15728880);b.finishDrawing();need(b.getVertexCount()>0,"real native bay vertices");java.nio.ByteBuffer bytes=b.getByteBuffer();for(int n=0;n<b.getVertexCount();n++){int at=n*DefaultVertexFormats.BLOCK.getNextOffset();float y=bytes.getFloat(at+4);need(y>.0625&&y<.421875,"all cassette/frame vertices fit both table recesses above foot and below angled top");}
        }
        for(int scale:new int[]{1,4}){
            Framebuffer f=new Framebuffer(20*scale,20*scale,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,20,20,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
            try(TurntableRenderState saved=new TurntableRenderState()){
                f.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(13/255F,17/255F,24/255F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableAlpha();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(com.vandorlabs.items.ModItems.NAVIGATION_DATA_MODULE),2,2);GL11.glFinish();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(20*scale,20*scale,f);int minX=20*scale,minY=minX,maxX=-1,maxY=-1,count=0;
                for(int y=0;y<20*scale;y++)for(int x=0;x<20*scale;x++)if((image.getRGB(x,y)&0xffffff)!=0x0d1118){count++;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
                need(count>16*scale*scale,"nonempty inventory module");need(minX>=3*scale&&minY>=3*scale&&maxX<17*scale&&maxY<17*scale,"inventory icon padding "+minX+","+minY+".."+maxX+","+maxY);javax.imageio.ImageIO.write(image,"png",new File(output,"navigation-module-icon-"+scale+"x.png"));
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        }
        System.out.println("[vandorlabs] navigation-module-visual PASS actual inventory icon padding 1x/4x single-bay frame/cassette four facings seated/extracted both table clearances");
    }
    private NavigationModuleVisualChecks(){}
}
