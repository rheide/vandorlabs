package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.persistence.SegmentedWindowLayout;
import java.util.List;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Root-only world adapter; geometry, motion and material caches stay in the common draw backend. */
public final class NativeSegmentedBlockRenderer implements BlockEntityRenderer<NativeSegmentedDoorEntity> {
    private record Presentation(NativeSegmentedDoorEntity.Publication source,NativeSegmentedVisual visual,NativeSegmentedVisual.Motion motion){}
    private static final com.google.common.cache.Cache<NativeSegmentedDoorEntity,Presentation> PRESENTATIONS=
            com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumWeight(1024*1024)
                    .weigher((NativeSegmentedDoorEntity key,Presentation value)->512+value.visual.height()*128).build();
    private final int[] lights=new int[32];
    private final BlockPos.MutableBlockPos lightPos=new BlockPos.MutableBlockPos();
    public NativeSegmentedBlockRenderer(BlockEntityRendererProvider.Context context){}
    private static Presentation presentation(NativeSegmentedDoorEntity tile){
        var source=tile.publication();if(source==null)return null;var found=PRESENTATIONS.getIfPresent(tile);if(found!=null&&found.source==source)return found;
        var original=source.visual();NativeSegmentedVisual visual;
        if(tile.inert())visual=new NativeSegmentedVisual(1,1,0,original.facing(),-1,NativeSegmentedDoorSettings.LEGACY,
                List.of(NativeSegmentedDoorSettings.LEGACY),List.of(SegmentedWindowLayout.SINGLE),true,false,false,true);
        else{
            int texture=original.texture();String category=texture<0?"":ModernFinishCatalog.option(texture).category();int slash=category.indexOf('/');if(slash>=0)category=category.substring(0,slash);
            boolean eligible=original.settings().direction==0&&original.settings().position==0&&!original.settings().straight
                    &&texture<ModernFinishCatalog.ENTRIES.size()&&!category.equals("Frame")&&!category.equals("Frames");
            for(var row:original.rows())eligible&=row.nativePanel();
            visual=eligible==original.terrainEligible()?original:new NativeSegmentedVisual(original.width(),original.height(),original.column(),original.facing(),texture,
                    original.settings(),original.rows(),original.windows(),original.ready(),original.suppressed(),original.awaitingChunk(),eligible);
        }
        var motion=source.motion();
        // A first client observation can occur later than snapshot publication or its server start tick.
        motion=new NativeSegmentedVisual.Motion(motion.identity(),motion.motionIdentity(),motion.sequence(),motion.targetOpen(),tile.pose(0),motion.ticksPerSection(),motion.started(),motion.duration());
        found=new Presentation(source,visual,motion);PRESENTATIONS.put(tile,found);return found;
    }
    public static double selectionPose(NativeSegmentedDoorEntity tile){var p=presentation(tile);return p==null||tile.inert()?0:NativeSegmentedRenderer.selectionPose(p.visual,p.motion,tile.getLevel(),tile.getBlockPos());}
    @Override public void render(NativeSegmentedDoorEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.topOffset()!=0||tile.getLevel()==null)return;var p=presentation(tile);if(p==null||!p.visual.ready())return;
        var pos=tile.getBlockPos();for(int row=0;row<p.visual.height();row++)lights[row]=LevelRenderer.getLightColor(tile.getLevel(),lightPos.set(pos.getX(),pos.getY()-row,pos.getZ()));
        if(tile.inert())NativeSegmentedRenderer.renderClosed(p.visual,poses,buffers,lights,overlay);
        else NativeSegmentedRenderer.render(p.visual,p.motion,tile.getLevel(),pos,poses,buffers,lights,overlay);
    }
    @Override public boolean shouldRenderOffScreen(NativeSegmentedDoorEntity tile){return tile.topOffset()==0;}
    @Override public int getViewDistance(){return 256;}
    @Override public AABB getRenderBoundingBox(NativeSegmentedDoorEntity tile){var p=presentation(tile);return p==null?new AABB(tile.getBlockPos()):NativeSegmentedRenderer.bounds(tile.getBlockPos(),p.visual);}
    @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Lifecycle {
        @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())PRESENTATIONS.invalidateAll();}
    }
}
