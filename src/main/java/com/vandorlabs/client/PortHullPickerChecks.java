package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Actual shared picker interaction and uploaded 64x64 Hull artwork. */
final class PortHullPickerChecks {
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Port Hull picker: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        int choice=-1;for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++)if(ScreenHousingTextures.IDS[i].equals("port_hull"))choice=i;
        need(choice>=0&&ScreenHousingTextures.category(choice).equals("Hull")&&ScreenHousingTextures.generalTexture(choice),"append-only generic Hull entry");
        HousingTextureList picker=new HousingTextureList(20,40,220,-1,8);picker.searchClick(25,47,0);for(char c:"resource_ports/port_hull".toCharArray())picker.searchKey(c,0);varFieldCheck(picker,choice);need(picker.click(35,73,0)&&picker.selected()==choice,"real filtered row selects Port Hull");
        java.awt.image.BufferedImage source;try(InputStream input=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs","textures/blocks/resource_ports/port_hull.png")).getInputStream()){source=ImageIO.read(input);}need(source.getWidth()==64&&source.getHeight()==64,"established block resolution");
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));need(sprite!=mc.getTextureMapBlocks().getMissingSprite()&&sprite.getIconWidth()==64&&sprite.getIconHeight()==64,"actual atlas sprite");
        Framebuffer target=new Framebuffer(64,64,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,64,64,0,-1,1);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.disableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableBlend();GlStateManager.disableAlpha();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);b.pos(0,64,0).tex(sprite.getMinU(),sprite.getMaxV()).endVertex();b.pos(64,64,0).tex(sprite.getMaxU(),sprite.getMaxV()).endVertex();b.pos(64,0,0).tex(sprite.getMaxU(),sprite.getMinV()).endVertex();b.pos(0,0,0).tex(sprite.getMinU(),sprite.getMinV()).endVertex();Tessellator.getInstance().draw();java.nio.ByteBuffer pixels=BufferUtils.createByteBuffer(64*64*4);GL11.glReadPixels(0,0,64,64,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);for(int y=0;y<64;y++)for(int x=0;x<64;x++){int at=((63-y)*64+x)*4,pixel=source.getRGB(x,y);for(int c=0;c<4;c++)need(Math.abs((pixels.get(at+c)&255)-(pixel>>>(c==3?24:16-c*8)&255))<=1,"uploaded pixel "+x+","+y+","+c);}
            ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(64,64,target),"png",new File(output,"port-hull-atlas.png"));
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        System.out.println("[vandorlabs][reprolab] port-hull-picker PASS real search/row selection/Hull category and 16384 uploaded GPU components");
    }
    private static void varFieldCheck(HousingTextureList picker,int choice)throws Exception{java.lang.reflect.Field field=HousingTextureList.class.getDeclaredField("rows");field.setAccessible(true);need(((List<?>)field.get(picker)).equals(Collections.singletonList(choice)),"search isolates supplied Hull face");}
    private PortHullPickerChecks(){}
}
