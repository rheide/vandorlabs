package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.credentials.NativeReaderAssets;
import java.security.MessageDigest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.*;

/** Actual reader model bake and uploaded standard frame/logo pixels, without world activation. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeReaderResourceChecks {
 private static int ticks;private static boolean done;
 private static void need(boolean value,String label){if(!value)throw new AssertionError("Reader resource: "+label);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!"true".equals(System.getenv("VANDOR_READER_RESOURCE_CHECK"))||done)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
  if(++ticks>2400)throw new AssertionError("Reader title timeout screen="+mc.screen+" overlay="+mc.getOverlay());if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen))return;
  need(mc.level==null&&mc.getSingleplayerServer()==null,"no world/server");int models=0,quads=0,sprites=0;var random=RandomSource.create(0);
  for(String id:NativeReaderAssets.models()){var model=mc.getModelManager().getModel(ModelResourceLocation.standalone(ResourceLocation.parse(id)));int count=model.getQuads(null,null,random).size();for(Direction side:Direction.values())count+=model.getQuads(null,side,random).size();need(count==NativeReaderAssets.faces(id),"authored face count "+id+" got="+count);models++;quads+=count;}
  int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),framebuffer=GL30.glGenFramebuffers();
  try{
   GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer);var atlas=mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,atlas.getId(),0);need(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"read atlas attachment");GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
   for(var descriptor:NativeReaderAssets.textures()){
    var id=ResourceLocation.parse(descriptor.id());var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);var image=sprite.contents().getOriginalImage();need(sprite.contents().name().equals(id)&&image.getWidth()==descriptor.size()&&image.getHeight()==descriptor.size(),"real sprite "+id+" got="+sprite.contents().name()+" image="+image.getWidth()+"x"+image.getHeight()+" expected="+descriptor.size());
    MessageDigest hash=MessageDigest.getInstance("SHA-256");var pixels=org.lwjgl.BufferUtils.createByteBuffer(descriptor.size()*descriptor.size()*4);GL11.glReadPixels(sprite.getX(),sprite.getY(),descriptor.size(),descriptor.size(),GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int pixel=image.getPixelRGBA(x,y),at=(y*image.getWidth()+x)*4;for(int c=0;c<4;c++){int value=(pixel>>>(c*8))&255;hash.update((byte)value);need((pixels.get(at+c)&255)==value,"exact uploaded pixel "+id+" "+x+","+y+" channel="+c);}}
    String digest=java.util.HexFormat.of().formatHex(hash.digest());need(digest.equals(descriptor.rgbaSha256()),"standard source RGBA "+id);sprites++;
   }
  }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);GL30.glDeleteFramebuffers(framebuffer);}
  need(models==24&&sprites==8&&quads==576,"complete authored inventory");System.out.println("[vandorlabs][reader-resources] title PASS models=24 sprites=8 quads=576 actual authored bake and exact GPU source RGBA; no world/server or placed-reader claim");done=true;mc.stop();
 }
}
