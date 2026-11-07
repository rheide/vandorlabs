package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.vandorlabs.neoforge.ramp.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real completed atlas/shader/consumer. Private standalone backend, not public Ramp gameplay. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRampPartRenderChecks {
    private record Scene(NativeRampGeometry.Spec spec,NativeRampGeometry.Snapshot snapshot,double pose,NativeRampPartRenderer.Materials materials,NativeRampPartRenderer.SourceFaces source,NativeRampPartRenderer.Draw draw){}
    private static Scene[] scenes;private static NativeRampPartRenderer.Bake bake;private static int generation,frames,checks;private static boolean capture,done;private static java.util.concurrent.CompletableFuture<Void> reload;
    private static boolean active(){return Boolean.getBoolean("vandorlabs.rampPartRenderCheck");}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Ramp part generation="+generation+": "+why);}
    private static final class Gallery extends Screen {Gallery(){super(net.minecraft.network.chat.Component.literal("Ramp parts"));}@Override public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float partial){}}
    private static void prepare(){
        bake=NativeRampPartRenderer.currentBake();need(bake!=null,"completed generation-owned material bake");var result=new ArrayList<Scene>();var cell=new BlockPos(8,80,8);
        for(int facing=2;facing<=5;facing++)for(int mode=0;mode<6;mode++){
            var direction=Direction.from3DDataValue(facing);boolean stairs=mode==4;var shapes=stairs?List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,0,1,1,.5)):List.of(new AABB(0,0,0,1,1,1));var origins=List.of(new NativeRampGeometry.Origin(cell,shapes),new NativeRampGeometry.Origin(mode==5?cell.above():cell.relative(direction),shapes));
            var spec=new NativeRampGeometry.Spec(cell,direction,80,0,3,4,0,1,-.25,.75,mode==1,mode==2?2:0,mode==3,false,0,mode==5,mode==4,stairs,origins);double pose=.3;var snapshot=NativeRampGeometry.snapshot(spec,pose);need(!snapshot.pieces().isEmpty(),"scene physical pieces="+mode+" facing="+facing);
            var state=mode==0?Blocks.GRASS_BLOCK.defaultBlockState():mode==1?Blocks.GLASS.defaultBlockState():Blocks.STONE.defaultBlockState();var choices=new LinkedHashMap<BlockPos,NativeRampPartMaterial>();if(mode>=2)for(var origin:origins)choices.put(origin.position(),new NativeRampPartMaterial(state,64,7,mode%3,facing-2,stairs,false,new int[]{-1,-1,-1,-1,-1,-1}));
            var materials=new NativeRampPartRenderer.Materials(state,choices);var source=NativeRampPartRenderer.source(state,null,cell);var draw=bake.draw(spec,snapshot,pose,materials,source);need(draw.vertices()>0&&draw.vertices()%4==0,"actual prepared quads="+mode);need(bake.draw(spec,snapshot,pose,materials,source)==draw,"prepared mesh reuse");result.add(new Scene(spec,snapshot,pose,materials,source,draw));
            choices.clear();need(bake.draw(spec,snapshot,pose,materials,source)==draw,"caller material mutation cannot affect captured draw");
        }
        scenes=result.toArray(Scene[]::new);need(scenes.length==24,"six paths across four facings");measure();
    }
    private static void measure(){
        var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();int count=Arrays.stream(scenes).mapToInt(s->s.draw.vertices()).sum();var pose=new PoseStack();int rounds=Math.max(1,Math.min(16,200000/count));
        try(var memory=new ByteBufferBuilder(32*1024*1024)){
            for(int warm=0;warm<8;warm++){var builder=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);MultiBufferSource buffers=ignored->builder;for(int i=0;i<rounds;i++)for(var scene:scenes)bake.draw(scene.spec,scene.snapshot,scene.pose,scene.materials,scene.source).emit(pose.last(),buffers,0xF000F0,OverlayTexture.NO_OVERLAY);builder.buildOrThrow().close();memory.clear();}
            var builder=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);MultiBufferSource buffers=ignored->builder;long builds=bake.builds(),before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<rounds;i++)for(var scene:scenes)bake.draw(scene.spec,scene.snapshot,scene.pose,scene.materials,scene.source).emit(pose.last(),buffers,0xF000F0,OverlayTexture.NO_OVERLAY);long allocated=meter.getThreadAllocatedBytes(thread)-before;try(var mesh=builder.buildOrThrow()){need(mesh.drawState().vertexCount()==count*rounds&&allocated==0&&builds==bake.builds(),"actual prepared batch vertices="+mesh.drawState().vertexCount()+" allocated="+allocated);System.out.println("[vandorlabs][ramp-part-render] generation="+generation+" actualVertices="+mesh.drawState().vertexCount()+" allocated="+allocated+" builds="+builds+" retained="+bake.retainedBytes());}
        }
        need(bake.retainedBytes()<=8*1024*1024,"byte-bounded geometry/material cache");
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){
        if(!active()||done)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof AccessibilityOnboardingScreen intro){intro.onClose();return;}if(event.getScreen() instanceof TitleScreen){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}if(scenes==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
        if(frames>=4){var buffers=mc.renderBuffers().bufferSource();for(int i=0;i<scenes.length;i++){var scene=scenes[i];int x=12+i%6*102,y=12+i/6*83;gui.enableScissor(x,y,x+96,y+78);var pose=gui.pose();pose.pushPose();try{pose.translate(x+48,y+40,300);pose.scale(47,-47,47);pose.mulPose(Axis.XP.rotationDegrees(20));pose.mulPose(Axis.YP.rotationDegrees(-30));pose.translate(-.5,-.5,-.5);scene.draw.emit(pose.last(),buffers,0xF000F0,OverlayTexture.NO_OVERLAY);buffers.endBatch();}finally{pose.popPose();gui.disableScissor();}}}gui.flush();frames++;capture=frames==4||frames==9;
    }
    @SubscribeEvent public static void shot(RenderFrameEvent.Post event)throws Exception{
        if(!active()||!capture||done)return;capture=false;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/ramp-parts-"+generation+"-"+(frames==4?"disabled":"positive")+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);int scale=(int)mc.getWindow().getGuiScale();for(int i=0;i<24;i++){int ink=0;for(int y=0;y<78*scale;y++)for(int x=0;x<96*scale;x++)if((image.getPixelRGBA((12+i%6*102)*scale+x,(12+i/6*83)*scale+y)&0xffffff)!=0x302820)ink++;need(frames==4?ink==0:ink>40,"actual or disabled scene="+i+" ink="+ink);}}
        if(frames==9){bake.clear();need(bake.retainedBytes()==0,"explicit cache retirement");if(generation++==0){scenes=null;frames=0;reload=mc.reloadResourcePacks();}else{done=true;System.out.println("[vandorlabs][ramp-part-render] title PASS checks="+checks+" scenes24/atlas/shading/glass/mixedMaterials/sideways/moat/vertical/consumer/reload/disabled; no runtime movement claim");mc.stop();}}
    }
}
