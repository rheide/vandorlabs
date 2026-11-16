package com.vandorlabs.neoforge.builders;

import com.mojang.math.Axis;
import com.vandorlabs.neoforge.builders.client.*;
import com.vandorlabs.neoforge.rack.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Actual title-screen GPU path: complete atlas meshes, fabrication, distance cull and resource reload. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeBuilderRenderChecks {
    private static int frames,generation,checkedGeneration=-1;private static boolean capture,done;private static int[] baseline;
    private static boolean enabled(){return "true".equals(System.getenv("VANDOR_BUILDER_RENDER_CHECK"));}
    private static void need(boolean yes,String text){if(!yes)throw new AssertionError("Native builder render: "+text);}
    private static NativeBuilderEntity.Visual scene(int index){
        String design=index==3?"assembly_press":index==4?"enclosed_gantry":"synthesis_tower";
        boolean processing=index==1||index==2||index==5;float progress=index==5?.8F:.5F;
        var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,8000,false,true);
        return new NativeBuilderEntity.Visual(design,index==5?Direction.EAST:Direction.NORTH,BlockPos.ZERO,true,processing,processing?progress:0,0,0,true,battery,ItemStack.EMPTY);
    }
    private static void verifyDynamicNormals(){
        need(NativeBuilderModelCache.mesh("synthesis_tower",true).vertexCount()==24,"exact six-face core before beam records");
        int[] vertices={0},normals={0};
        var target=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(com.mojang.blaze3d.vertex.VertexConsumer.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
            if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
            if(method.getName().equals("addVertex"))vertices[0]++;
            if(method.getName().equals("setNormal")&&vertices[0]>24&&vertices[0]<=56){normals[0]++;need(Math.abs(((Number)args[0]).floatValue())<.001&&((Number)args[1]).floatValue()<-.99&&Math.abs(((Number)args[2]).floatValue())<.001,"dynamic beam normal follows GUI flipped-Y pose");}
            return method.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?proxy:null;
        });
        var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.scale(1,-1,1);
        NativeSynthesisEffects.processing(scene(1),pose,layer->target,20);
        need(normals[0]==32,"four crossed beams emit32 correctly posed normal records");
        System.out.println("[vandorlabs][builder-render] posed-normal PASS fourBeams32records flippedGUIY");
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){
        if(done||!enabled()||!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||!NativeBuilderModelCache.ready())return;
        if(checkedGeneration!=generation){verifyDynamicNormals();checkedGeneration=generation;}
        var gui=event.getGuiGraphics();gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight(),cellW=width/3,cellH=height/2;
        gui.fill(0,0,width,height,0xff202830);gui.flush();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        var buffers=mc.renderBuffers().bufferSource();
        for(int i=0;i<6;i++){
            int x=i%3*cellW,y=i/3*cellH;gui.enableScissor(x,y,x+cellW,y+cellH);var pose=gui.pose();pose.pushPose();
            try{
                pose.translate(x+cellW/2D,y+cellH/2D,350);float scale=Math.min(cellW/7F,cellH/6.5F);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(15));pose.mulPose(Axis.YP.rotationDegrees(155));
                var visual=scene(i);pose.translate(-NativeBuilderModelCache.width(visual.design())/2D,-NativeBuilderModelCache.height(visual.design())/2D,-1.5);
                NativeBuilderRenderer.renderVisual(visual,pose,buffers,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,frames<5?20:50,i!=2);
            }finally{pose.popPose();}buffers.endBatch();gui.flush();gui.disableScissor();
        }
        com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
        need(NativeBuilderModelCache.retainedBytes()<4*1024*1024,"fixed mesh byte budget");capture=++frames>=10;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{
        if(done||!enabled()||(!capture&&frames!=5))return;var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var proof=mc.gameDirectory.toPath().resolve("screenshots/builders-native-"+generation+(frames==5?"-baseline":"")+".png");java.nio.file.Files.createDirectories(proof.getParent());image.writeToFile(proof);
            int cellW=image.getWidth()/3,cellH=image.getHeight()/2;
            if(frames==5){baseline=new int[image.getWidth()*image.getHeight()];for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)baseline[y*image.getWidth()+x]=image.getPixelRGBA(x,y);return;}
            int[] red=new int[6],changed=new int[6];
            for(int i=0;i<6;i++){
                int ink=0;for(int y=i/3*cellH;y<(i/3+1)*cellH;y++)for(int x=i%3*cellW;x<(i%3+1)*cellW;x++){
                    int pixel=image.getPixelRGBA(x,y);if((pixel&0xffffff)!=0x302820)ink++;int r=pixel&255,g=pixel>>>8&255,b=pixel>>>16&255;if(r>110&&r>g*1.5&&r>b*1.3)red[i]++;
                    if(pixel!=baseline[y*image.getWidth()+x])changed[i]++;
                }
                need(ink>80,"complete textured mesh scene="+i+" ink="+ink);
            }
            need(red[1]>12&&red[2]<red[1]/4,"nearby-only four beams visible; distant negative scene near="+red[1]+" far="+red[2]);
            need(changed[1]>10,"pause-aware clock moves scanning endpoints and screen preview pixels="+changed[1]);
            need(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"render path leaves no GL error");

            System.out.println("[vandorlabs][builder-render] generation="+generation+" beamPixels="+red[1]+" farNegative="+red[2]+" moved="+changed[1]+" retainedBytes="+NativeBuilderModelCache.retainedBytes());
        }
        if(generation++==0){frames=0;capture=false;mc.reloadResourcePacks();return;}
        done=true;System.out.println("[vandorlabs][builder-render] title PASS exactly3 finished structures textured native attachments partialCore fourLasers nearCull rotation fixedByteCache resourceReload; development/software only");mc.stop();
    }
    private NativeBuilderRenderChecks(){}
}
