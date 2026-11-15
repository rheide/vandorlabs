package com.vandorlabs.neoforge.rack.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.rack.ModuleRackMotion;
import com.vandorlabs.neoforge.rack.*;
import com.vandorlabs.neoforge.client.NativeSegmentedSectionUploads;
import com.vandorlabs.neoforge.mixin.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** No idle BER: only active bounded module timelines enter the ordinary solid stage. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRackAnimation {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<BlockPos,Entry> ACTIVE=new LinkedHashMap<>();
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private static ClientLevel world;
    private static double time;
    private static long draws,starts,uploads;
    public static long[] stats(){return new long[]{ACTIVE.size(),draws,starts,uploads};}
    private static double now(){return CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    private static void reset(){for(var entry:ACTIVE.values()){entry.close();if(entry.tile.getLevel()==world)entry.tile.renderMask(entry.tile.occupied());}ACTIVE.clear();if(storage!=null){storage.close();storage=null;buffers=null;}world=Minecraft.getInstance().level;}
    public static void reload(){reset();}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world){reset();world=null;}}
    public static void updated(NativeRackEntity tile,int previous,int previousKinds,long[] before,boolean initial){
        var mc=Minecraft.getInstance();if(tile.getLevel()!=mc.level||mc.level.getBlockEntity(tile.getBlockPos())!=tile)return;if(!mc.isSameThread()){mc.execute(()->updated(tile,previous,previousKinds,before,initial));return;}
        if(world!=mc.level)reset();time=now();var pos=tile.getBlockPos();var entry=ACTIVE.get(pos);
        if(entry!=null&&entry.incarnation!=tile.incarnation()){entry.close();ACTIVE.remove(pos);entry=null;}
        if(entry!=null)entry.tile=tile;
        for(int slot=0;slot<4;slot++){
            boolean extracting=(tile.occupied()&1<<slot)==0;var old=entry==null?null:entry.poses[slot];long sequence=tile.sequence(slot);
            if(old!=null&&old.sequence==sequence&&old.extracting==extracting)continue;
            if(!ModuleRackMotion.starts(initial,previous,tile.occupied(),slot,before[slot],sequence))continue;
            if(extracting&&old!=null&&old.waiting&&!old.extracting){entry.poses[slot]=null;continue;}
            double from=old==null?extracting?0:ModuleRackMotion.TRAVEL_BLOCKS:old.offset(time);
            if(entry==null){entry=new Entry(tile);ACTIVE.put(pos.immutable(),entry);}
            int kind=extracting?old==null?(previousKinds>>>(slot*2)&3):old.kind:tile.kind(slot);
            entry.poses[slot]=new Pose(sequence,time,extracting,from,kind);starts++;
        }
        if(entry==null){tile.renderMask(tile.occupied());return;}
        if(!entry.any()){entry.close();ACTIVE.remove(pos);tile.renderMask(tile.occupied());return;}entry.publish();
        while(ACTIVE.size()>2048){var iterator=ACTIVE.entrySet().iterator();var oldest=iterator.next().getValue();oldest.close();oldest.tile.renderMask(oldest.tile.occupied());iterator.remove();}
    }
    private static final class Pose {
        final long sequence;final boolean extracting;final double from;final int kind;double started;boolean waiting=true,seated;
        Pose(long sequence,double time,boolean extracting,double from,int kind){this.sequence=sequence;this.started=time;this.extracting=extracting;this.from=from;this.kind=kind;}
        double offset(double time){return waiting?from:ModuleRackMotion.between(time-started,from,extracting?ModuleRackMotion.TRAVEL_BLOCKS:0);}
    }
    private static final class Entry {
        NativeRackEntity tile;final AABB bounds;final long incarnation;final Pose[] poses=new Pose[4];Publication publication;int mask=-1,occupied=-1;double changed=time;
        Entry(NativeRackEntity tile){this.tile=tile;bounds=new AABB(tile.getBlockPos()).inflate(ModuleRackMotion.TRAVEL_BLOCKS);incarnation=tile.incarnation();}
        boolean valid(){var pos=tile.getBlockPos();var chunk=world==null?null:world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var current=chunk==null?null:chunk.getBlockEntities().get(pos);if(!(current instanceof NativeRackEntity replacement)||replacement.incarnation()!=incarnation||replacement.isRemoved())return false;tile=replacement;return true;}
        boolean any(){for(var pose:poses)if(pose!=null)return true;return false;}
        void close(){if(publication!=null)publication.close();publication=null;}
        void publish(){int desired=tile.occupied();for(int slot=0;slot<4;slot++)if(poses[slot]!=null&&!poses[slot].seated)desired&=~(1<<slot);if(publication==null||mask!=desired||occupied!=tile.occupied()){if(publication!=null)publication.close();publication=new Publication(tile.getBlockPos());mask=desired;occupied=tile.occupied();tile.renderMask(desired);publication.dirty();changed=time;}else tile.renderMask(desired);}
        void acknowledge(){if(publication==null||!publication.uploaded())return;for(int slot=0;slot<4;slot++){var pose=poses[slot];if(pose==null)continue;if(pose.waiting&&(mask&1<<slot)==0){pose.waiting=false;pose.started=time;uploads++;}if(pose.seated&&(mask&1<<slot)!=0)poses[slot]=null;}}
    }
    private static final class Publication {
        final BlockPos origin;SectionRenderDispatcher.RenderSection section;Object compiled,task;boolean requested;NativeSegmentedSectionUploads.Observation observation;
        Publication(BlockPos pos){origin=new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15);bind();}
        void bind(){var view=((SegmentedLevelRendererAccessor)Minecraft.getInstance().levelRenderer).vandorlabs$segmentedViewArea();var candidate=view==null?null:((SegmentedViewAreaInvoker)view).vandorlabs$segmentedSection(origin);if(candidate!=null&&!candidate.getOrigin().equals(origin))candidate=null;if(candidate!=section){if(observation!=null)observation.close();observation=null;section=candidate;if(section!=null)observation=NativeSegmentedSectionUploads.observe((NativeSegmentedSectionUploads.Section)section);compiled=section==null?null:section.getCompiled();task=section==null?null:((NativeSegmentedSectionUploads.Section)section).vandorlabs$terrainTask();requested=false;}}
        void close(){if(observation!=null)observation.close();observation=null;}
        void dirty(){if(section!=null)section.setDirty(false);}
        boolean uploaded(){bind();return section!=null&&NativeSegmentedSectionUploads.observedUpload((NativeSegmentedSectionUploads.Section)section,compiled,task);}
        void submit(){bind();if(section==null||requested)return;var dispatcher=((SegmentedLevelRendererAccessor)Minecraft.getInstance().levelRenderer).vandorlabs$segmentedDispatcher();if(dispatcher==null)return;section.setDirty(false);section.rebuildSectionAsync(dispatcher,new RenderRegionCache());section.setNotDirty();requested=true;}
    }
    private static void advance(){var mc=Minecraft.getInstance();if(mc.level!=world){reset();return;}if(ACTIVE.isEmpty())return;time=now();var iterator=ACTIVE.values().iterator();while(iterator.hasNext()){var entry=iterator.next();if(!entry.valid()){entry.close();iterator.remove();continue;}entry.acknowledge();for(int slot=0;slot<4;slot++){var pose=entry.poses[slot];if(pose==null)continue;if((entry.tile.occupied()&1<<slot)==0!=pose.extracting||entry.tile.sequence(slot)!=pose.sequence){entry.poses[slot]=null;continue;}if(!pose.waiting&&time-pose.started>=ModuleRackMotion.DURATION_TICKS){if(pose.extracting)entry.poses[slot]=null;else pose.seated=true;}}entry.publish();if(time-entry.changed>60){entry.close();entry.tile.renderMask(entry.tile.occupied());iterator.remove();continue;}entry.publication.submit();entry.acknowledge();if(!entry.any()){entry.close();iterator.remove();}}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){advance();}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS||ACTIVE.isEmpty())return;advance();if(ACTIVE.isEmpty())return;var mc=Minecraft.getInstance();if(buffers==null){storage=new ByteBufferBuilder(65536);buffers=MultiBufferSource.immediate(storage);}var buffer=buffers.getBuffer(RenderType.solid());int batchVertices=0;var camera=event.getCamera().getPosition();var stack=event.getPoseStack();var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();modelView.mul(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();try{for(var entry:ACTIVE.values()){
        var pos=entry.tile.getBlockPos();if(!event.getFrustum().isVisible(entry.bounds))continue;var face=entry.tile.getBlockState().getValue(NativeRackBlock.FACING);var model=NativeRackClient.facing(face);if(model==null)continue;int light=LevelRenderer.getLightColor(world,pos);
        for(int slot=0;slot<4;slot++){var pose=entry.poses[slot];if(pose==null||pose.waiting)continue;double offset=pose.offset(time);stack.pushPose();stack.translate(pos.getX()-camera.x+face.getStepX()*offset,pos.getY()-camera.y+face.getStepY()*offset,pos.getZ()-camera.z+face.getStepZ()*offset);for(var quad:model.moving(slot,pose.kind)){int[] raw=quad.getVertices();int stride=raw.length/4;for(int vertex=0;vertex<4;vertex++){int at=vertex*stride,normal=raw[at+7];float nx=(byte)normal/127F,ny=(byte)(normal>>>8)/127F,nz=(byte)(normal>>>16)/127F,shade=nx*nx*.6F+ny*ny*(ny>0?1:.5F)+nz*nz*.8F;buffer.addVertex(stack.last().pose(),Float.intBitsToFloat(raw[at]),Float.intBitsToFloat(raw[at+1]),Float.intBitsToFloat(raw[at+2])).setColor(shade,shade,shade,1).setUv(Float.intBitsToFloat(raw[at+4]),Float.intBitsToFloat(raw[at+5])).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),nx,ny,nz);}batchVertices+=4;if(batchVertices>=1024){buffers.endBatch(RenderType.solid());buffer=buffers.getBuffer(RenderType.solid());batchVertices=0;}}stack.popPose();draws++;}
    }buffers.endBatch(RenderType.solid());}finally{modelView.popMatrix();RenderSystem.applyModelViewMatrix();}}
    private NativeRackAnimation(){}
}
