package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.rack.EmbeddedRackMotion;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import java.util.*;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** One controller draws one cached complete frame, with no part renderers or per-frame world scans. */
public final class NativeBuilderRenderer implements BlockEntityRenderer<NativeBuilderEntity> {
    private static VisualClock clock=new VisualClock();
    public NativeBuilderRenderer(BlockEntityRendererProvider.Context context){}
    private record Rack(java.util.UUID identity,EmbeddedRackMotion motion,com.vandorlabs.machines.BuilderFabricationPose fabrication){}
    private static final Map<net.minecraft.world.level.Level,LinkedHashMap<net.minecraft.core.BlockPos,Rack>> racks=new WeakHashMap<>();
    public static void clear(){clock=new VisualClock();racks.clear();NativeBuilderProgressMotion.clear();NativeBuilderPartFabrication.clear();NativeDriveRenderer.clear();}
    private static Rack motion(NativeBuilderEntity tile,NativeBuilderEntity.Visual visual,double now){
        var values=racks.computeIfAbsent(tile.getLevel(),ignored->new LinkedHashMap<>(16,.75F,true));var rack=values.get(tile.getBlockPos());if(rack==null||!rack.identity.equals(tile.key())){rack=new Rack(tile.key(),new EmbeddedRackMotion(),new com.vandorlabs.machines.BuilderFabricationPose());values.put(tile.getBlockPos().immutable(),rack);if(values.size()>256)values.remove(values.keySet().iterator().next());}
        int mask=0;for(int bay=0;bay<4;bay++)if(!visual.batteries().get(bay).isEmpty())mask|=1<<bay;
        rack.motion.observe(mask,visual.batterySequences().get(0),visual.batterySequences().get(1),visual.batterySequences().get(2),visual.batterySequences().get(3),level(visual,0),level(visual,1),level(visual,2),level(visual,3),now);rack.fabrication.observe(now,visual.working());return rack;
    }
    private static int level(NativeBuilderEntity.Visual visual,int bay){return com.vandorlabs.rack.BatteryChargeBar.pixels(NativeBatteryModule.energy(visual.batteries().get(bay)),NativeBatteryModule.CAPACITY);}
    public static void observe(NativeBuilderEntity tile){if(tile.getLevel()!=null){
        if(tile.production())motion(tile,tile.visual(),now());
        else if(tile.drive()==com.vandorlabs.neoforge.builders.NativeDriveDesign.OCULUS)NativeDriveRenderer.observe(tile,now());
    }}
    public static double now(){return clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static void orient(PoseStack poses,Direction facing,int width,int depth){switch(facing){case EAST-> {poses.translate(depth,0,0);poses.mulPose(Axis.YP.rotationDegrees(-90));}case SOUTH->{poses.translate(width,0,depth);poses.mulPose(Axis.YP.rotationDegrees(180));}case WEST->{poses.translate(0,0,width);poses.mulPose(Axis.YP.rotationDegrees(90));}default->{}}}
    @Override public void render(NativeBuilderEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.isRemoved()||tile.inert()||tile.getLevel()==null||!tile.formed()||!NativeBuilderModelCache.ready())return;
        if(tile.drive()!=null){NativeDriveRenderer.render(tile,poses,buffers,light,overlay,now());return;}
        var visual=tile.visual();double time=now();renderVisual(visual,poses,buffers,light,overlay,time,nearby(tile),motion(tile,visual,time),tile.synthesis()?NativeBuilderProgressMotion.sample(tile,visual,time):null);
    }
    private static boolean nearby(NativeBuilderEntity tile){var camera=Minecraft.getInstance().getCameraEntity();return camera!=null&&camera.distanceToSqr(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5,tile.getBlockPos().getZ()+.5)<=24*24;}
    /** Shared immutable path also serves isolated rendered regressions. */
    public static void renderVisual(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,int light,int overlay,double now,boolean nearby){
        renderVisual(visual,poses,buffers,light,overlay,now,nearby,null,null);
    }
    private static void renderVisual(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,int light,int overlay,double now,boolean nearby,Rack motion,NativeBuilderProgressMotion.State progress){
        if(!visual.formed()||!NativeBuilderModelCache.ready())return;poses.pushPose();try{
            poses.translate(-visual.pivot().getX(),-visual.pivot().getY(),-visual.pivot().getZ());orient(poses,visual.facing(),NativeBuilderModelCache.width(visual.design()),3);
            double work=motion==null?(visual.working()?now:0):motion.fabrication.phase();
            var geometry=buffers.getBuffer(NativeBuilderModelCache.LAYER);
            if(visual.design().equals("enclosed_gantry")){
                NativeBuilderModelCache.gantryMesh("static").emit(poses.last(),geometry,light,overlay);
                double arm=com.vandorlabs.machines.BuilderFabricationPose.armZ(work);
                poses.pushPose();try{
                    poses.translate(0,0,arm);
                    NativeBuilderModelCache.gantryMesh("gantry_arm").emit(poses.last(),geometry,light,overlay);
                    poses.translate(com.vandorlabs.machines.BuilderFabricationPose.headX(work),0,0);
                    NativeBuilderModelCache.gantryMesh("gantry_head").emit(poses.last(),geometry,light,overlay);
                }finally{poses.popPose();}
            }else NativeBuilderModelCache.mesh(visual.design(),false).emit(poses.last(),geometry,light,overlay);
            NativeSynthesisEffects.mounted(visual,poses,buffers,light,overlay,motion==null?null:motion.motion,now);
            if(nearby){
                if(visual.design().equals("synthesis_tower"))NativeSynthesisEffects.processing(visual,poses,buffers,now,progress);
                else NativeSynthesisEffects.partProcessing(visual,poses,buffers,now,work);
            }
        }finally{poses.popPose();}
    }
    @Override public int getViewDistance(){return 64;}
    @Override public AABB getRenderBoundingBox(NativeBuilderEntity tile){var p=tile.getBlockPos().subtract(tile.pivot());var shape=tile.layout();return new AABB(p.getX(),p.getY(),p.getZ(),p.getX()+shape.extentX(),p.getY()+shape.height,p.getZ()+shape.extentZ()).inflate(.5);}
}
