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
    private static int frames,generation,checkedGeneration=-1;private static boolean capture,done,icons;private static int[] baseline;
    private static boolean enabled(){return "true".equals(System.getenv("VANDOR_BUILDER_RENDER_CHECK"));}
    private static void need(boolean yes,String text){if(!yes)throw new AssertionError("Native builder render: "+text);}
    private static NativeBuilderEntity.Visual scene(int index){
        String design=index==3?"assembly_press":index==4?"enclosed_gantry":"synthesis_tower";
        boolean processing=index!=0;float progress=index>=3?.8F:.5F;
        var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,8000,false,true);
        if(index==0){var modules=new java.util.ArrayList<ItemStack>();for(int bay=0;bay<4;bay++){var module=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(module,10000-bay*2500,false,true);modules.add(module);}return new NativeBuilderEntity.Visual(design,Direction.NORTH,BlockPos.ZERO,true,false,0,0,0,true,modules.get(0),ItemStack.EMPTY,java.util.List.copyOf(modules),java.util.List.of(0L,0L,0L,0L),false);}
        int product=index==5?NativeBuilderProducts.RAMP:index==3?1:index==4?5:0;
        return new NativeBuilderEntity.Visual(design,index==5?Direction.EAST:Direction.NORTH,BlockPos.ZERO,true,processing,processing?progress:0,product,product,true,battery,ItemStack.EMPTY);
    }
    private static void verifyDynamicNormals(){
        need(NativeBuilderModelCache.mesh("synthesis_tower",true).vertexCount()==com.vandorlabs.geometry.CoreMesh.faces("synthesis_tower").size()*4,"shared hollow Core mesh");
        int[] vertices={0},normals={0};boolean[] beam={false};var colors=new java.util.HashSet<Integer>();for(int i=0;i<4;i++)colors.add(com.vandorlabs.machines.SynthesisLaserStyle.color(i,20));
        var target=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(com.mojang.blaze3d.vertex.VertexConsumer.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
            if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
            if(method.getName().equals("addVertex")){vertices[0]++;beam[0]=false;}
            if(method.getName().equals("setColor")&&args.length==1&&args[0] instanceof Integer color)beam[0]=colors.contains(color);
            if(method.getName().equals("setColor")&&args.length==4)beam[0]=colors.contains(((Number)args[3]).intValue()<<24|((Number)args[0]).intValue()<<16|((Number)args[1]).intValue()<<8|((Number)args[2]).intValue());
            if(method.getName().equals("setNormal")&&beam[0]){normals[0]++;need(Math.abs(((Number)args[0]).floatValue())<.001&&((Number)args[1]).floatValue()<-.99&&Math.abs(((Number)args[2]).floatValue())<.001,"dynamic beam normal follows GUI flipped-Y pose");}
            return method.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?proxy:null;
        });
        var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.scale(1,-1,1);
        NativeSynthesisEffects.processing(scene(1),pose,layer->target,20);
        need(normals[0]==32,"four crossed beams emit32 correctly posed normal records observed="+normals[0]);
        var active=scene(1);var paused=new NativeBuilderEntity.Visual(active.design(),active.facing(),active.pivot(),active.formed(),active.processing(),active.progress(),active.selected(),active.building(),active.connected(),active.battery(),active.chip(),active.batteries(),active.batterySequences(),false);
        NativeSynthesisEffects.processing(paused,pose,layer->target,20);need(normals[0]==32,"paused partial scaffold emits no lasers");

        int before=vertices[0];
        NativeSynthesisEffects.processing(scene(5),pose,layer->target,20);
        need(vertices[0]-before>32&&normals[0]==64,"fixed Ramp fabrication emits native item quads alongside four beams");
        System.out.println("[vandorlabs][builder-render] posed-normal PASS fourBeams32records flippedGUIY");
    }
    private record FabricationDraw(int green,int blue,int voxels,double minY,double maxY,double headX){}
    private static FabricationDraw observeParts(NativeBuilderEntity.Visual visual,double now){
        var mc=Minecraft.getInstance();
        var sprite=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .apply(net.minecraft.resources.ResourceLocation.parse("vandorlabs:blocks/core_flat"));
        float spriteU=sprite.getU(.5F),spriteV=sprite.getV(.5F);
        int[] counts=new int[3];
        boolean[] beam={false};
        double[] values={0,0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN};
        var vertexType=com.mojang.blaze3d.vertex.VertexConsumer.class;
        var target=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                vertexType.getClassLoader(),new Class<?>[]{vertexType},(proxy,method,args)->{
            if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
            switch(method.getName()){
                case "addVertex"->{values[0]=((Number)args[0]).doubleValue();values[1]=((Number)args[1]).doubleValue();beam[0]=false;}
                case "setUv"->{if(!beam[0]&&((Number)args[0]).floatValue()==spriteU&&((Number)args[1]).floatValue()==spriteV){
                    counts[2]++;values[2]=Math.min(values[2],values[1]);values[3]=Math.max(values[3],values[1]);
                }}
                case "setColor"->{
                    int color=args.length==1?((Number)args[0]).intValue():
                            ((Number)args[3]).intValue()<<24|((Number)args[0]).intValue()<<16
                            |((Number)args[1]).intValue()<<8|((Number)args[2]).intValue();
                    if(color==0xb04fee75){counts[0]++;beam[0]=true;}
                    if(color==0x324baeff||color==0xc05bbcff||color==0xd06ccaff){
                        if(counts[1]==0)values[4]=values[0];
                        counts[1]++;beam[0]=true;
                    }
                }
                default->{}
            }
            return method.getReturnType()==vertexType?proxy:null;
        });
        NativeBuilderRenderer.renderVisual(visual,new com.mojang.blaze3d.vertex.PoseStack(),
                layer->target,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,now,true);
        return new FabricationDraw(counts[0],counts[1],counts[2],values[2],values[3],values[4]);
    }
    private static void verifyPartFabrication(){
        var press=observeParts(scene(3),20);
        var gantry=observeParts(scene(4),20);
        var later=observeParts(scene(4),50);
        need(press.green==32&&press.blue==0&&press.voxels>0&&press.maxY-press.minY<.3,"Press emits a flat product with four green emitter beams");
        need(gantry.blue==16&&gantry.green==0&&gantry.voxels>0&&gantry.maxY-gantry.minY>.4,"Gantry emits a full-size product with a blue blade");
        need(Math.abs(gantry.headX-later.headX)>1,"blue scan endpoint follows the moving gantry head");
        var source=scene(4);
        var paused=new NativeBuilderEntity.Visual(source.design(),source.facing(),source.pivot(),true,true,source.progress(),
                source.selected(),source.building(),source.connected(),source.battery(),source.chip(),source.batteries(),
                source.batterySequences(),false);
        var still=observeParts(paused,50);
        need(still.blue==0&&still.voxels>0,"paused gantry retains the paid partial geometry without an active laser");
        var clock=new com.vandorlabs.machines.BuilderFabricationPose();
        clock.observe(10,true);clock.observe(15,false);clock.observe(30,false);clock.observe(30,true);
        need(clock.phase()==5,"pause freezes the authored carriage and repeated updates retain phase");
        clock.observe(31,true);
        need(clock.phase()==6,"gantry resumes from its earlier phase");
        System.out.println("[vandorlabs][builder-render] part-fabrication PASS distinctGreenBlue/voxelMeshes/headMotion/pausedPartial");
    }
    private static void verifyDriveLighting(){
        try{
            var method=Class.forName("com.vandorlabs.neoforge.builders.client.NativeDriveRenderer").getDeclaredMethod(
                    "render",NativeBuilderEntity.class,com.mojang.blaze3d.vertex.PoseStack.class,
                    net.minecraft.client.renderer.MultiBufferSource.class,int.class,int.class,double.class);
            method.setAccessible(true);
            var registries=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            for(var drive:NativeDriveDesign.SUPPORTED){
                var core=NativeBuilderBindings.CORES.get(drive.id);
                var tile=new NativeBuilderEntity(BlockPos.ZERO,core.defaultBlockState().setValue(NativeBuilderCoreBlock.FORMED,true));
                var update=tile.getUpdateTag(registries);
                update.put("Drive",new NativeDriveSettings(true,false,drive.engine(),0,8,-2,-2,false,1).save());
                long[] off=drawDrive(method,tile,update,0,registries);
                long[] dim=drawDrive(method,tile,update,7,registries);
                long[] full=drawDrive(method,tile,update,15,registries);
                need(off[0]>0&&dim[0]>0&&full[0]>0,"authored idle and active drive parts emit "+drive.id);
                if(drive.engine()){
                    need(dim[1]==full[1]&&dim[1]>0&&dim[2]>0&&dim[2]<full[2],"signal scales the same active lights "+drive.id);
                    need(drive.exhaust?off[1]>0&&off[2]<full[2]:off[1]==0,"distinct authored standby lights "+drive.id);
                }else need(off[1]==0&&full[1]==0&&off[3]!=full[3],"Vector Fold uses its authored idle and active atlas art");
            }
            var shutters=new com.vandorlabs.drives.DriveShutterPose();
            need(shutters.sample(false,0)==0&&shutters.sample(true,0)==0,"closed shutters initialize and target does not jump");
            need(shutters.sample(true,10)==.5&&shutters.sample(true,10)==.5,"half travel survives pause and duplicate target");
            need(shutters.sample(false,10)==.5&&shutters.sample(false,15)==.25&&shutters.sample(false,20)==0,
                    "Oculus reverses from the current travel over the matching remaining distance");
            need(com.vandorlabs.drives.DriveShutterPose.easedTravel(1)==.5125,"authored shutter travel bound");
            System.out.println("[vandorlabs][builder-render] drives PASS actualMeshes/idleActive/signalBrightness/OculusPausedReverse");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static long[] drawDrive(java.lang.reflect.Method method,NativeBuilderEntity tile,net.minecraft.nbt.CompoundTag publicData,
                                    int strength,net.minecraft.core.HolderLookup.Provider registries)throws ReflectiveOperationException{
        publicData.putInt("DriveLevel",strength);
        tile.handleUpdateTag(publicData,registries);
        need(!tile.inert()&&tile.driveLevel()==strength,"valid public drive level for rendering");
        long[] counts=new long[4];int[] color={0};
        var vertexType=com.mojang.blaze3d.vertex.VertexConsumer.class;
        var target=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                vertexType.getClassLoader(),new Class<?>[]{vertexType},(proxy,call,args)->{
            if(call.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,call,args);
            switch(call.getName()){
                case "addVertex"->counts[0]++;
                case "setUv"->counts[3]+=Float.floatToRawIntBits(((Number)args[0]).floatValue());
                case "setColor"->color[0]=((Number)args[0]).intValue()+((Number)args[1]).intValue()+((Number)args[2]).intValue();
                case "setUv2"->{if(((Number)args[0]).intValue()==240&&((Number)args[1]).intValue()==240){counts[1]++;counts[2]+=color[0];}}
                default->{}
            }
            return call.getReturnType()==vertexType?proxy:null;
        });
        net.minecraft.client.renderer.MultiBufferSource buffers=layer->target;
        method.invoke(null,tile,new com.mojang.blaze3d.vertex.PoseStack(),buffers,0,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,20D);
        return counts;
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){
        if(done||!enabled()||!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||!NativeBuilderModelCache.ready())return;
        if(icons){NativeCoreIconRenderChecks.render(event.getGuiGraphics());return;}
        if(checkedGeneration!=generation){verifyDynamicNormals();verifyPartFabrication();verifyDriveLighting();checkedGeneration=generation;}
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
        if(icons){done=true;NativeCoreIconRenderChecks.capture();System.out.println("[vandorlabs][builder-render] title PASS exactly3 finished structures textured native attachments partialCore fourLasers nearCull rotation fixedByteCache resourceReload actualCoreIcons fixedRampFabrication PressGantry; development/software only");mc.stop();return;}
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
        icons=true;
    }
    private NativeBuilderRenderChecks(){}
}
