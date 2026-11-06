package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.NativeTrapdoorRenderer;
import com.vandorlabs.neoforge.trapdoor.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

/** Native world adapter; the common backend owns meshes, materials and pause-aware cosmetic motion. */
public final class NativeTrapdoorBlockRenderer implements BlockEntityRenderer<NativeTrapdoorEntity> {
    private record Presentation(NativeTrapdoorVisual source,BlockState state,NativeTrapdoorVisual visual,AABB bounds) { }
    private static final com.google.common.cache.Cache<NativeTrapdoorEntity,Presentation> PRESENTATIONS=
            com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(1024).build();
    public NativeTrapdoorBlockRenderer(BlockEntityRendererProvider.Context context) { }
    private static Presentation presentation(NativeTrapdoorEntity tile){
        var source=tile.trapdoorVisual();var state=tile.getBlockState();var found=PRESENTATIONS.getIfPresent(tile);
        if(found!=null&&found.source()==source&&found.state()==state)return found;
        var visual=source;
        if(visual==null){
            var pose=NativeTrapdoorGeometry.missing(tile.diagonal(),NativeTrapdoorGeometry.turns(state.getValue(NativeTrapdoorBlock.FACING)),state.getValue(NativeTrapdoorBlock.HALF)==Half.TOP);
            visual=new NativeTrapdoorVisual(tile.settings(),pose,new NativeTrapdoorVisual.ArtworkBounds(0,0,1,1),0,state.getValue(NativeTrapdoorBlock.OPEN));
        }
        var pos=tile.getBlockPos();
        // Match the released culling envelope without allocating a new box for every frame.
        found=new Presentation(source,state,visual,new AABB(pos.getX()-8,pos.getY()-8,pos.getZ()-8,pos.getX()+9,pos.getY()+9,pos.getZ()+9));
        PRESENTATIONS.put(tile,found);return found;
    }
    @Override public void render(NativeTrapdoorEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.getLevel()!=null)NativeTrapdoorRenderer.render(presentation(tile).visual(),tile.getLevel(),tile.getBlockPos(),poses,buffers,light,overlay);
    }
    @Override public int getViewDistance(){return 1024;}
    @Override public AABB getRenderBoundingBox(NativeTrapdoorEntity tile){return presentation(tile).bounds();}
    @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Lifecycle {
        @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())PRESENTATIONS.invalidateAll();}
    }
}
