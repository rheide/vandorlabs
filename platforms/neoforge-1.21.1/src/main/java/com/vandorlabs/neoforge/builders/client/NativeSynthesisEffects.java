package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.vandorlabs.machines.SynthesisPresentation;
import com.vandorlabs.rack.EmbeddedRackMotion;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
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
                boolean occupied=!visual.batteries().get(bay).isEmpty();int charge=NativeBatteryModule.energy(visual.batteries().get(bay))*4/NativeBatteryModule.CAPACITY;
                if(motion!=null){occupied=motion.occupied(bay);charge=motion.level(bay);}
                emitRack("indicator_"+bay+(occupied?"_on":"_off"),poses,buffers,light,overlay);
                if(!occupied)emitRack("slot_empty_"+bay,poses,buffers,light,overlay);
                if(motion==null?occupied:motion.visible(bay,now)){poses.pushPose();try{if(motion!=null)poses.translate(0,0,motion.offset(bay,now));emitRack((motion!=null&&motion.moving(bay,now)?"slot_battery_":"slot_seated_battery_")+bay+"_"+charge,poses,buffers,light,overlay);}finally{poses.popPose();}}
            }
        }finally{poses.popPose();}
        if(!visual.chip().isEmpty()){
            poses.pushPose();try{poses.translate(SynthesisPresentation.CHIP_X,SynthesisPresentation.CHIP_Y,SynthesisPresentation.CHIP_Z-.07);poses.scale(.35F,.35F,.35F);poses.mulPose(Axis.YP.rotationDegrees(180));Minecraft.getInstance().getItemRenderer().renderStatic(visual.chip(),ItemDisplayContext.FIXED,light,overlay,poses,buffers,Minecraft.getInstance().level,0);}finally{poses.popPose();}
        }
        if(visual.connected()){
            var sprite=sprite("vandorlabs:blocks/resource_ports/power_in_on");var out=buffers.getBuffer(NativeBuilderModelCache.LAYER);
            point(out,poses,sprite,2,0,3.002,0,1,0xffffffff);point(out,poses,sprite,3,0,3.002,1,1,0xffffffff);point(out,poses,sprite,3,1,3.002,1,0,0xffffffff);point(out,poses,sprite,2,1,3.002,0,0,0xffffffff);
            point(out,poses,sprite,2,-.002,2,0,0,0xffffffff);point(out,poses,sprite,3,-.002,2,1,0,0xffffffff);point(out,poses,sprite,3,-.002,3,1,1,0xffffffff);point(out,poses,sprite,2,-.002,3,0,1,0xffffffff);
        }
    }
    public static void processing(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,double now){
        if(!visual.processing()&&visual.progress()<=0)return;
        String id=NativeBuilderModelCache.IDS.get(Math.clamp(visual.building(),0,2));float progress=visual.processing()?visual.progress():1;
        poses.pushPose();try{poses.translate(1,2.125,1.125);NativeBuilderModelCache.mesh(id,true).emitClipped(poses.last(),buffers.getBuffer(NativeBuilderModelCache.LAYER),FULLBRIGHT,OverlayTexture.NO_OVERLAY,progress);}finally{poses.popPose();}
        if(!visual.processing())return;
        var white=sprite("minecraft:block/white_concrete");var out=buffers.getBuffer(NativeBuilderModelCache.LAYER);double surface=2.125+Math.clamp(progress,.001F,1F)+.008;
        if(visual.working())for(int i=0;i<4;i++){
            double ex=1.5+.46*Math.sin(now*.12+i*Math.PI/2),ez=1.625+.46*Math.sin(now*.075+i*Math.PI),sx=i%2==0?.70:2.30,sz=i<2?.95:2.30,r=.014;
            point(out,poses,white,sx-r,4.48,sz,.5,.5,0xffff4132);point(out,poses,white,sx+r,4.48,sz,.5,.5,0xffff4132);point(out,poses,white,ex+r,surface,ez,.5,.5,0xffff4132);point(out,poses,white,ex-r,surface,ez,.5,.5,0xffff4132);
            point(out,poses,white,sx,4.48,sz-r,.5,.5,0xffff4132);point(out,poses,white,sx,4.48,sz+r,.5,.5,0xffff4132);point(out,poses,white,ex,surface,ez+r,.5,.5,0xffff4132);point(out,poses,white,ex,surface,ez-r,.5,.5,0xffff4132);
        }
        poses.pushPose();try{poses.translate(SynthesisPresentation.SCREEN_X,SynthesisPresentation.SCREEN_Y,SynthesisPresentation.SCREEN_Z-.01);double scale=SynthesisPresentation.SCREEN_SIZE*1.3/Math.sqrt(Math.pow(NativeBuilderModelCache.width(id),2)+Math.pow(NativeBuilderModelCache.height(id),2)+9);poses.scale((float)scale,(float)scale,.002F);poses.mulPose(Axis.YP.rotationDegrees((float)(now*1.4)));NativeBuilderModelCache.preview(id,poses,buffers,FULLBRIGHT);}finally{poses.popPose();}
    }
    private static void emitRack(String name,PoseStack poses,MultiBufferSource buffers,int light,int overlay){var model=NativeBuilderModelCache.rack(name);if(model!=null)model.emit(poses.last(),buffers.getBuffer(NativeBuilderModelCache.LAYER),light,overlay);}
    private static TextureAtlasSprite sprite(String id){return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(id));}
    private static void point(VertexConsumer target,PoseStack pose,TextureAtlasSprite sprite,double x,double y,double z,double u,double v,int color){var s=SCRATCH.get();pose.last().pose().transformPosition((float)x,(float)y,(float)z,s.position);pose.last().transformNormal(0,1,0,s.normal);target.addVertex(s.position.x,s.position.y,s.position.z).setColor(color).setUv(sprite.getU((float)u),sprite.getV((float)v)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT).setNormal(s.normal.x,s.normal.y,s.normal.z);}
    private NativeSynthesisEffects(){}
}
