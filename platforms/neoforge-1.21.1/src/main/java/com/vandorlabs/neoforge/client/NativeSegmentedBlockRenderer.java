package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.persistence.SegmentedWindowLayout;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Root-only adapter. Settled terrain columns perform no row lighting or body emission. */
public final class NativeSegmentedBlockRenderer implements BlockEntityRenderer<NativeSegmentedDoorEntity> {
    private static final int BUDGET=1024*1024;
    private static final class Presentation {
        final NativeSegmentedDoorEntity.Publication source;final NativeSegmentedTerrainSnapshot.ClientProjection client;
        final NativeSegmentedVisual visual;final AABB bounds;NativeSegmentedVisual.Motion motion;
        Presentation(NativeSegmentedDoorEntity.Publication source,NativeSegmentedTerrainSnapshot.ClientProjection client,NativeSegmentedVisual visual,AABB bounds,NativeSegmentedVisual.Motion motion){this.source=source;this.client=client;this.visual=visual;this.bounds=bounds;this.motion=motion;}
        int bytes(){return 512+visual.height()*128;}
    }
    // Weak-key values contain only immutable world-free publication/geometry, never their tile key.
    private static final WeakHashMap<NativeSegmentedDoorEntity,Presentation> PRESENTATIONS=new WeakHashMap<>();
    private static long calls,draws,lightSamples;
    public record Stats(long calls,long bodyDraws,long lightSamples){}
    public static Stats stats(){return new Stats(calls,draws,lightSamples);}
    private final int[] lights=new int[32];private final BlockPos.MutableBlockPos lightPos=new BlockPos.MutableBlockPos();
    public NativeSegmentedBlockRenderer(BlockEntityRendererProvider.Context context){}
    private static Presentation presentation(NativeSegmentedDoorEntity tile){
        var source=tile.publication();if(source==null)return null;var client=tile.clientTerrain();var found=PRESENTATIONS.get(tile);if(found!=null&&found.source==source&&found.client==client&&found.visual.facing()==tile.facing())return found;
        var original=source.visual();NativeSegmentedVisual visual;
        if(tile.inert())visual=new NativeSegmentedVisual(1,1,0,original.facing(),-1,NativeSegmentedDoorSettings.LEGACY,List.of(NativeSegmentedDoorSettings.LEGACY),List.of(SegmentedWindowLayout.SINGLE),true,false,false,true,client.headerTerrain());
        else visual=new NativeSegmentedVisual(original.width(),original.height(),original.column(),tile.facing(),original.texture(),original.settings(),original.rows(),original.windows(),original.ready(),client.suppressed(),client.awaitingChunk(),NativeSegmentedTerrainSnapshot.eligible(original),client.headerTerrain(),original.artwork());
        var bounds=found!=null&&found.source==source&&found.visual.facing()==tile.facing()?found.bounds:NativeSegmentedRenderer.bounds(tile.getBlockPos(),visual);
        found=new Presentation(source,client,visual,bounds,found!=null&&found.source==source?found.motion:null);PRESENTATIONS.put(tile,found);
        int retained=0;for(var value:PRESENTATIONS.values())retained+=value.bytes();var iterator=PRESENTATIONS.entrySet().iterator();while(retained>BUDGET&&iterator.hasNext()){var removed=iterator.next();retained-=removed.getValue().bytes();iterator.remove();}
        return found;
    }
    private static NativeSegmentedVisual.Motion observe(NativeSegmentedDoorEntity tile,Presentation p){
        if(p.motion==null){var source=p.source.motion();p.motion=new NativeSegmentedVisual.Motion(source.identity(),source.motionIdentity(),source.sequence(),source.targetOpen(),tile.pose(0),source.ticksPerSection(),source.started(),source.duration());}return p.motion;
    }
    public static NativeSegmentedVisual visual(NativeSegmentedDoorEntity tile){var p=presentation(tile);return p==null?null:p.visual;}
    public static NativeSegmentedVisual.Motion motion(NativeSegmentedDoorEntity tile){var p=presentation(tile);return p==null?null:observe(tile,p);}
    public static double selectionPoseWithoutPolling(NativeSegmentedDoorEntity tile){if(tile.inert()||tile.clientTerrain().suppressed()||tile.clientTerrain().awaitingChunk())return 0;var p=presentation(tile);return p==null?0:NativeSegmentedRenderer.selectionPose(p.visual,observe(tile,p),tile.getLevel(),tile.getBlockPos());}
    public static double selectionPose(NativeSegmentedDoorEntity tile){NativeSegmentedChunks.pollReady(tile);return selectionPoseWithoutPolling(tile);}
    @Override public void render(NativeSegmentedDoorEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.topOffset()!=0||tile.getLevel()==null)return;calls++;NativeSegmentedChunks.pollReady(tile);
        if(tile.clientTerrain().suppressed()&&tile.settings().direction==0&&tile.settings().position==0)return;
        var p=presentation(tile);if(p==null||!p.visual.ready())return;var pos=tile.getBlockPos();
        for(int row=0;row<p.visual.height();row++){lights[row]=LevelRenderer.getLightColor(tile.getLevel(),lightPos.set(pos.getX(),pos.getY()-row,pos.getZ()));lightSamples++;}draws++;
        var gates=NativeSegmentedGateContexts.prepare(tile);
        if(tile.inert()||p.visual.awaitingChunk())NativeSegmentedRenderer.renderClosed(p.visual,poses,buffers,lights,overlay,gates.context,gates.leftPose(),gates.rightPose());
        else NativeSegmentedRenderer.render(p.visual,observe(tile,p),tile.getLevel(),pos,poses,buffers,lights,overlay,gates.context,gates.leftPose(),gates.rightPose());
    }
    @Override public boolean shouldRenderOffScreen(NativeSegmentedDoorEntity tile){return tile.topOffset()==0;}
    @Override public int getViewDistance(){return 256;}
    @Override public AABB getRenderBoundingBox(NativeSegmentedDoorEntity tile){var p=presentation(tile);return p==null?new AABB(tile.getBlockPos()):p.bounds;}
    @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Lifecycle {
        @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())PRESENTATIONS.clear();}
    }
}
