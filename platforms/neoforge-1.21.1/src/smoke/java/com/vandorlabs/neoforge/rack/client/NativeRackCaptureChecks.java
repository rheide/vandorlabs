package com.vandorlabs.neoforge.rack.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vandorlabs.neoforge.rack.*;
import com.vandorlabs.rack.ModuleRackMotion;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Mid-stroke real-world pixels, not an isolated gallery or a draw-counter substitute. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRackCaptureChecks {
    private static int stage,ticks,checks,stageTicks,cleanFrames;private static boolean inserted,extracted,iconsDrawn,iconsDone;private static Matrix4f beforeView,beforePose;private static Map<?,?> beforeBuilders;private static Object beforeLast;
    private static Pending pending;
    private record Pending(String name,double age,double offset,double[] bounds,float[] view,float[] projection){}
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Rack visual stage="+stage+" tick="+ticks+": "+message);}
    private static Object field(Object target,String name)throws ReflectiveOperationException{Class<?> type=target instanceof Class<?> value?value:target.getClass();Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target instanceof Class<?>?null:target);}
    private static void next(int value){stage=value;stageTicks=0;}
    private static void use(Minecraft mc){var pos=NativeRackCaptureFixture.RACK;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(pos.getX()+.4,pos.getY()+13.25/16D,pos.getZ()+1),Direction.SOUTH,pos,false));}
    private static void mode(int value){PacketDistributor.sendToServer(new NativeRackCaptureFixture.Mode(value));}
    private static boolean ack(Minecraft mc,int value){return mc.level.getBlockState(NativeRackCaptureFixture.ACK.east(value)).is(Blocks.STONE);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeRackCaptureFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==40&&mc.level==null){need(inserted&&extracted&&cleanFrames>1,"both mid-stroke images and clean render state");System.out.println("[vandorlabs][rack-capture] client PASS checks="+checks+" realInsertExtract/overlayFreeMidStroke/projectedPixelPositive/modelViewAndPoseRestored/sharedBatchUnchanged/64KiBPrivateBuffer");next(41);mc.stop();return;}need(!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),"unexpected disconnect");if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<800&&++stageTicks<300,"bounded capture mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" overlay="+mc.getOverlay()+" stats="+Arrays.toString(NativeRackAnimation.stats()));mc.getToasts().clear();mc.gui.getChat().clearMessages(true);var rack=mc.level.getBlockEntity(NativeRackCaptureFixture.RACK) instanceof NativeRackEntity owner?owner:null;switch(stage){
        case 0->{if(mc.getOverlay()!=null||mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeRackBindings.AUTOPILOT)||mc.player.getMainHandItem().getCount()!=1||mc.player.position().distanceToSqr(.5,96,3.5)>.01||Math.abs(mc.player.getXRot()-18)>.5||rack==null||stageTicks<20||!iconsDone)return;NativeRackAppearanceChecks.verify();use(mc);next(1);}
        case 1->{if(!inserted||rack.occupied()!=1||NativeRackAnimation.stats()[0]!=0||!mc.player.getMainHandItem().isEmpty())return;mode(0);next(2);}
        case 2->{if(!ack(mc,0))return;use(mc);next(3);}
        case 3->{if(!extracted||rack.occupied()!=0||NativeRackAnimation.stats()[0]!=0||!mc.player.getMainHandItem().is(NativeRackBindings.AUTOPILOT))return;need(com.vandorlabs.neoforge.client.NativeSegmentedSectionUploads.observers()==0,"no idle upload observer");mode(1);next(4);}
        case 4->{if(!ack(mc,1))return;mc.level.disconnect();mc.disconnect();next(40);}
        default->{}
    }}
    @SubscribeEvent public static void icons(RenderGuiEvent.Post event){
        if(!NativeRackCaptureFixture.enabled()||stage!=0||iconsDone)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||mc.screen!=null)return;
        var graphics=event.getGuiGraphics();var items=new ItemStack[]{new ItemStack(NativeRackBindings.BLOCK),new ItemStack(NativeRackBindings.BLANK),new ItemStack(NativeRackBindings.AUTOPILOT)};
        for(int index=0;index<3;index++){int x=16+index*28,y=mc.getWindow().getGuiScaledHeight()-28;graphics.fill(x,y,x+24,y+24,0xff202830);graphics.renderItem(items[index],x+4,y+4);}graphics.flush();iconsDrawn=true;
    }
    @SubscribeEvent public static void iconImage(RenderFrameEvent.Post event)throws Exception{
        if(!NativeRackCaptureFixture.enabled()||!iconsDrawn||iconsDone)return;var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int scale=(int)mc.getWindow().getGuiScale();for(int index=0;index<3;index++){int ink=0;for(int y=0;y<16*scale;y++)for(int x=0;x<16*scale;x++){int pixel=image.getPixelRGBA((20+index*28)*scale+x,(mc.getWindow().getGuiScaledHeight()-24)*scale+y)&0xffffff;if(pixel==0x302820)continue;ink++;need(x>=scale&&x<15*scale&&y>=scale&&y<15*scale,"actual item icon padding "+index+" "+x+","+y);}need(ink>30,"visible authored item "+index);}
            var path=mc.gameDirectory.toPath().resolve("screenshots/native-rack-appearance-icons.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);
        }iconsDone=true;System.out.println("[vandorlabs][rack-capture] inventory-icons PASS threePaddedModels/abstractAutopilotMarker");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(RenderLevelStageEvent event)throws Exception{if(!NativeRackCaptureFixture.enabled()||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)return;beforeView=beforePose=null;if(NativeRackAnimation.stats()[0]==0)return;var mc=Minecraft.getInstance();beforeView=new Matrix4f(RenderSystem.getModelViewMatrix());beforePose=new Matrix4f(event.getPoseStack().last().pose());var source=mc.renderBuffers().bufferSource();beforeBuilders=new HashMap<>((Map<?,?>)fieldFrom(source,MultiBufferSource.BufferSource.class,"startedBuilders"));beforeLast=fieldFrom(source,MultiBufferSource.BufferSource.class,"lastSharedType");}
    private static Object fieldFrom(Object target,Class<?> type,String name)throws Exception{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(RenderLevelStageEvent event)throws Exception{if(!NativeRackCaptureFixture.enabled()||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS||beforeView==null)return;var mc=Minecraft.getInstance();need(beforeView.equals(RenderSystem.getModelViewMatrix()),"exact model-view restoration");need(beforePose.equals(event.getPoseStack().last().pose()),"exact pose restoration");var source=mc.renderBuffers().bufferSource();need(beforeBuilders.equals(fieldFrom(source,MultiBufferSource.BufferSource.class,"startedBuilders"))&&beforeLast==fieldFrom(source,MultiBufferSource.BufferSource.class,"lastSharedType"),"Minecraft shared buffer is untouched");var storage=field(NativeRackAnimation.class,"storage");if(storage!=null)need(((Number)field(storage,"capacity")).intValue()==65536,"bounded private direct buffer");cleanFrames++;
        if(pending!=null||mc.getOverlay()!=null||mc.screen!=null||stage!=1&&stage!=3)return;String name=stage==1?"inserting":"extracting";if(stage==1&&inserted||stage==3&&extracted)return;
        var active=(Map<?,?>)field(NativeRackAnimation.class,"ACTIVE");var entry=active.get(NativeRackCaptureFixture.RACK);if(entry==null)return;var pose=((Object[])field(entry,"poses"))[0];if(pose==null||(boolean)field(pose,"waiting"))return;double time=((Number)field(NativeRackAnimation.class,"time")).doubleValue(),age=time-((Number)field(pose,"started")).doubleValue();if(age<ModuleRackMotion.DURATION_TICKS*.35||age>ModuleRackMotion.DURATION_TICKS*.65)return;var offsetMethod=pose.getClass().getDeclaredMethod("offset",double.class);offsetMethod.setAccessible(true);double offset=((Number)offsetMethod.invoke(pose,time)).doubleValue();need(offset>.1&&offset<.4,"genuine mid-stroke offset");var bounds=new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};var transform=new Matrix4f(event.getProjectionMatrix()).mul(beforeView).mul(event.getModelViewMatrix()).mul(beforePose);var camera=event.getCamera().getPosition();var pos=NativeRackCaptureFixture.RACK;for(var quad:NativeRackClient.facing(Direction.SOUTH).moving(0,2)){var data=quad.getVertices();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++){int at=vertex*stride;var projected=new Vector4f((float)(pos.getX()+Float.intBitsToFloat(data[at])-camera.x),(float)(pos.getY()+Float.intBitsToFloat(data[at+1])-camera.y),(float)(pos.getZ()+Float.intBitsToFloat(data[at+2])+offset-camera.z),1);transform.transform(projected);need(projected.w>0,"cassette in front of camera");double x=(projected.x/projected.w*.5+.5)*mc.getWindow().getWidth(),y=(.5-projected.y/projected.w*.5)*mc.getWindow().getHeight();bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);}}
        pending=new Pending(name,age,offset,bounds,event.getModelViewMatrix().get(new float[16]),event.getProjectionMatrix().get(new float[16]));mc.options.hideGui=true;
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event)throws Exception{if(!NativeRackCaptureFixture.enabled()||pending==null)return;var mc=Minecraft.getInstance();need(mc.getOverlay()==null,"capture has no resource overlay");var sample=pending;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var bounds=sample.bounds;int left=Math.max(0,(int)Math.floor(bounds[0])),top=Math.max(0,(int)Math.floor(bounds[1])),right=Math.min(image.getWidth()-1,(int)Math.ceil(bounds[2])),bottom=Math.min(image.getHeight()-1,(int)Math.ceil(bounds[3]));need(right-left>20&&bottom-top>10,"visible projected cassette bounds");int pixels=0;for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){int rgba=image.getPixelRGBA(x,y),r=rgba&255,g=rgba>>>8&255,b=rgba>>>16&255;if(g>45&&b>45&&g>r*1.6&&b>r*1.6)pixels++;}need(pixels>=8,"positive cyan cassette pixels inside exact projected active bounds, got "+pixels);var path=mc.gameDirectory.toPath().resolve("screenshots/native-module-"+sample.name+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);var record=new com.google.gson.JsonObject();record.addProperty("phase",sample.name);record.addProperty("ageTicks",sample.age);record.addProperty("offsetBlocks",sample.offset);record.addProperty("cyanPixels",pixels);record.add("projectedBounds",new com.google.gson.Gson().toJsonTree(bounds));record.add("modelView",new com.google.gson.Gson().toJsonTree(sample.view));record.add("projection",new com.google.gson.Gson().toJsonTree(sample.projection));java.nio.file.Files.writeString(path.resolveSibling("native-module-"+sample.name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(record));System.out.println("[vandorlabs][rack-capture] "+sample.name+" PASS offset="+sample.offset+" pixels="+pixels+" bounds="+Arrays.toString(bounds));}
        if(sample.name.equals("inserting"))inserted=true;else extracted=true;pending=null;mc.options.hideGui=false;
    }
}
