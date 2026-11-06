package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Asserted actual frames, rejection X/indicators and credential rendering without world activation. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeReaderRenderChecks {
 private static int frames;private static boolean drawn,done;private static NativeReaderRenderer.Bake bake;
 private static void need(boolean value,String label){if(!value)throw new AssertionError("Reader rendering: "+label);}
 @SubscribeEvent public static void screen(ScreenEvent.Render.Post event){
  if(!"true".equals(System.getenv("VANDOR_READER_RENDER_CHECK"))||done)return;var mc=Minecraft.getInstance();if(!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen)||mc.getOverlay()!=null)return;
  need(mc.level==null&&mc.getSingleplayerServer()==null,"title only");if(bake==null)bake=new NativeReaderRenderer.Bake(id->mc.getModelManager().getModel(ModelResourceLocation.standalone(ResourceLocation.parse(id))));
  var graphics=event.getGuiGraphics();graphics.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);graphics.flush();var buffers=mc.renderBuffers().bufferSource();
  for(int row=0;row<2;row++)for(int column=0;column<4;column++){
   var poses=graphics.pose();poses.pushPose();try{
    poses.translate(20+column*150,30+row*150+100,200);poses.scale(100,-100,-100);
    var target=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));bake.frame(row!=0,Direction.NORTH,0,poses.last(),target,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,false);bake.frame(row!=0,Direction.NORTH,0,poses.last(),target,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,true);
    var visual=new NativeTokenReader.Visual(row!=0,1,1,column>=2,column>=2?15:0,column>=2);var motion=new NativeTokenReader.Motion(1,column==2?0:16,column==3,0,0);
    NativeReaderRenderer.contents(visual,motion,motion.age(),column==1,Direction.NORTH,0,poses,buffers,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
   }finally{poses.popPose();}
  }buffers.endBatch();graphics.flush();drawn=++frames>=10;
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{
  if(!drawn||done)return;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
   var path=mc.gameDirectory.toPath().resolve("screenshots/reader-native.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);
  double scale=mc.getWindow().getGuiScale();int cells=0;for(int row=0;row<2;row++)for(int column=0;column<4;column++){
    int amber=0,cyan=0,dark=0,changed=0;
    for(int y=(int)((30+row*150)*scale);y<(int)((130+row*150)*scale);y++)for(int x=(int)((20+column*150)*scale);x<(int)((120+column*150)*scale);x++){
     int rgba=image.getPixelRGBA(x,y),rgb=rgba&0xffffff;if(rgb==0x40b3ff)amber++;if(rgb==0xffff5c)cyan++;if(rgb==0x76681c)dark++;if(rgb!=0x302820)changed++;
    }
    need(changed>500,"authored visible frame cell="+cells+" pixels="+changed);if(column==1)need(amber>20&&dark>2,"rejected X and off strip cell="+cells+" amber="+amber+" dark="+dark);if(column==0)need(dark>2,"empty reader off strip cell="+cells+" dark="+dark);if(column>=2){int itemPixels=0;double top=30+row*150+(1-(.5+(row==0?4.4:3)/16))*100;for(int y=(int)(row*150*scale);y<(int)(top*scale);y++)for(int x=(int)((20+column*150+35)*scale);x<(int)((20+column*150+65)*scale);x++){int pixel=image.getPixelRGBA(x,y),r=pixel&255,g=(pixel>>>8)&255,b=(pixel>>>16)&255;if(r>g+5&&g>b+5)itemPixels++;}need(itemPixels>10,"canonical gold credential visible cell="+cells+" pixels="+itemPixels);}
    if(column>=2)need(cyan>2,"occupied active indicator cell="+cells+" cyan="+cyan);cells++;
   }
   need(bake.retainedBytes()<512*1024,"bounded exact reader frame bake");System.out.println("[vandorlabs][reader-render] title PASS cells="+cells+" actual card/chip frames, amber rejection X, empty/off and active indicators, inserted/returning canonical credential items; retainedBytes="+bake.retainedBytes()+" screenshot=reader-native.png; no world/server");
  }done=true;mc.stop();
 }
}
