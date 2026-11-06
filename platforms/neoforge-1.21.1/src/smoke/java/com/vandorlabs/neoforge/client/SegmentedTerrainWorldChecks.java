package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.segmented.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual uploaded terrain and BER handoff, isolated from canonical interaction acceptance. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class SegmentedTerrainWorldChecks {
 private static int ticks,stage,wait,checks,frames,heldReceipts;private static long generation;private static String capture;private static boolean captured,hold;private static NativeSegmentedBlockRenderer.Stats before;private static Field renderers;private static Object original;
 @SuppressWarnings("unchecked")private static Collection<NativeSegmentedSectionUploads.Section> tracked(){try{var f=NativeSegmentedSectionUploads.class.getDeclaredField("TRACKED");f.setAccessible(true);return ((Map<NativeSegmentedSectionUploads.Section,?>)f.get(null)).keySet();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private static void receipts(boolean release){try{var f=NativeSegmentedSectionUploads.class.getDeclaredField("holdAcknowledgements");f.setAccessible(true);f.setBoolean(null,!release);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}for(var section:tracked()){if(section.vandorlabs$terrainTask() instanceof NativeSegmentedSectionUploads.Task task&&task.vandorlabs$terrainUploaded()!=null)heldReceipts++;if(release)((net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection)section).setDirty(false);}}
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)public static void delayTick(ClientTickEvent.Post e){if(hold)receipts(false);}
 @SubscribeEvent public static void delayRender(RenderLevelStageEvent e){if(hold&&e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)receipts(false);}
 private static void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Segmented terrain world stage="+stage+" tick="+ticks+" stats="+NativeSegmentedChunks.stats()+": "+why);}
 private static NativeSegmentedDoorEntity tile(Minecraft mc,int x){return mc.level.getBlockEntity(SegmentedTerrainWorldFixture.ROOT.east(x)) instanceof NativeSegmentedDoorEntity t?t:null;}
 private static boolean settled(Minecraft mc){for(int x=0;x<3;x++){var t=tile(mc,x);if(t==null||t.height()!=32||!t.clientTerrain().suppressed()||t.clientTerrain().awaitingChunk())return false;}return true;}
 private static void mode(int n){PacketDistributor.sendToServer(new SegmentedTerrainWorldFixture.Mode(n));wait=ticks;}
 private static void capture(String name){capture=name;captured=false;frames=0;}
 @SuppressWarnings("unchecked")private static void ber(Minecraft mc,boolean enabled){try{if(renderers==null){renderers=net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class.getDeclaredField("renderers");renderers.setAccessible(true);}var d=mc.getBlockEntityRenderDispatcher();if(!enabled){original=renderers.get(d);var next=new HashMap<>((Map<Object,Object>)original);next.remove(NativeSegmentedRegistrations.ENTITY.get());renderers.set(d,next);}else{renderers.set(d,original);original=null;}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!SegmentedTerrainWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(stage==15&&mc.level==null){System.out.println("[vandorlabs][segmented-terrain-world] client PASS checks="+checks+" actual 3x32 cross-section upload/delayed receipts/closed suppression/open removal/reversal/reload/section replacement +128x32 costs; GPU masks separate");mc.stop();stage=16;return;}if(mc.level==null||mc.player==null)return;
  need(++ticks<2600,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" position="+mc.player.position()+" generation="+NativeSegmentedTerrainModel.activeGeneration());var tile=tile(mc,0);
  switch(stage){
   case 0->{if(ticks<80||!settled(mc))return;need(NativeSegmentedChunks.stats().completions()>=3,"real section completion markers acknowledge all columns");mc.options.hideGui=true;mc.options.fov().set(75);mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);before=NativeSegmentedBlockRenderer.stats();wait=ticks;stage=1;}
   case 1->{if(ticks<wait+60)return;var now=NativeSegmentedBlockRenderer.stats();need(now.calls()>before.calls()&&now.bodyDraws()==before.bodyDraws()&&now.lightSamples()==before.lightSamples(),"settled real BER calls emit no bodies or row lights before="+before+" after="+now);capture("terrain-positive.png");stage=2;}
   case 2->{if(!captured)return;ber(mc,false);capture("terrain-without-ber.png");stage=3;}
   case 3->{if(!captured)return;ber(mc,true);hold=true;receipts(false);mode(0);stage=4;}
   case 4->{if(!tile.targetOpen()||hold&&tile.clientTerrain().suppressed())return;if(hold&&ticks<wait+60){need(tile.clientTerrain().awaitingChunk()&&NativeSegmentedBlockRenderer.selectionPose(tile)==0,"delayed upload receipt retains closed fallback and unsampled clock");return;}if(hold){need(heldReceipts>0,"real installed task completion was withheld");hold=false;receipts(true);}if(tile.clientTerrain().awaitingChunk()){need(NativeSegmentedBlockRenderer.selectionPose(tile)==0,"closed fallback while removing terrain");return;}if(NativeSegmentedBlockRenderer.selectionPose(tile)<.08)return;need(!tile.clientTerrain().terrain().body()&&!tile.clientTerrain().suppressed(),"open animation starts only after body removal upload");mode(1);stage=5;}
   case 5->{if(tile.targetOpen()||!settled(mc))return;generation=NativeSegmentedTerrainModel.activeGeneration();mc.reloadResourcePacks();wait=ticks;stage=6;}
   case 6->{if(NativeSegmentedTerrainModel.activeGeneration()<=generation)return;need(tile.clientTerrain().awaitingChunk()||settled(mc),"reload uses closed fallback or already acknowledged terrain");mc.levelRenderer.allChanged();wait=ticks;stage=7;}
   case 7->{if(ticks<wait+60||!settled(mc))return;need(NativeSegmentedSectionUploads.tracked()==0,"reload/cancelled old section publications released");mode(2);stage=8;}
   case 8->{if(!tile.settings().straight||tile.clientTerrain().awaitingChunk()||tile.clientTerrain().terrain().body())return;before=NativeSegmentedBlockRenderer.stats();wait=ticks;stage=9;}
   case 9->{if(ticks<wait+60)return;need(NativeSegmentedBlockRenderer.stats().bodyDraws()>before.bodyDraws()&&NativeSegmentedBlockRenderer.stats().lightSamples()>before.lightSamples(),"ineligible BER negative performs body and row lighting");ber(mc,false);capture("terrain-body-disabled.png");stage=10;}
   case 10->{if(!captured)return;ber(mc,true);mode(3);stage=11;}
   case 11->{if(!settled(mc)||ticks<wait+30)return;need(NativeSegmentedSectionUploads.tracked()==0,"final tracked sections released");mode(4);stage=12;}
   case 12->{if(Math.abs(mc.player.getX()-76.5)>.1)return;for(int c=0;c<128;c++){if(!(mc.level.getBlockEntity(SegmentedTerrainWorldFixture.bank(c)) instanceof NativeSegmentedDoorEntity t)||t.height()!=32||!t.clientTerrain().suppressed()||t.clientTerrain().awaitingChunk())return;}mc.options.fov().set(110);wait=ticks;stage=13;}
   case 13->{if(ticks<wait+60)return;costs(mc);System.out.println("[vandorlabs][segmented-terrain-world] upload stats="+NativeSegmentedChunks.stats()+" renderer="+NativeSegmentedBlockRenderer.stats()+" delayedReceipts="+heldReceipts);mc.level.disconnect();mc.disconnect();stage=15;}
  }
 }
 private static final class Count implements com.mojang.blaze3d.vertex.VertexConsumer {long vertices;public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z){vertices++;return this;}public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v){return this;}public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z){return this;}}
 private static void costs(Minecraft mc){var tiles=new NativeSegmentedDoorEntity[128];var saved=new NativeSegmentedTerrainSnapshot.ClientProjection[128];for(int c=0;c<128;c++){tiles[c]=(NativeSegmentedDoorEntity)mc.level.getBlockEntity(SegmentedTerrainWorldFixture.bank(c));saved[c]=tiles[c].clientTerrain();}
  var sink=new Count();net.minecraft.client.renderer.MultiBufferSource buffers=layer->sink;var poses=new com.mojang.blaze3d.vertex.PoseStack();var renderer=(NativeSegmentedBlockRenderer)mc.getBlockEntityRenderDispatcher().getRenderer(tiles[0]);
  // Restore only the former BER draw state for a measured before control, without changing terrain or server facts.
  for(int c=0;c<128;c++){var p=saved[c];tiles[c].clientTerrain(new NativeSegmentedTerrainSnapshot.ClientProjection(p.terrain(),false,false,true,p.revision()));renderer.render(tiles[c],0,poses,buffers,15728880,0);}
  for(int warm=0;warm<10;warm++)for(var t:tiles)renderer.render(t,0,poses,buffers,15728880,0);
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();
  sink.vertices=0;var b=NativeSegmentedBlockRenderer.stats();long start=bean.getThreadAllocatedBytes(id);for(var t:tiles)renderer.render(t,0,poses,buffers,15728880,0);long beforeAllocated=bean.getThreadAllocatedBytes(id)-start,beforeVertices=sink.vertices;var a=NativeSegmentedBlockRenderer.stats();need(beforeVertices==128L*520&&a.lightSamples()-b.lightSamples()==128L*32,"before control emits source settled endpoint vertices and every row light vertices="+beforeVertices);
  for(int c=0;c<128;c++)tiles[c].clientTerrain(saved[c]);for(int warm=0;warm<100;warm++)for(var t:tiles)renderer.render(t,0,poses,buffers,15728880,0);
  sink.vertices=0;b=NativeSegmentedBlockRenderer.stats();start=bean.getThreadAllocatedBytes(id);for(int repeat=0;repeat<100;repeat++)for(var t:tiles)renderer.render(t,0,poses,buffers,15728880,0);long allocated=bean.getThreadAllocatedBytes(id)-start;a=NativeSegmentedBlockRenderer.stats();need(sink.vertices==0&&a.lightSamples()==b.lightSamples()&&allocated==0,"settled128x32 adapter emits zero vertices/lights and allocates zero bytes allocated="+allocated);
  System.out.println("[vandorlabs][segmented-terrain-world] costs128x32 beforeVertices="+beforeVertices+" beforeRowLights=4096 beforeAllocatedBytes="+beforeAllocated+" beforeSamples=128 afterVertices="+sink.vertices+" afterRowLights=0 afterAllocatedBytes="+allocated+" afterSamples=12800; software/structural evidence, no hardware FPS claim");
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(!SegmentedTerrainWorldFixture.enabled()||capture==null||++frames<8)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->captured=true);}
}
