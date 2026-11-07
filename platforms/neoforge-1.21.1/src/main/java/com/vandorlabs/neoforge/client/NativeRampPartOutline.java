package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Released single outer highlight; precise ray and collision shapes remain untouched. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRampPartOutline {
    private NativeRampPartOutline(){}
    public static AABB selected(NativeRampPartEntity tile){var value=tile.publication();return !tile.ownershipKnown()||value==null||value.geometry().pieces().isEmpty()?null:value.geometry().bounds();}
    @SubscribeEvent public static void render(RenderHighlightEvent.Block event){
        var level=Minecraft.getInstance().level;if(level==null)return;var pos=event.getTarget().getBlockPos();
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!(chunk.getBlockState(pos).getBlock() instanceof NativeRampPartBlock)||!(chunk.getBlockEntities().get(pos) instanceof NativeRampPartEntity tile))return;
        var box=selected(tile);if(box==null)return;var camera=event.getCamera().getPosition();event.setCanceled(true);
        LevelRenderer.renderLineBox(event.getPoseStack(),event.getMultiBufferSource().getBuffer(RenderType.lines()),
                pos.getX()+box.minX-.002-camera.x,pos.getY()+box.minY-.002-camera.y,pos.getZ()+box.minZ-.002-camera.z,
                pos.getX()+box.maxX+.002-camera.x,pos.getY()+box.maxY+.002-camera.y,pos.getZ()+box.maxZ+.002-camera.z,0,0,0,.4F);
    }
}
