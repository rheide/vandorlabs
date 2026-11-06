package com.vandorlabs.neoforge.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** One twelve-edge bounding box in the established selection style; never enumerates collision slices. */
public final class NativeBoxOutline {
    private NativeBoxOutline(){}
    public static void draw(RenderHighlightEvent.Block event,AABB box){
        var camera=event.getCamera().getPosition();event.setCanceled(true);
        LevelRenderer.renderLineBox(event.getPoseStack(),event.getMultiBufferSource().getBuffer(RenderType.lines()),
                box.inflate(.002).move(-camera.x,-camera.y,-camera.z),0,0,0,.4F);
    }
}
