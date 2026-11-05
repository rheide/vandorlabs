package com.vandorlabs.neoforge.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Keep the released simple outline while detailed geometry remains available for ray and collision tests. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeShapeHighlight {
    private static final net.minecraft.world.phys.AABB FULL=new net.minecraft.world.phys.AABB(0,0,0,1,1,1);
    @SubscribeEvent public static void highlight(RenderHighlightEvent.Block event){
        var entity=event.getCamera().getEntity();if(entity==null)return;
        var level=entity.level();var pos=event.getTarget().getBlockPos();
        if(!level.getWorldBorder().isWithinBounds(pos))return;
        var state=level.getBlockState(pos);net.minecraft.world.phys.AABB box;
        if(state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeProgrammableStairs)box=FULL;
        else if(state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeFlatWallBlock)box=com.vandorlabs.neoforge.shape.NativeFlatWallBlock.geometry(state,level,pos).bounds();
        else return;
        event.setCanceled(true);
        var camera=event.getCamera().getPosition();
        LevelRenderer.renderLineBox(event.getPoseStack(),event.getMultiBufferSource().getBuffer(RenderType.lines()),pos.getX()+box.minX-camera.x,pos.getY()+box.minY-camera.y,pos.getZ()+box.minZ-camera.z,pos.getX()+box.maxX-camera.x,pos.getY()+box.maxY-camera.y,pos.getZ()+box.maxZ-camera.z,0,0,0,.4F);
    }
    private NativeShapeHighlight(){}
}
