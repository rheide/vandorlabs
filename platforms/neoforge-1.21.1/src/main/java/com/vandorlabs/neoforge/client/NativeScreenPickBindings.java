package com.vandorlabs.neoforge.client;
import com.vandorlabs.neoforge.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** Replaces only creative Screen picking; the server supplies the complete configured item. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeScreenPickBindings {
 private NativeScreenPickBindings(){}
 @SubscribeEvent public static void pick(InputEvent.InteractionKeyMappingTriggered event){if(!event.isPickBlock())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null||!mc.gameMode.getPlayerMode().isCreative()||mc.getCameraEntity()!=mc.player||mc.player.containerMenu!=mc.player.inventoryMenu||!(mc.hitResult instanceof BlockHitResult hit)||!mc.level.hasChunkAt(hit.getBlockPos())||!(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof NativeScreenBlock))return;event.setCanceled(true);PacketDistributor.sendToServer(new ScreenPickPayload(hit.getBlockPos(),mc.player.getInventory().selected,mc.player.inventoryMenu.getStateId()));}
}
