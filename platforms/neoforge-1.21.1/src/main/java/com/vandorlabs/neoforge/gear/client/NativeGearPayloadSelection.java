package com.vandorlabs.neoforge.gear.client;

import com.vandorlabs.neoforge.gear.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Captured geometry proposes a root only; every dig is retraced and timed by the server. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearPayloadSelection {
    private static NativeGearHarvestPayload sent;private static int renewal;
    public static NativeGearPayloadTrace.Hit refreshPick(float partial){var mc=Minecraft.getInstance();if(!NativeGearBindings.enabled()||mc.level==null||mc.player==null||mc.gameMode==null||mc.player.isSpectator()||mc.getCameraEntity()!=mc.player)return null;double reach=mc.player.blockInteractionRange();if(!Double.isFinite(reach)||reach<=0||reach>64)return null;var start=mc.player.getEyePosition(partial);var end=start.add(mc.player.getViewVector(partial).scale(reach));var hit=NativeGearPayloadTrace.nearest(mc.level,start,end,NativeGearVisualClock::pose);if(hit==null)return null;var mechanism=NativeGearMechanismTrace.nearest(mc.level,start,end,NativeGearVisualClock::pose);if(mechanism!=null&&mechanism.distanceSq()+1e-8<hit.distanceSquared())return null;if(mc.hitResult!=null&&mc.hitResult.getType()!=HitResult.Type.MISS&&start.distanceToSqr(mc.hitResult.getLocation())+1e-8<hit.distanceSquared())return null;mc.hitResult=new BlockHitResult(hit.point(),hit.face(),hit.owner().getBlockPos(),false);mc.crosshairPickEntity=null;return hit;}
    public static boolean attacking(boolean held){return held&&refreshPick(1)!=null;}
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){if(event.isAttack()&&refreshPick(1)!=null){event.setCanceled(true);event.setSwingHand(true);}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();var hit=mc.player!=null&&mc.screen==null&&mc.options.keyAttack.isDown()?refreshPick(1):null;if(hit==null){if(sent!=null&&mc.getConnection()!=null)PacketDistributor.sendToServer(new NativeGearHarvestPayload(sent.root(),sent.identity(),true));sent=null;renewal=0;return;}var packet=new NativeGearHarvestPayload(hit.owner().getBlockPos(),hit.owner().identity(),false);if(!packet.equals(sent)||renewal++>=4){PacketDistributor.sendToServer(packet);sent=packet;renewal=0;}}
    private NativeGearPayloadSelection(){}
}
