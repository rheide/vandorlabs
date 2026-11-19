package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.vandorlabs.machines.SynthesisPresentation;
import com.vandorlabs.rack.EmbeddedRackMotion;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.builders.NativeBuilderProducts;
import com.vandorlabs.neoforge.builders.NativeSynthesisRuntime;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** Fabrication remains cosmetic. Exact cached core artwork is clipped at the authoritative build height. */
public final class NativeSynthesisEffects {
    private static final int FULLBRIGHT=0xf000f0;
    private static final class Scratch {final Vector3f position=new Vector3f(),normal=new Vector3f();}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    public static void mounted(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        mounted(visual,poses,buffers,light,overlay,null,0);
    }
    public static void mounted(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,int light,int overlay,EmbeddedRackMotion motion,double now){
        poses.pushPose();try{
            poses.translate(SynthesisPresentation.RACK_X+1,SynthesisPresentation.RACK_Y,SynthesisPresentation.RACK_Z+1);poses.mulPose(Axis.YP.rotationDegrees(180));
            for(int bay=0;bay<4;bay++){
                boolean occupied=!visual.batteries().get(bay).isEmpty();int charge=com.vandorlabs.rack.BatteryChargeBar.pixels(NativeBatteryModule.energy(visual.batteries().get(bay)),NativeBatteryModule.CAPACITY);
                if(motion!=null){occupied=motion.occupied(bay);charge=motion.level(bay);}
                emitRack("indicator_"+bay+(occupied?"_on":"_off"),poses,buffers,light,overlay);
                if(!occupied)emitRack("slot_empty_"+bay,poses,buffers,light,overlay);
                if(motion==null?occupied:motion.visible(bay,now)){poses.pushPose();try{if(motion!=null)poses.translate(0,0,motion.offset(bay,now));emitRack((motion!=null&&motion.moving(bay,now)?"slot_battery_":"slot_seated_battery_")+bay+"_0",poses,buffers,light,overlay);var bar=NativeBuilderModelCache.batteryBar(bay,charge);if(bar!=null)bar.emit(poses.last(),buffers.getBuffer(NativeBuilderModelCache.LAYER),FULLBRIGHT,overlay);}finally{poses.popPose();}}
            }
        }finally{poses.popPose();}
        if(!visual.chip().isEmpty()){
            poses.pushPose();try{poses.translate(SynthesisPresentation.CHIP_X,SynthesisPresentation.CHIP_Y,SynthesisPresentation.CHIP_Z-.07);poses.scale(.35F,.35F,.35F);poses.mulPose(Axis.YP.rotationDegrees(180));Minecraft.getInstance().getItemRenderer().renderStatic(visual.chip(),ItemDisplayContext.FIXED,light,overlay,poses,buffers,Minecraft.getInstance().level,0);}finally{poses.popPose();}
        }
        int mask=visual.portConnections();
        if((mask&1)!=0)rear(poses,buffers,"power_in_on",2);
        if((mask&2)!=0)bottom(poses,buffers,"power_in_on",2);
        if((mask&4)!=0)rear(poses,buffers,"matter_in_on",0);
        if((mask&16)!=0)bottom(poses,buffers,"matter_out_on",0);
        if((mask&8)!=0){var sprite=sprite("vandorlabs:blocks/resource_ports/matter_out_on");var out=buffers.getBuffer(NativeBuilderModelCache.LAYER);point(out,poses,sprite,-.002,0,2,0,1,-1);point(out,poses,sprite,-.002,0,3,1,1,-1);point(out,poses,sprite,-.002,1,3,1,0,-1);point(out,poses,sprite,-.002,1,2,0,0,-1);}
    }
    private static void rear(PoseStack poses,MultiBufferSource buffers,String name,int x){var sprite=sprite("vandorlabs:blocks/resource_ports/"+name);var out=buffers.getBuffer(NativeBuilderModelCache.LAYER);point(out,poses,sprite,x+1,0,3.002,0,1,-1);point(out,poses,sprite,x,0,3.002,1,1,-1);point(out,poses,sprite,x,1,3.002,1,0,-1);point(out,poses,sprite,x+1,1,3.002,0,0,-1);}
    private static void bottom(PoseStack poses,MultiBufferSource buffers,String name,int x){var sprite=sprite("vandorlabs:blocks/resource_ports/"+name);var out=buffers.getBuffer(NativeBuilderModelCache.LAYER);point(out,poses,sprite,x,-.002,2,0,0,-1);point(out,poses,sprite,x+1,-.002,2,1,0,-1);point(out,poses,sprite,x+1,-.002,3,1,1,-1);point(out,poses,sprite,x,-.002,3,0,1,-1);}

    private static ItemStack rampPreview=ItemStack.EMPTY;
    private static ItemStack rampPreview(){
        if(rampPreview.isEmpty())rampPreview=NativeBuilderProducts.result(NativeBuilderProducts.RAMP);
        return rampPreview;
    }
    public static void processing(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,double now){processing(visual,poses,buffers,now,null);}
    public static void processing(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,double now,NativeBuilderProgressMotion.State eased){
        boolean digitize=visual.mode()==1;var input=eased==null?visual.input():eased.input();var template=eased==null?visual.template():eased.template();
        if(eased==null?!visual.processing()&&visual.progress()<=0&&(!digitize||input.isEmpty()):!eased.visible)return;
        float progress=eased==null?visual.processing()?visual.progress():1:eased.progress;float fraction=digitize?visual.processing()||visual.input().isEmpty()?1-progress:1:progress;
        int building=visual.building();
        boolean ramp=building==NativeBuilderProducts.RAMP;
        boolean blockProduct=digitize||building==NativeSynthesisRuntime.DYNAMIC||ramp;
        ItemStack stack=digitize?input:ramp?rampPreview():template;
        String id=blockProduct?null:NativeBuilderProducts.IDS[Math.clamp(building,0,NativeBuilderProducts.IDS.length-1)];
        poses.pushPose();try{
            poses.translate(1,SynthesisPresentation.CORE_Y,1.125);
            if(blockProduct)NativeBlockRecipePreview.render(stack,poses,buffers,FULLBRIGHT,OverlayTexture.NO_OVERLAY,fraction);
            else NativeCoreModel.emit(id,fraction,poses.last(),buffers.getBuffer(NativeBuilderModelCache.LAYER),FULLBRIGHT,OverlayTexture.NO_OVERLAY);
        }finally{poses.popPose();}
        if(!visual.processing())return;
        var white=sprite("minecraft:block/white_concrete");var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));double surface=SynthesisPresentation.CORE_Y+(blockProduct?NativeBlockRecipePreview.upperY(stack,fraction):com.vandorlabs.geometry.CoreMesh.height(id,fraction))+.008;
        if(visual.working())for(int i=0;i<4;i++){
            int color=com.vandorlabs.machines.SynthesisLaserStyle.color(i,now);
            double ex=1.5+.46*Math.sin(now*.12+i*Math.PI/2),ez=1.625+.46*Math.sin(now*.075+i*Math.PI),sx=i%2==0?.70:2.30,sz=i<2?.95:2.30,r=.014;
            point(out,poses,white,sx-r,4.48,sz,.5,.5,color);point(out,poses,white,sx+r,4.48,sz,.5,.5,color);point(out,poses,white,ex+r,surface,ez,.5,.5,color);point(out,poses,white,ex-r,surface,ez,.5,.5,color);
            point(out,poses,white,sx,4.48,sz-r,.5,.5,color);point(out,poses,white,sx,4.48,sz+r,.5,.5,color);point(out,poses,white,ex,surface,ez+r,.5,.5,color);point(out,poses,white,ex,surface,ez-r,.5,.5,color);
        }
        if(blockProduct){if(!stack.isEmpty()){poses.pushPose();try{poses.translate(SynthesisPresentation.SCREEN_X,SynthesisPresentation.SCREEN_Y,SynthesisPresentation.SCREEN_Z-.01);double scale=SynthesisPresentation.screenPreviewScale(1,1,1);poses.scale((float)scale,(float)scale,.002F);poses.mulPose(Axis.YP.rotationDegrees((float)(now*1.4)));poses.translate(-.5,-.5,-.5);NativeBlockRecipePreview.render(stack,poses,buffers,FULLBRIGHT,OverlayTexture.NO_OVERLAY,1);}finally{poses.popPose();}}return;}
        if(visual.building()>=3)return;
        poses.pushPose();try{poses.translate(SynthesisPresentation.SCREEN_X,SynthesisPresentation.SCREEN_Y,SynthesisPresentation.SCREEN_Z-.01);double scale=SynthesisPresentation.screenPreviewScale(NativeBuilderModelCache.width(id),NativeBuilderModelCache.height(id),3);poses.scale((float)scale,(float)scale,.002F);poses.mulPose(Axis.YP.rotationDegrees((float)(now*1.4)));NativeBuilderModelCache.preview(id,poses,buffers,FULLBRIGHT);}finally{poses.popPose();}
    }
    private static void emitRack(String name,PoseStack poses,MultiBufferSource buffers,int light,int overlay){var model=NativeBuilderModelCache.rack(name);if(model!=null)model.emit(poses.last(),buffers.getBuffer(NativeBuilderModelCache.LAYER),light,overlay);}
    private static TextureAtlasSprite sprite(String id){return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(id));}
    private static void point(VertexConsumer target,PoseStack pose,TextureAtlasSprite sprite,double x,double y,double z,double u,double v,int color){var s=SCRATCH.get();pose.last().pose().transformPosition((float)x,(float)y,(float)z,s.position);pose.last().transformNormal(0,1,0,s.normal);target.addVertex(s.position.x,s.position.y,s.position.z).setColor(color).setUv(sprite.getU((float)u),sprite.getV((float)v)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT).setNormal(s.normal.x,s.normal.y,s.normal.z);}
    private NativeSynthesisEffects(){}
}
