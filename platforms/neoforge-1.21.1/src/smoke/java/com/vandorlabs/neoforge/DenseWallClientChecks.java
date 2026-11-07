package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.NativeWallEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Actual per-owner BER preparation cost; reflection observes the production atlas bake only. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class DenseWallClientChecks {
 private static int stage,ticks,settle;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event)throws Exception{
  if(!DenseWallFixture.enabled()||stage==99)return;var mc=net.minecraft.client.Minecraft.getInstance();
  if(stage==90&&mc.level==null){System.out.println("[vandorlabs][dense-wall] client PASS actual1024owners/preparation/emission");stage=99;mc.stop();return;}
  if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
  if(mc.level==null||mc.player==null)return;if(++ticks>1800)throw new AssertionError("Dense wall client timeout stage="+stage+" pos="+mc.player.position());if(mc.screen!=null)return;
  for(int role=0;role<2;role++)for(int i=0;i<512;i++)if(!(mc.level.getBlockEntity(DenseWallFixture.pos(role,i)) instanceof NativeWallEntity))return;
  if(++settle<80)return;
  var field=NativePanelPlaybackRenderer.class.getDeclaredField("active");field.setAccessible(true);var active=field.get(null);if(active==null)return;
  var accessor=active.getClass().getDeclaredMethod("bake");accessor.setAccessible(true);var bake=(NativeFlatWallModel.Bake)accessor.invoke(active);
  for(int role=0;role<2;role++)measure(mc,bake,role);
  mc.disconnect();stage=90;
 }
 private static void measure(net.minecraft.client.Minecraft mc,NativeFlatWallModel.Bake bake,int role)throws Exception{
  NativeWallEntity[] owners=new NativeWallEntity[512];NativeWallEntity.WallAppearance[] views=new NativeWallEntity.WallAppearance[512];
  for(int i=0;i<views.length;i++){owners[i]=(NativeWallEntity)mc.level.getBlockEntity(DenseWallFixture.pos(role,i));views[i]=owners[i].wallAppearance();}
  Set<Object> panes=Collections.newSetFromMap(new IdentityHashMap<>()),bodies=Collections.newSetFromMap(new IdentityHashMap<>());
  for(var view:views){panes.add(bake.paneProjection(view));if(!view.chunkBody())bodies.add(bake.bodyProjection(view));}
  for(int pass=0;pass<16;pass++)for(var view:views){bake.paneProjection(view);if(!view.chunkBody())bake.bodyProjection(view);}
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
  for(int pass=0;pass<4;pass++)for(var view:views){bake.paneProjection(view);if(!view.chunkBody())bake.bodyProjection(view);}
  long bytes=bean.getThreadAllocatedBytes(thread)-before;
  var renderer=new NativePanelPlaybackRenderer();var pose=new com.mojang.blaze3d.vertex.PoseStack();var discard=new Discard();net.minecraft.client.renderer.MultiBufferSource buffers=layer->discard;
  for(int pass=0;pass<8;pass++)for(int i=0;i<512;i++)renderer.render(owners[i],0,pose,buffers,15728880,0);
  discard.vertices=0;before=bean.getThreadAllocatedBytes(thread);
  for(int i=0;i<512;i++)renderer.render(owners[i],0,pose,buffers,15728880,0);
  long emittedBytes=bean.getThreadAllocatedBytes(thread)-before;
  var bodyField=NativeFlatWallModel.Bake.class.getDeclaredField("bodyBytes");bodyField.setAccessible(true);var paneField=NativeFlatWallModel.Bake.class.getDeclaredField("playbackBytes");paneField.setAccessible(true);
  System.out.println("[vandorlabs][dense-wall] role="+DenseWallFixture.IDS[role]+" owners=512 paneProjections="+panes.size()+" bodyLists="+bodies.size()+" bodyBytes="+bodyField.getInt(bake)+" paneBytes="+paneField.getInt(bake)+" warm2048Bytes="+bytes+" emittedVertices="+discard.vertices+" fullRender512Bytes="+emittedBytes);
 }
 private static final class Discard implements com.mojang.blaze3d.vertex.VertexConsumer {
  long vertices;public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z){vertices++;return this;}public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z){return this;}
 }
 private DenseWallClientChecks(){}
}
