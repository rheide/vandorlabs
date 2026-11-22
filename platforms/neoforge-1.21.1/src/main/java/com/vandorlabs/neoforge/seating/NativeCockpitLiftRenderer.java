package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.seating.NativeChairEntity;
import com.vandorlabs.neoforge.seating.NativeCockpitSurfaceEdit;
import com.vandorlabs.neoforge.seating.NativeCockpitThrottle;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import com.vandorlabs.neoforge.client.NativeMaterialPlayback;
import com.vandorlabs.neoforge.screen.NativeYokeGeometry;
import net.minecraft.world.inventory.InventoryMenu;
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

    private static final int FULL_LIGHT=0x00F000F0;
    private final NativeScreenRowsRenderer touch;
    private final Map<Integer,NativeMaterialPlayback.Material> artwork=new java.util.LinkedHashMap<>(32,.75F,true);
    NativeCockpitLiftRenderer(BlockEntityRendererProvider.Context context) {
        touch=new NativeScreenRowsRenderer(context.getFont());
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
        double bank=0;
        var facing=tile.getBlockState().getValue(NativeSeatBlock.FACING);
        if(tile.kind()==NativeSeatKind.COCKPIT){
            bank=visualProgress(tile);
            var pose=POSES.get(tile);
            var buffer=buffers.getBuffer(RenderType.cutout());
            stack.pushPose();
            try{
                stack.translate(.5,0,.5);
                stack.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
                stack.translate(-.5,0,-.5);
                part(false,bank*NativeCockpitLift.TRAVEL,stack,buffer,light,overlay);
                part(true,pose.button(clock),stack,buffer,light,overlay);
                for(int side=0;side<tile.throttleCount();side++){
                    stack.pushPose();
                    try{
                        var pivot=NativeCockpitThrottle.pivot(side,tile.throttleCount());
                        stack.translate(pivot.x,pivot.y,pivot.z);
                        stack.mulPose(Axis.XP.rotationDegrees((float)NativeCockpitThrottle.angle(side,tile.throttleStep(side),tile.throttleCount())));
                        quads(NativeSeatModels.cockpitThrottle(side),stack,buffer,light,overlay);
                    }finally{stack.popPose();}
                }
            }finally{stack.popPose();}
        }
        var panel=tile.cockpitTouch();
        if(panel==null)return;
        for(int slot=0;slot<panel.controlSlots();slot++){
            var face=panel.surface(facing,slot,bank);
            if(face==null||!panel.active(slot))continue;
            var settings=panel.settings(slot);
            int pixels=panel.visibleHeight(face,slot);
            if(pixels==0||settings.playback()==0)continue;
            if(settings.artwork()==NativeCockpitSurfaceEdit.REDSTONE)
                touch.emitCockpit(face,panel.controls(slot),stack,buffers,FULL_LIGHT,tile.getLevel().getGameTime(),pixels);
            else{
                int choice=settings.artwork()==NativeCockpitSurfaceEdit.ORIGINAL?panel.originalArtwork(slot):settings.artwork();
                if(choice>=0)artwork(choice,settings,face,pixels,stack,buffers,overlay);
            }
        }
    }

    private void artwork(int choice,NativeCockpitSurfaceEdit settings,NativeYokeGeometry.Face face,int pixels,
                         PoseStack stack,MultiBufferSource buffers,int overlay){
        var entry=ModernFinishCatalog.entry(choice);
        if(!artwork.containsKey(choice)){
            artwork.put(choice,NativeMaterialPlayback.capture(entry));
            if(artwork.size()>256)artwork.remove(artwork.keySet().iterator().next());
        }
        var playback=artwork.get(choice);
        VertexConsumer output;
        float u1,u2,v1,v2;
        float visible=pixels/(float)face.height();
        if(playback!=null){
            int mode=settings.playback(),frame=playback.frame(mode,settings.speed(),NativeMaterialPlayback.tick());
            output=buffers.getBuffer(RenderType.entityCutoutNoCull(playback.texture(mode)));
            u1=0;u2=1;
            v1=(float)playback.top(mode,frame);
            v2=v1+((float)playback.bottom(mode,frame)-v1)*visible;
        }else{
            var atlas=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            var sprite=atlas.apply(net.minecraft.resources.ResourceLocation.parse(entry.texture()));
            output=buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            u1=sprite.getU0();u2=sprite.getU(entry.u());
            v1=sprite.getV0();v2=sprite.getV(entry.v()*visible);
        }
        var origin=face.topLeft();
        var right=face.right();
        var down=face.down().scale(visible);
        var normal=face.normal();
        var pose=stack.last();
        for(int corner=0;corner<4;corner++){
            boolean lower=corner<2,far=corner==1||corner==2;
            output.addVertex(pose.pose(),(float)(origin.x+(far?right.x:0)+(lower?down.x:0)),
                    (float)(origin.y+(far?right.y:0)+(lower?down.y:0)),(float)(origin.z+(far?right.z:0)+(lower?down.z:0)))
                .setColor(-1).setUv(far?u2:u1,lower?v2:v1).setLight(FULL_LIGHT).setOverlay(overlay)
                .setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
        }
    }

    private static void part(boolean button,double drop,PoseStack stack,VertexConsumer buffer,int light,int overlay){
        stack.pushPose();
        try{
            stack.translate(0,-drop,0);
            quads(NativeSeatModels.cockpitPart(button),stack,buffer,light,overlay);
        }finally{stack.popPose();}
    }

    private static void quads(java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                              PoseStack stack,VertexConsumer buffer,int light,int overlay){
        for(var quad:quads){
            var direction=quad.getDirection();
            float shade=direction.getAxis()==Direction.Axis.Y?(direction==Direction.UP?1:.5F)
                :direction.getAxis()==Direction.Axis.X?.6F:.8F;
            buffer.putBulkData(stack.last(),quad,shade,shade,shade,1,light,overlay);
        }
    }

    @Override public AABB getRenderBoundingBox(NativeChairEntity tile){return new AABB(tile.getBlockPos()).inflate(1.5,1,1.5);}
}
