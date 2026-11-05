package com.vandorlabs.copychecks;
import com.vandorlabs.neoforge.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
/** Measures actual target/outline path; no production renderer replacement. */
public final class ControlHoverClient {
 private static int sample,ticks,frames,targets;private static long start;private static final List<Long> times=new ArrayList<>();private static boolean measured,capture,captured,done;private static Vec3 target;
 private static void require(boolean ok,String detail){if(!ok)throw new IllegalStateException("Control hover: "+detail);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(mc.player==null||mc.level==null||mc.screen!=null||done)return;if(++ticks>3000)throw new IllegalStateException("Hover timeout sample="+sample+" targets="+targets);
  var pos=ControlHoverServer.position(sample);if(!(mc.level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity))return;
  var center=target==null?pos.getCenter():target;Vec3 delta=center.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));mc.options.hideGui=false;
  if(!measured){measured=true;var cache=clearCaches();long at=System.nanoTime();var shape=mc.level.getBlockState(pos).getShape(mc.level,pos);long constructed=System.nanoTime()-at;long collisionEntries=cache.size();if("true".equals(System.getenv("VANDOR_HOVER_BOUNDED")))require(collisionEntries==0,"cold outline populated collision voxel cache sample="+sample+" entries="+collisionEntries);int[] edges={0};at=System.nanoTime();shape.forAllEdges((a,b,c,d,e,f)->edges[0]++);long cold=System.nanoTime()-at;at=System.nanoTime();for(int n=0;n<20;n++)shape.forAllEdges((a,b,c,d,e,f)->{});long warm=(System.nanoTime()-at)/20;target=shape.toAabbs().stream().map(box->box.getCenter().add(pos.getX(),pos.getY(),pos.getZ())).min(Comparator.comparingDouble(point->point.distanceToSqr(mc.player.getEyePosition()))).orElseThrow();int collisionBoxes=mc.level.getBlockState(pos).getCollisionShape(mc.level,pos).toAabbs().size();require(collisionBoxes==new int[]{72,103,88,130,12,21,21,25}[sample],"detailed collision pieces changed sample="+sample+" boxes="+collisionBoxes);System.out.println("[vandorlabs][control-hover] sample="+sample+" block="+mc.level.getBlockState(pos)+" edges="+edges[0]+" collisionEntriesAfterColdOutline="+collisionEntries+" shapeNs="+constructed+" coldEdgesNs="+cold+" warmEdgesNs="+warm+" collisionBoxes="+collisionBoxes);if("true".equals(System.getenv("VANDOR_HOVER_BOUNDED")))require(edges[0]==12,"expected legacy single bounding outline sample="+sample+" edges="+edges[0]);}
  if(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos))targets++;
  if(frames<35)return;require(targets>5,"actual player hover did not target sample="+sample+" hit="+mc.hitResult);
  if(sample==3&&!captured){capture=true;return;}
  var sorted=new ArrayList<>(times);Collections.sort(sorted);System.out.println("[vandorlabs][control-hover] frame sample="+sample+" actualTargetTicks="+targets+" frames="+times.size()+" medianNs="+sorted.get(sorted.size()/2)+" maxNs="+Collections.max(sorted));
  if(++sample==8){done=true;System.out.println("[vandorlabs][control-hover] client PASS actual player target 8controls; software-frame timing only");mc.stop();}else{frames=targets=0;times.clear();measured=false;target=null;}
 }
 private static com.google.common.cache.Cache<?,?> clearCaches(){try{var field=NativeMountedControlGeometry.class.getDeclaredField("SHAPES");field.setAccessible(true);var cache=(com.google.common.cache.Cache<?,?>)field.get(null);cache.invalidateAll();try{var outlines=NativeMountedControlGeometry.class.getDeclaredField("OUTLINES");outlines.setAccessible(true);((com.google.common.cache.Cache<?,?>)outlines.get(null)).invalidateAll();}catch(NoSuchFieldException baseline){}return cache;}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
 @SubscribeEvent public static void pre(RenderFrameEvent.Pre event){start=System.nanoTime();}
 @SubscribeEvent public static void post(RenderFrameEvent.Post event){if(measured&&!done){times.add(System.nanoTime()-start);frames++;if(capture){capture=false;var mc=Minecraft.getInstance();require(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(ControlHoverServer.position(3)),"screenshot actual target");Screenshot.grab(mc.gameDirectory,"industrial-hover-native.png",mc.getMainRenderTarget(),message->captured=true);}}}
}
