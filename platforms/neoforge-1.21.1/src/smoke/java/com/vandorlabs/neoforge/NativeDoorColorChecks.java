package com.vandorlabs.neoforge;
import net.minecraft.client.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeDoorColorChecks {
 private static int frames;private static boolean capture,done;
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!"true".equals(System.getenv("VANDOR_DOOR_COLOR_CHECK"))||done||!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;var graphics=event.getGuiGraphics();graphics.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);graphics.flush();var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));if(!sprite.contents().name().getPath().equals("block/white_concrete"))throw new AssertionError("Actual color-control sprite");var buffers=mc.renderBuffers().bufferSource();
  for(int patch=0;patch<2;patch++){int[] data=new int[32];float[][] points={{0,0,.5F},{1,0,.5F},{1,1,.5F},{0,1,.5F}};for(int v=0;v<4;v++){for(int a=0;a<3;a++)data[v*8+a]=Float.floatToRawIntBits(points[v][a]);data[v*8+3]=patch==0?0xffe06020:0xff2070e0;data[v*8+4]=Float.floatToRawIntBits(v==1||v==2?sprite.getU1():sprite.getU0());data[v*8+5]=Float.floatToRawIntBits(v<2?sprite.getV1():sprite.getV0());data[v*8+7]=127<<16;}var mesh=NativeDoorMesh.prepareQuads(java.util.List.of(new BakedQuad(data,-1,Direction.SOUTH,sprite,false,false)),false);var poses=graphics.pose();poses.pushPose();try{poses.translate(50+patch*180,180,300);poses.scale(100,-100,-100);mesh.emit(poses.last(),buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType()),Direction.SOUTH,0,0xf000f0,OverlayTexture.NO_OVERLAY,0,0,0,0,0,1,0,1,false);}finally{poses.popPose();}}
  buffers.endBatch();graphics.flush();capture=++frames>=10;
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{if(!capture||done)return;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/door-color-native.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);double scale=mc.getWindow().getGuiScale();for(int patch=0;patch<2;patch++){int color=image.getPixelRGBA((int)((100+patch*180)*scale),(int)(130*scale)),r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;if(patch==0?b<r*2:r<b*2)throw new AssertionError("Door ABGR source semantic mismatch patch="+patch+" actualRGB="+r+","+g+","+b);}}System.out.println("[vandorlabs][door-color] title PASS asymmetric packed ABGR blue/orange source records become correct GPU RGB; no world/server");done=true;mc.stop();}
}
