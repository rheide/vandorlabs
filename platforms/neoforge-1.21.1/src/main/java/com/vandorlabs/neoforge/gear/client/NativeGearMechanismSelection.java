package com.vandorlabs.neoforge.gear.client;

import com.vandorlabs.neoforge.gear.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Nearest-hit proposal composes with payload picking in either hook order. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearMechanismSelection {
    public static NativeGearMechanismTrace.Hit refreshPick(float partial){var mc=Minecraft.getInstance();if(!NativeGearBindings.enabled()||mc.level==null||mc.player==null||mc.gameMode==null||mc.player.isSpectator()||mc.getCameraEntity()!=mc.player)return null;double reach=mc.player.blockInteractionRange();if(!Double.isFinite(reach)||reach<=0||reach>64)return null;var start=mc.player.getEyePosition(partial);var end=start.add(mc.player.getViewVector(partial).scale(reach));var hit=NativeGearMechanismTrace.nearest(mc.level,start,end,NativeGearVisualClock::pose);if(hit==null)return null;var payload=NativeGearPayloadTrace.nearest(mc.level,start,end,NativeGearVisualClock::pose);if(payload!=null&&payload.distanceSquared()<=hit.distanceSq()+1e-8)return null;if(mc.hitResult!=null&&mc.hitResult.getType()!=HitResult.Type.MISS&&start.distanceToSqr(mc.hitResult.getLocation())+1e-8<hit.distanceSq())return null;var address=hit.source();if(mc.player.getMainHandItem().getItem() instanceof NativeGearItem item&&((NativeGearBlock)item.getBlock()).definition().foot()&&hit.owner().progress()==1&&hit.owner().assembly()!=null&&hit.owner().assembly().kind()!=com.vandorlabs.gear.core.GearTopology.FootKind.NONE){var inside=hit.worldPoint().subtract(Vec3.atLowerCornerOf(hit.worldFace().getNormal()).scale(.0001));var cell=net.minecraft.core.BlockPos.containing(inside);var member=NativeGearFormation.at(mc.level,cell);if(member!=null&&member.definition().foot()&&member.controllerEntity()==hit.owner())address=cell;else {double nearest=Double.POSITIVE_INFINITY;for(var foot:hit.owner().assembly().feet()){var source=hit.owner().getBlockPos().offset(NativeGearOrientation.vector(hit.owner().mount(),new net.minecraft.core.BlockPos(foot.x,foot.y,foot.z)));var candidate=NativeGearFormation.at(mc.level,source);if(candidate==null||!candidate.definition().foot())continue;double distance=Vec3.atCenterOf(source).distanceToSqr(hit.worldPoint());if(distance<nearest){nearest=distance;address=source;}}}}mc.hitResult=new BlockHitResult(hit.worldPoint(),hit.worldFace(),address,false);mc.crosshairPickEntity=null;return hit;}
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND)return;var mc=Minecraft.getInstance();if(mc.player==null)return;boolean configure=mc.player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get());boolean foot=mc.player.getMainHandItem().getItem() instanceof NativeGearItem item&&((NativeGearBlock)item.getBlock()).definition().foot();if(!configure&&!foot&&!mc.player.getMainHandItem().isEmpty())return;var hit=refreshPick(1);if(hit==null)return;if(foot&&hit.owner().assembly()!=null&&hit.owner().assembly().kind()!=com.vandorlabs.gear.core.GearTopology.FootKind.NONE&&hit.owner().progress()==1)return;event.setCanceled(true);event.setSwingHand(true);PacketDistributor.sendToServer(new NativeGearMechanismUsePayload(hit.owner().getBlockPos(),hit.owner().identity(),foot?2:configure?1:0));}
    private NativeGearMechanismSelection(){}
}
