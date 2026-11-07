package com.vandorlabs.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Fixed GUI projection of actual canonical owners; no animated title-screen background. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class DenseWallRenderChecks {
 private static int frames,generation;private static Runnable finished;private static int[][] negative,previous;
 private static net.minecraft.world.level.block.entity.BlockEntity[] owners;
 static void begin(int atlas,Runnable completion){
  generation=atlas;frames=0;finished=completion;negative=null;var mc=Minecraft.getInstance();owners=new net.minecraft.world.level.block.entity.BlockEntity[3];
  for(int role=0;role<3;role++){owners[role]=mc.level.getBlockEntity(DenseWallFixture.pos(role,0));if(role<2)for(int i=0;i<512;i++){var tile=(com.vandorlabs.neoforge.shape.NativeWallEntity)mc.level.getBlockEntity(DenseWallFixture.pos(role,i));if(!tile.wallAppearance().chunkBody()){owners[role]=tile;break;}}}
  mc.getToasts().clear();mc.gui.getChat().clearMessages(true);mc.setScreen(new Gallery());
 }
 private static final class Gallery extends Screen {
  Gallery(){super(Component.empty());}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
   var mc=Minecraft.getInstance();mc.getToasts().clear();gui.fill(0,0,width,height,0xff202830);gui.flush();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
   if(frames>=5){var buffers=mc.renderBuffers().bufferSource();var renderer=new NativePanelPlaybackRenderer();
    for(int role=0;role<3;role++){int px=10+role*205;gui.enableScissor(px,16,px+195,186);var pose=gui.pose();pose.pushPose();try{
     pose.translate(px+97,101,350);pose.scale(126,-126,126);pose.mulPose(Axis.XP.rotationDegrees(12));pose.mulPose(Axis.YP.rotationDegrees(-24));pose.translate(-.5,-.5,-.5);
     if(role<2)renderer.render(owners[role],0,pose,buffers,0xF00000,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);else NativeFlatGlassRenderer.renderAt(NativeFlatGlassRenderer.currentBake(),((com.vandorlabs.neoforge.glass.NativeGlassEntity)owners[role]).visual(),pose,buffers,0xF00000,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);buffers.endBatch();
    }finally{pose.popPose();gui.disableScissor();}}
   }frames++;
  }
 }
 @SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event)throws Exception{
  if(!DenseWallFixture.enabled()||finished==null||(frames!=5&&frames!=25))return;var mc=Minecraft.getInstance();
  try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
   int scale=(int)mc.getWindow().getGuiScale(),width=195*scale,height=170*scale;int[][] pixels=new int[3][width*height];
   for(int role=0;role<3;role++)for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[role][y*width+x]=image.getPixelRGBA((10+role*205)*scale+x,16*scale+y);
   if(frames==5){negative=pixels;return;}
   var target=mc.gameDirectory.toPath().resolve("screenshots/dense-wall-"+generation+".png");java.nio.file.Files.createDirectories(target.getParent());image.writeToFile(target);
   String failure=null;int total=0;for(int role=0;role<3;role++){int changed=0,reload=0;for(int i=0;i<pixels[role].length;i++){if(pixels[role][i]!=negative[role][i])changed++;if(generation==1&&pixels[role][i]!=previous[role][i])reload++;}if(changed<100)failure="Disabled-render negative lacks visible canonical owner role="+role+" pixels="+changed;System.out.println("[vandorlabs][dense-wall] scene="+role+" generation="+generation+" changed="+changed+" reloadDelta="+reload);if(reload!=0)failure="Reload changed fixed canonical pixels role="+role+" count="+reload;total+=changed;}
   if(failure!=null){finished=null;throw new AssertionError(failure);}previous=generation==0?pixels:null;System.out.println("[vandorlabs][dense-wall] render generation="+generation+" changedPixels="+total+" exactReload=true actualCanonicalOwners");
  }
  var completion=finished;finished=null;owners=null;completion.run();
 }
 private DenseWallRenderChecks(){}
}
