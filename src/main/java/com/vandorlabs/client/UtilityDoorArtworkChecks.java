package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Actual atlas pixels, dedicated picker boundaries and unchanged ordinary/Large leaf emitters. */
final class UtilityDoorArtworkChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Utility Door art: "+why);}
 static void run(Minecraft mc,File output)throws Exception{
  int choices=0,scenes=0;for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)if(ScreenHousingTextures.IDS[choice].startsWith("utility_door_")){
   choices++;need(ScreenHousingTextures.category(choice).equals("Doors/Utility")&&!ScreenHousingTextures.generalTexture(choice),"dedicated category/general exclusion");need(ScreenHousingTextures.doorArtworkMirror(choice,true)&&!ScreenHousingTextures.doorArtworkMirror(choice,false),"paired right-leaf artwork orientation");
   for(boolean large:new boolean[]{false,true})for(int detail=0;detail<2;detail++){
    HousingTextureList picker=HousingTextureList.forDoors(detail,20,40,220,-1,large,true);java.lang.reflect.Field field=HousingTextureList.class.getDeclaredField("options");field.setAccessible(true);need(((Map<?,?>)field.get(picker)).containsKey(choice),"ordinary/Large picker eligibility");picker.searchClick(25,47,0);for(char c:"doors/utility".toCharArray())picker.searchKey(c,0);java.lang.reflect.Field rows=HousingTextureList.class.getDeclaredField("rows");rows.setAccessible(true);need(((List<?>)rows.get(picker)).size()==12,"actual search isolates Utility category");need(picker.click(35,73,0)&&picker.selected()>=0&&ScreenHousingTextures.category(picker.selected()).equals("Doors/Utility"),"actual category-search row click");
   }
   com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);BufferedImage source;try(InputStream input=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs","textures/blocks/"+entry.get("source").getAsString()+".png")).getInputStream()){source=ImageIO.read(input);}need(source.getWidth()==128&&source.getHeight()==256,"native supplied resolution");TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));need(UnifiedTextureSprites.contentWidth(sprite)==128&&UnifiedTextureSprites.contentHeight(sprite)==256,"full artwork retained in square atlas");pixels(mc,sprite,source);
   for(boolean large:new boolean[]{false,true}){DoorAppearanceRuntimeChecks.Fixture fixture=new DoorAppearanceRuntimeChecks.Fixture(mc,large,2,!large);try{BufferedImage baseline=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,0,false);fixture.tile.setFaceTexture(choice);fixture.tile.setTextureLayout(0);BufferedImage selected=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,0,false);int changed=0;for(int y=0;y<384;y++)for(int x=0;x<384;x++)if(baseline.getRGB(x,y)!=selected.getRGB(x,y))changed++;need(changed>1000,"actual ordinary/Large selected leaf pixels");int asymmetric=0,total=0;for(int y=40;y<344;y++)for(int x=40;x<174;x++){int a=selected.getRGB(x,y),b=selected.getRGB(383-x,y);if(Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255))>30)asymmetric++;total++;}need(asymmetric<total/15,"actual paired/Large mirrored pixels "+asymmetric+"/"+total);ImageIO.write(selected,"png",new File(output,ScreenHousingTextures.IDS[choice]+(large?"-large":"-ordinary")+".png"));scenes++;if(ScreenHousingTextures.reactorArtwork(choice)){need(fixture.tile.hasGlass()&&fixture.tile.supportsGlassAppearance(),"Reactor advertises glass editor and pane pass");DoorGlassAppearance glassFinish=new DoorGlassAppearance(-1,14,3,1);fixture.tile.setGlassAppearance(glassFinish);BufferedImage tinted=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,0,false);int tintedPixels=0;for(int y=0;y<384;y++)for(int x=0;x<384;x++)if(selected.getRGB(x,y)!=tinted.getRGB(x,y))tintedPixels++;need(tintedPixels>500&&fixture.tile.opaqueGlass(),"actual selectable Reactor glass owns opaque depth");TileEntitySpaceDoor restored=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();restored.applyItemSettings(fixture.tile.itemSettings());need(restored.supportsGlassAppearance()&&restored.glassAppearance().equals(glassFinish),"Reactor configured-item glass roundtrip");ImageIO.write(tinted,"png",new File(output,large?"reactor-glass-large.png":"reactor-glass-ordinary.png"));}}finally{fixture.close();}}
  }
  int glass=0;for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++){com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);if(e!=null&&e.has("design")&&(e.get("design").getAsInt()==15||e.get("design").getAsInt()==16||e.get("design").getAsInt()==29)){glass++;need(ScreenHousingTextures.category(choice).equals("Doors/Glass")&&!ScreenHousingTextures.generalTexture(choice),"retained Glass IDs dedicated category");}}
  need(choices==12&&glass==8,"complete Utility/Glass inventory");System.out.println("[vandorlabs][reprolab] utility-door-artwork PASS choices=12 glass8 scenes="+scenes+" gpuRGBAwords=393216 checks="+checks+" full128x256/paddedatlas/ordinaryLarge/searchClick/mirroredPolicy");
 }
 private static void pixels(Minecraft mc,TextureAtlasSprite sprite,BufferedImage source){
  int width=128,height=256;Framebuffer target=new Framebuffer(width,height,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width,height,0,-1,1);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState saved=new TurntableRenderState()){
   target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.disableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableBlend();GlStateManager.disableAlpha();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);b.pos(0,height,0).tex(sprite.getMinU(),sprite.getMaxV()).endVertex();b.pos(width,height,0).tex(sprite.getMaxU(),sprite.getMaxV()).endVertex();b.pos(width,0,0).tex(sprite.getMaxU(),sprite.getMinV()).endVertex();b.pos(0,0,0).tex(sprite.getMinU(),sprite.getMinV()).endVertex();Tessellator.getInstance().draw();java.nio.ByteBuffer bytes=BufferUtils.createByteBuffer(width*height*4);GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int at=((height-1-y)*width+x)*4,pixel=source.getRGB(x,y);for(int component=0;component<4;component++){int shift=component==3?24:16-component*8;need(Math.abs((bytes.get(at+component)&255)-(pixel>>>shift&255))<=1,"actual full-leaf GPU texel "+sprite.getIconName()+" "+x+","+y+" component="+component);}}
  }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
 private UtilityDoorArtworkChecks(){}
}
