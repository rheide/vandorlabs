package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.door.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Moved-leaf rays replace voxel picking after vanilla publishes its result and before an input action. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeDoorSelection {
    private NativeDoorSelection(){}
    /** Called after vanilla publishes its ray, before tick-time continuous attack captures that result. */
    public static void refreshPick(float partialTick){update(partialTick);}
    public static NativeDoorTargets.Target update(float partialTick){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.gameMode==null||mc.player.isSpectator()||mc.getCameraEntity()!=mc.player)return null;
        double reach=mc.player.blockInteractionRange();if(!Double.isFinite(reach)||reach<=0||reach>64)return null;
        var start=mc.player.getEyePosition(partialTick);var end=start.add(mc.player.getViewVector(partialTick).scale(reach));var target=NativeDoorTargets.pick(mc.level,mc.player,start,end);if(target==null)return null;
        if(mc.hitResult instanceof EntityHitResult entity&&start.distanceToSqr(entity.getLocation())<start.distanceToSqr(target.hit().getLocation()))return null;
        mc.hitResult=target.hit();mc.crosshairPickEntity=null;return target;
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){
        var target=update(1);if(target==null||!event.isUseItem())return;
        var mc=Minecraft.getInstance();var part=com.vandorlabs.neoforge.segmented.NativeSegmentedTargets.loaded(mc.level,target.hit().getBlockPos());
        if(part!=null){if(event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND||mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem)return;var root=part.root();var publication=root.publication();if(publication==null)return;
            event.setCanceled(true);PacketDistributor.sendToServer(new com.vandorlabs.neoforge.segmented.SegmentedUsePayload(part.getBlockPos(),target.hit().getLocation(),publication.motion().identity(),root.sequence(),com.vandorlabs.neoforge.segmented.NativeSegmentedTargets.pose(root)));return;}
        if(com.vandorlabs.neoforge.trapdoor.NativeTrapdoorEntity.at(mc.level,target.hit().getBlockPos())!=null){event.setCanceled(true);PacketDistributor.sendToServer(new com.vandorlabs.neoforge.trapdoor.TrapdoorUsePayload(target.root(),event.getHand()));return;}
        if(com.vandorlabs.neoforge.canopy.NativeCanopyLifecycle.at(mc.level,target.hit().getBlockPos())!=null){event.setCanceled(true);PacketDistributor.sendToServer(new com.vandorlabs.neoforge.canopy.CanopyUsePayload(target.root(),event.getHand()));return;}
        event.setCanceled(true);PacketDistributor.sendToServer(new DoorUsePayload(target.root(),event.getHand()));
    }
}
