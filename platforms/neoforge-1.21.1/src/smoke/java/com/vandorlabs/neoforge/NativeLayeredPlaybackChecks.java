package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.screen.NativeScreenCatalog;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.client.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Narrow actual native pane-consumer regression for the shipped straight-alpha company animation. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeLayeredPlaybackChecks {
 private static NativeFlatWallModel.Bake.PaneProjection pane;private static int frames;private static boolean capture,done;
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Layered playback GPU: "+message);}
 private static void prepare(){var bake=NativePanelPlaybackRenderer.currentBake();need(bake!=null,"actual material bake");int choice=NativeScreenCatalog.INSTANCE.sourceChoice("console_inputs/company_seyr_half_communications_static");var base=new ProgrammableShapeEntity.Appearance(new ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,new StairState(8,0));var appearance=new NativeWallEntity.WallAppearance(base,new WallSettings(false,false,0,0,true,0),new FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false),new PanelGlassSettings(2,choice,3,false,1,true,true),NativeFlatWallGeometry.straight(Direction.NORTH,1),NativePortholeLayout.unit(0),new PanelPlayback(2,0));pane=bake.paneProjection(appearance);need(!pane.quads().isEmpty()&&pane.material()!=null&&pane.material().frameCount()==6,"real company layered animation has six frames");}
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(done||!"true".equals(System.getenv("VANDOR_LAYERED_PLAYBACK_CHECK"))||!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(pane==null)prepare();var gui=event.getGuiGraphics();gui.flush();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);var buffers=mc.renderBuffers().bufferSource();for(int i=0;i<2;i++){var pose=gui.pose();pose.pushPose();try{pose.translate(100+i*230,130,350);pose.scale(175,-175,175);pose.translate(-.5,-.5,-.5);NativePanelPlaybackRenderer.emit(pane.quads(),pane.source(),pane.material(),new PanelPlayback(2,0),i*60L,pose.last(),buffers.getBuffer(pane.renderType(2)),0xF000F0,OverlayTexture.NO_OVERLAY);}finally{pose.popPose();}buffers.endBatch();}gui.flush();capture=++frames>=8;}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{if(!capture||done)return;done=true;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){double scale=mc.getWindow().getGuiScale();int x0=(int)(12*scale),y0=(int)(42*scale),width=(int)(176*scale),height=(int)(176*scale),shift=(int)(230*scale),changed=0,ink=0;for(int y=0;y<height;y++)for(int x=0;x<width;x++){int a=image.getPixelRGBA(x0+x,y0+y),b=image.getPixelRGBA(x0+x+shift,y0+y);if(a!=b)changed++;if((a&0xffffff)!=0x302820)ink++;}need(ink>100&&changed>20,"actual frame selection changes visible pixels ink="+ink+" changed="+changed);var path=mc.gameDirectory.toPath().resolve("screenshots/layered-playback.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);System.out.println("[vandorlabs][layered-playback] title PASS company6frames actual pane consumer/shader; changedPixels="+changed+" visiblePixels="+ink);}mc.stop();}
}
