package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.neoforge.shape.PanelPlayback;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.screen.*;
import java.util.*;
import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;

/** Actual native font and row buffers, independently of the inactive block registry. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeScreenRowsRenderChecks {
 private static final class Gallery extends net.minecraft.client.gui.screens.Screen {
  Gallery(){super(net.minecraft.network.chat.Component.literal("Screen row fixture"));}
  @Override public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float tick){}
 }
 private static final class Discard implements VertexConsumer {
  public VertexConsumer addVertex(float x,float y,float z){return this;}public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
 }
 private static final ScreenFamily.State STATE=new ScreenFamily.State(2,false,false,false);
 private static NativeScreenRowsRenderer renderer;private static NativeScreenEntity.Visual[] scenes;private static boolean capture,done;private static int frames,generation;
 private static void need(boolean yes,String why){if(!yes)throw new AssertionError("Screen rows: "+why);}
 private static NativeScreenEntity.Visual visual(ScreenFamily family,int variant){
  var catalog=NativeScreenCatalog.INSTANCE;var tag=NativeScreenFactory.choices(family,catalog,catalog.defaultHousing(),catalog.defaultFrame());var settings=NativeScreenSettings.read(tag,catalog);var frame=FrameSettings.read(new ModernHousingTags(tag),catalog::validFrame,catalog::validFrameSurface,catalog::validMaterial);var rows=new ArrayList<NativeScreenControls.Row>();
  for(int i=0;i<family.standaloneControlRows(0);i++)rows.add(new NativeScreenControls.Row(i==0?"POWER":i==1?"THROTTLE":"ROW "+(i+1),ChannelList.EMPTY,i%3==1,i%3==2,3,15,variant==0?7:15,Map.of(),ChannelList.EMPTY,variant!=0,variant==0?0:120));
  var controls=List.of(new NativeScreenControls(true,variant==0?"REDSTONE CONTROL":"CONFIGURED ROWS",rows),new NativeScreenControls(false,"",List.of()));var appearance=NativeScreenAppearance.of(family,settings,frame,controls,0,catalog);return new NativeScreenEntity.Visual(family,settings,frame,PanelPlayback.DEFAULT,controls,0,0,false,1,appearance);
 }
 private static ScreenSurface.Quad surface(NativeScreenEntity.Visual visual){var full=ScreenSurface.quad(ScreenSurface.Kind.FLAT,false);return visual.family().half(0)?ScreenSurface.half(full,true):full;}
 private static void emit(NativeScreenEntity.Visual visual,PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers){renderer.emit(NativeScreenRenderPlan.Binding.ROWS,visual,STATE,0,surface(visual),IDENTITY,poses,buffers,0xf000f0,0,112);}
 private static final Matrix4f IDENTITY=new Matrix4f();
 private static void prepare(){renderer=new NativeScreenRowsRenderer(Minecraft.getInstance().font);scenes=new NativeScreenEntity.Visual[14];for(int i=0;i<14;i++)scenes[i]=visual(ScreenFamily.values()[i%7],i/7);var discard=new Discard();net.minecraft.client.renderer.MultiBufferSource buffers=layer->discard;var pose=new PoseStack();for(int i=0;i<30000;i++)emit(scenes[0],pose,buffers);long builds=renderer.builds();var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);long before=bean.getThreadAllocatedBytes(Thread.currentThread().threadId());for(int i=0;i<10000;i++)emit(scenes[0],pose,buffers);long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-before;need(allocated<=4096,"warm 10000 row draws bytes="+allocated);need(renderer.builds()==builds,"warm draw rebuilt fonts");need(renderer.retainedBytes()>0&&renderer.retainedBytes()<4*1024*1024+512*1024,"bounded cache");System.out.println("[vandorlabs][screen-rows-render] warm PASS 10000 draws bytes="+allocated+" builds="+builds+" retained="+renderer.retainedBytes());}
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(done||!"true".equals(System.getenv("VANDOR_SCREEN_ROWS_RENDER_CHECK")))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen){mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(renderer==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);var buffers=mc.renderBuffers().bufferSource();if(frames>=4)for(int i=0;i<14;i++){var poses=gui.pose();poses.pushPose();try{poses.translate(10+(i%7)*90+80,10+(i/7)*170+128,350);poses.scale(-5,-8,-1);emit(scenes[i],poses,buffers);}finally{poses.popPose();}buffers.endBatch();gui.flush();}capture=++frames==4||frames==9;}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{if(!capture||done)return;capture=false;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){double scale=mc.getWindow().getGuiScale();int[] ink=new int[14],glyphs=new int[14];for(int i=0;i<14;i++){int x0=(int)((10+(i%7)*90)*scale),y0=(int)((10+(i/7)*170)*scale);for(int y=0;y<(int)(128*scale);y++)for(int x=0;x<(int)(80*scale);x++){int rgb=image.getPixelRGBA(x0+x,y0+y)&0xffffff;if(rgb!=0x302820)ink[i]++;if(rgb==0xd8c8b8||rgb==0xf0ffde||rgb==0xffeec8||rgb==0xefe8e0)glyphs[i]++;}need(frames==4?ink[i]==0:ink[i]>500,"scene="+i+" frame="+frames+" ink="+ink[i]);}if(frames==9)for(int i=0;i<14;i++)need(glyphs[i]>20,"visible labels scene="+i+" glyph pixels="+glyphs[i]);var path=mc.gameDirectory.toPath().resolve("screenshots/screen-rows-"+generation+"-"+(frames==4?"disabled":"positive")+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);if(frames==9){if(generation++==0){renderer.clear();need(renderer.retainedBytes()==0,"clear retires retained font data");renderer=null;frames=0;mc.reloadResourcePacks();}else{done=true;System.out.println("[vandorlabs][screen-rows-render] title PASS 14 scenes/all seven role capacities/native glyphs/negative empty/resource reload");mc.stop();}}}}
}
