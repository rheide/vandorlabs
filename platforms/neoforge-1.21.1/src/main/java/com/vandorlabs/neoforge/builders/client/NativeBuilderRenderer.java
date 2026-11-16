package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.animation.VisualClock;
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
    public static void clear(){clock=new VisualClock();}
    public static double now(){return clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static void orient(PoseStack poses,Direction facing,int width,int depth){switch(facing){case EAST-> {poses.translate(depth,0,0);poses.mulPose(Axis.YP.rotationDegrees(-90));}case SOUTH->{poses.translate(width,0,depth);poses.mulPose(Axis.YP.rotationDegrees(180));}case WEST->{poses.translate(0,0,width);poses.mulPose(Axis.YP.rotationDegrees(90));}default->{}}}
    @Override public void render(NativeBuilderEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.isRemoved()||tile.inert()||tile.getLevel()==null||!tile.formed()||!NativeBuilderModelCache.ready())return;
        renderVisual(tile.visual(),poses,buffers,light,overlay,now(),nearby(tile));
    }
    private static boolean nearby(NativeBuilderEntity tile){var camera=Minecraft.getInstance().getCameraEntity();return camera!=null&&camera.distanceToSqr(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5,tile.getBlockPos().getZ()+.5)<=24*24;}
    /** Shared immutable path also serves isolated rendered regressions. */
    public static void renderVisual(NativeBuilderEntity.Visual visual,PoseStack poses,MultiBufferSource buffers,int light,int overlay,double now,boolean nearby){
        if(!visual.formed()||!NativeBuilderModelCache.ready())return;poses.pushPose();try{
            poses.translate(-visual.pivot().getX(),-visual.pivot().getY(),-visual.pivot().getZ());orient(poses,visual.facing(),NativeBuilderModelCache.width(visual.design()),3);
            NativeBuilderModelCache.mesh(visual.design(),false).emit(poses.last(),buffers.getBuffer(NativeBuilderModelCache.LAYER),light,overlay);
            if(visual.design().equals("synthesis_tower")){NativeSynthesisEffects.mounted(visual,poses,buffers,light,overlay);if(nearby)NativeSynthesisEffects.processing(visual,poses,buffers,now);}
        }finally{poses.popPose();}
    }
    @Override public int getViewDistance(){return 64;}
    @Override public AABB getRenderBoundingBox(NativeBuilderEntity tile){var p=tile.getBlockPos().subtract(tile.pivot());int width=NativeBuilderModelCache.width(tile.design().id()),depth=3;if(tile.facing().getAxis()==Direction.Axis.X){int swap=width;width=depth;depth=swap;}return new AABB(p.getX(),p.getY(),p.getZ(),p.getX()+width,p.getY()+NativeBuilderModelCache.height(tile.design().id()),p.getZ()+depth);}
}
