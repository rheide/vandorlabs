package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.NativeCanopyRenderer;
import com.vandorlabs.neoforge.canopy.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.AABB;

/** Authored moving/fixed standalone variants; regular glass is exclusively chunk-rendered. */
public final class NativeCanopyBlockRenderer implements BlockEntityRenderer<NativeCanopyEntity> {
    private record Presentation(NativeCanopyRenderSnapshot visual,AABB bounds){}
    private static final com.google.common.cache.Cache<NativeCanopyEntity,Presentation> PRESENTATIONS=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(1024).build();
    public NativeCanopyBlockRenderer(BlockEntityRendererProvider.Context context){}
    private static Presentation presentation(NativeCanopyEntity tile,NativeCanopyRenderSnapshot visual){var found=PRESENTATIONS.getIfPresent(tile);if(found!=null&&found.visual()==visual)return found;var pos=tile.getBlockPos();int radius=com.vandorlabs.persistence.CanopySettingsData.supports(visual.kind())?NativeCanopyModels.builtin().dimensions(visual.kind()).width()+visual.settings().data().length()+2:Math.max(5,visual.width()+2);found=new Presentation(visual,new AABB(pos.getX()-radius,pos.getY()-radius,pos.getZ()-radius,pos.getX()+1+radius,pos.getY()+1+radius,pos.getZ()+1+radius));PRESENTATIONS.put(tile,found);return found;}
    @Override public void render(NativeCanopyEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){var publication=tile.publication();if(tile.getLevel()==null||!publication.draws()||publication.visual().kind().equals("regular_canopy_glass"))return;var bake=NativeCanopyRenderer.currentBake();if(bake==null)return;double progress=publication.inert()?0:NativeCanopyVisualClock.pose(tile.getLevel(),tile.getBlockPos(),publication);bake.draw(publication.visual(),progress).emit(poses.last(),buffers,light,overlay,true,true);}
    @Override public int getViewDistance(){return 256;}
    @Override public AABB getRenderBoundingBox(NativeCanopyEntity tile){return presentation(tile,tile.publication().visual()).bounds();}
    @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Lifecycle {@net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())PRESENTATIONS.invalidateAll();}}
}
