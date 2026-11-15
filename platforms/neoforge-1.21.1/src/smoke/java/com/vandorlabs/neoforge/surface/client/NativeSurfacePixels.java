package com.vandorlabs.neoforge.surface.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vandorlabs.neoforge.surface.*;
import com.vandorlabs.neoforge.client.NativeSegmentedSectionUploads;
import com.vandorlabs.neoforge.mixin.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** A fresh successful terrain upload and actual green pixels after an appearance-only tile update. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSurfacePixels {
    private static SectionRenderDispatcher.RenderSection section;private static Object compiled,task;
    private static NativeSegmentedSectionUploads.Observation observation;
    private static boolean requested,done;private static int cleanFrames,armedFrames;private static double[][] polygon;
    public static boolean done(){return done;}
    public static int cleanFrames(){return cleanFrames;}
    public static boolean begin(){var mc=Minecraft.getInstance();var origin=new BlockPos(0,96,0);var view=((SegmentedLevelRendererAccessor)mc.levelRenderer).vandorlabs$segmentedViewArea();var next=view==null?null:((SegmentedViewAreaInvoker)view).vandorlabs$segmentedSection(origin);if(next==null||!next.getOrigin().equals(origin)||((NativeSegmentedSectionUploads.Section)next).vandorlabs$terrainDirty())return false;section=next;compiled=next.getCompiled();task=((NativeSegmentedSectionUploads.Section)next).vandorlabs$terrainTask();observation=NativeSegmentedSectionUploads.observe((NativeSegmentedSectionUploads.Section)next);return true;}
    public static boolean uploaded(){return section!=null&&NativeSegmentedSectionUploads.observedUpload((NativeSegmentedSectionUploads.Section)section,compiled,task);}
    public static void arm(){requested=true;armedFrames=0;Minecraft.getInstance().options.hideGui=true;}
    @SubscribeEvent public static void before(RenderFrameEvent.Pre event){if(!NativeSurfaceClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;mc.getToasts().clear();mc.gui.getChat().clearMessages(true);if(mc.getOverlay()==null){cleanFrames++;if(requested)armedFrames++;}else cleanFrames=armedFrames=0;}
    @SubscribeEvent public static void projected(RenderLevelStageEvent event){if(!NativeSurfaceClientFixture.enabled()||!requested||done||armedFrames<2||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||mc.screen!=null)return;if(!uploaded())throw new AssertionError("Surface appearance has no fresh successful terrain upload");var camera=event.getCamera().getPosition();var transform=new Matrix4f(event.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(event.getModelViewMatrix()).mul(event.getPoseStack().last().pose());polygon=new double[4][2];int index=0;for(double[] corner:new double[][]{{.1,.1},{.9,.1},{.9,.9},{.1,.9}}){var projected=new Vector4f((float)(corner[0]-camera.x),(float)(97.001-camera.y),(float)(corner[1]-camera.z),1);transform.transform(projected);if(projected.w<=0)throw new AssertionError("Surface face behind camera");polygon[index][0]=(projected.x/projected.w*.5+.5)*mc.getWindow().getWidth();polygon[index][1]=(.5-projected.y/projected.w*.5)*mc.getWindow().getHeight();index++;}}
    private static boolean inside(double x,double y){boolean positive=false,negative=false;for(int i=0;i<4;i++){var a=polygon[i];var b=polygon[(i+1)%4];double cross=(b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]);positive|=cross>1e-6;negative|=cross< -1e-6;}return !(positive&&negative);}
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event)throws Exception {if(!NativeSurfaceClientFixture.enabled()||polygon==null||done)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||mc.screen!=null||cleanFrames<2)return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;for(var point:polygon){minX=Math.min(minX,point[0]);maxX=Math.max(maxX,point[0]);minY=Math.min(minY,point[1]);maxY=Math.max(maxY,point[1]);}int samples=0,green=0;for(int y=Math.max(0,(int)Math.floor(minY));y<=Math.min(image.getHeight()-1,(int)Math.ceil(maxY));y++)for(int x=Math.max(0,(int)Math.floor(minX));x<=Math.min(image.getWidth()-1,(int)Math.ceil(maxX));x++)if(inside(x+.5,y+.5)){samples++;int rgba=image.getPixelRGBA(x,y),r=rgba&255,g=rgba>>>8&255,b=rgba>>>16&255;if(g>30&&g>r*1.05&&g>b*1.15)green++;}if(samples<100||green<Math.max(8,samples/5))throw new AssertionError("Surface actual terrain remains stale: green="+green+" samples="+samples+" polygon="+Arrays.deepToString(polygon));var path=mc.gameDirectory.toPath().resolve("screenshots/native-surface-repaired.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);var record=new com.google.gson.JsonObject();record.addProperty("greenPixels",green);record.addProperty("sampledPixels",samples);record.addProperty("freshSuccessfulTerrainUpload",uploaded());record.add("projectedInterior",new com.google.gson.Gson().toJsonTree(polygon));java.nio.file.Files.writeString(path.resolveSibling("native-surface-repaired.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(record));System.out.println("[vandorlabs][surface-client] pixels PASS green="+green+" samples="+samples+" polygon="+Arrays.deepToString(polygon));}finally{if(observation!=null)observation.close();observation=null;mc.options.hideGui=false;}done=true;requested=false;polygon=null;}
    private NativeSurfacePixels(){}
}
