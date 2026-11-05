package com.vandorlabs.neoforge.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Stair rays use detailed tread boxes; the released selection outline remains one full cell. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeStairHighlight {
    private static final ResourceLocation STAIRS=ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_stairs");
    @SubscribeEvent public static void highlight(RenderHighlightEvent.Block event){
        var entity=event.getCamera().getEntity();if(entity==null)return;
        var level=entity.level();var pos=event.getTarget().getBlockPos();
        if(!level.getWorldBorder().isWithinBounds(pos)||!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(STAIRS))return;
        event.setCanceled(true);
        var camera=event.getCamera().getPosition();
        LevelRenderer.renderLineBox(event.getPoseStack(),event.getMultiBufferSource().getBuffer(RenderType.lines()),pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z,pos.getX()+1-camera.x,pos.getY()+1-camera.y,pos.getZ()+1-camera.z,0,0,0,.4F);
    }
    private NativeStairHighlight(){}
}
