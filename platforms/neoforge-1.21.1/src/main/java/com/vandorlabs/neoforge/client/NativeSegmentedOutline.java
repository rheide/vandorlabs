package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.segmented.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Pick the fixed header or moved panel box, matching the source's single-box highlight. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSegmentedOutline {
    private NativeSegmentedOutline(){}
    public static AABB selected(NativeSegmentedDoorEntity tile,Vec3 point){
        var geometry=NativeSegmentedTargets.geometry(tile,NativeSegmentedTargets.pose(tile.root()));if(geometry==null)return null;
        var header=geometry.header();return header!=null&&header.inflate(.001).contains(point)?header:geometry.bounds();
    }
    @SubscribeEvent public static void render(RenderHighlightEvent.Block event){
        var level=Minecraft.getInstance().level;if(level==null)return;var hit=event.getTarget();var tile=NativeSegmentedTargets.loaded(level,hit.getBlockPos());if(tile==null)return;
        var box=selected(tile,hit.getLocation());if(box!=null)NativeBoxOutline.draw(event,box);
    }
}
