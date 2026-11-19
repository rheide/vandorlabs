package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.seating.NativeChairEntity;
import com.vandorlabs.neoforge.seating.NativeCockpitLift;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Only the stock detachable bank and button are submitted here. The static hull remains chunk geometry. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeCockpitLiftRenderer implements BlockEntityRenderer<NativeChairEntity> {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<NativeChairEntity,Pose> POSES=new WeakHashMap<>();
    private static double clock;

    NativeCockpitLiftRenderer(BlockEntityRendererProvider.Context context) {
        NativeChairEntity.bindLiftClientClock(()->clock);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){
        clock=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());
    }

    public static double visualProgress(NativeChairEntity owner){
        return POSES.computeIfAbsent(owner,unused->new Pose()).sample(owner,clock);
    }

    private static final class Pose {
        private long revision=-1;
        private double from,started,buttonStarted;
        private boolean goal;

        double sample(NativeChairEntity tile,double now){
            var motion=tile.lift();
            if(revision<0){
                from=motion.from();
                started=tile.liftReceivedAt()-tile.liftElapsed();
                goal=motion.retracted();
                buttonStarted=Double.NEGATIVE_INFINITY;
            }else if(motion.revision()>revision){
                double received=Math.max(started,tile.liftReceivedAt());
                from=at(received);
                started=received;
                goal=motion.retracted();
                buttonStarted=received;
            }
            revision=Math.max(revision,motion.revision());
            return at(now);
        }

        private double at(double now){return NativeCockpitLift.interpolate(from,goal,(now-started)/NativeCockpitLift.DURATION);}
        double button(double now){return .028125*Math.max(0,Math.min(1,(buttonStarted+8-now)/4));}
    }

    @Override public void render(NativeChairEntity tile,float partial,PoseStack stack,MultiBufferSource buffers,int light,int overlay){
        if(tile.inert()||tile.isRemoved()||tile.getLevel()==null||NativeSeatBlock.proxy(tile.getBlockState()))return;
        double bank=visualProgress(tile);
        var pose=POSES.get(tile);
        var facing=tile.getBlockState().getValue(NativeSeatBlock.FACING);
        var buffer=buffers.getBuffer(RenderType.cutout());
        stack.pushPose();
        try{
            stack.translate(.5,0,.5);
            stack.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
            stack.translate(-.5,0,-.5);
            part(false,bank*NativeCockpitLift.TRAVEL,stack,buffer,light,overlay);
            part(true,pose.button(clock),stack,buffer,light,overlay);
        }finally{stack.popPose();}
    }

    private static void part(boolean button,double drop,PoseStack stack,VertexConsumer buffer,int light,int overlay){
        stack.pushPose();
        try{
            stack.translate(0,-drop,0);
            for(var quad:NativeSeatModels.cockpitPart(button)){
                var direction=quad.getDirection();
                float shade=direction.getAxis()==Direction.Axis.Y?(direction==Direction.UP?1:.5F)
                    :direction.getAxis()==Direction.Axis.X?.6F:.8F;
                buffer.putBulkData(stack.last(),quad,shade,shade,shade,1,light,overlay);
            }
        }finally{stack.popPose();}
    }

    @Override public AABB getRenderBoundingBox(NativeChairEntity tile){return new AABB(tile.getBlockPos()).inflate(1.5,1,1.5);}
}
