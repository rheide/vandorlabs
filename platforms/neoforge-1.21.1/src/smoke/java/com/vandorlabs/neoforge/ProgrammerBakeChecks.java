package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.programmer.NativeProgrammerAssets;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.NativePanelPlaybackRenderer;
import com.vandorlabs.neoforge.programmer.client.*;
import com.vandorlabs.programmer.ProgrammerStatus;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;
/** Actual native OBJ bake and casing GPU pass, separate from player/world acceptance. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ProgrammerBakeChecks {
 private static final class Gallery extends Screen{Gallery(){super(net.minecraft.network.chat.Component.literal("Programmer authored casing"));}@Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float partial){}}
 private static NativeProgrammerBake bake;private static int frames,generation,checks;private static boolean capture,done;private static java.util.concurrent.CompletableFuture<Void> reload;
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Programmer bake: "+why);}
 private static final VertexConsumer DISCARD=new VertexConsumer(){public VertexConsumer addVertex(float x,float y,float z){return this;}public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}};
 private static void prepare(){var mc=Minecraft.getInstance();var models=new HashMap<ModelResourceLocation,BakedModel>();for(String id:NativeProgrammerAssets.models()){var key=ModelResourceLocation.standalone(ResourceLocation.parse(id));var model=mc.getModelManager().getModel(key);need(model!=mc.getModelManager().getMissingModel(),"actual model resolved "+id);models.put(key,model);}bake=new NativeProgrammerBake(models,m->mc.getTextureAtlas(m.atlasLocation()).apply(m.texture()),mc.getResourceManager());int quads=0;for(String id:NativeProgrammerAssets.models()){var rows=bake.quads(id,null,RandomSource.create(0),ModelData.EMPTY,null);need(rows.size()==NativeProgrammerAssets.model(id).geometry().size(),"actual authored face count "+id);for(var q:rows)need(!q.getSprite().contents().name().getPath().equals("missingno"),"authored sprite resolved");quads+=rows.size();}
  var poses=new PoseStack();for(int i=0;i<12000;i++)NativeProgrammerMesh.emit(bake,ProgrammerStatus.READY,Direction.NORTH,0,poses.last(),DISCARD,0,0,0,0xF000F0,OverlayTexture.NO_OVERLAY,1);var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)NativeProgrammerMesh.emit(bake,ProgrammerStatus.READY,Direction.NORTH,0,poses.last(),DISCARD,0,0,0,0xF000F0,OverlayTexture.NO_OVERLAY,1);long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes<=1024,"warm mesh allocation="+bytes);System.out.println("[vandorlabs][programmer-bake] generation="+generation+" models="+models.size()+" quads="+quads+" retained="+bake.retainedBytes()+" warm10000Bytes="+bytes);
 }
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!Boolean.getBoolean("vandorlabs.programmerRenderCheck")||done)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof AccessibilityOnboardingScreen intro){intro.onClose();return;}if(event.getScreen() instanceof TitleScreen){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}if(bake==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);if(frames>=4){var buffers=mc.renderBuffers().bufferSource();for(int i=0;i<16;i++){var poses=gui.pose();poses.pushPose();try{poses.translate(80+(i%4)*150,70+(i/4)*70,200);poses.scale(32,-32,-32);poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(18));poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(25));poses.translate(-1,-1,-.5);NativeProgrammerMesh.emit(bake,ProgrammerStatus.values()[i/4],Direction.from2DDataValue(i%4),0,poses.last(),buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType()),0,0,0,0xF000F0,OverlayTexture.NO_OVERLAY,1);}finally{poses.popPose();}}buffers.endBatch();gui.flush();}frames++;capture=frames==4||frames==9;}
 @SubscribeEvent public static void captured(RenderFrameEvent.Post event)throws Exception{if(!capture||done)return;capture=false;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/programmer-casing-"+generation+"-"+(frames==4?"disabled":"positive")+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);double scale=mc.getWindow().getGuiScale();for(int i=0;i<16;i++){int ink=0;for(int y=0;y<66*scale;y++)for(int x=0;x<140*scale;x++){int px=(int)((10+i%4*150)*scale)+x,py=(int)((34+i/4*70)*scale)+y;if((image.getPixelRGBA(px,py)&0xffffff)!=0x302820)ink++;}need(frames==4?ink==0:ink>200,"render scene="+i+" generation="+generation+" ink="+ink);}if(frames==9){if(generation++==0){bake=null;frames=0;reload=mc.reloadResourcePacks();}else{done=true;System.out.println("[vandorlabs][programmer-bake] title PASS checks="+checks+" nativeOBJ16scenes/reload/disablednegative; no world or socket claim");mc.stop();}}}}
}
