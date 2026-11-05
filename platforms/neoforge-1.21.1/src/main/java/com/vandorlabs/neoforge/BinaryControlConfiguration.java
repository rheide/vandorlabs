package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Ordinary survival shift and main-hand Configurizer share the exact same mounted-control menu. */
@EventBusSubscriber(modid="vandorlabs")
public final class BinaryControlConfiguration {
    public static boolean open(Player player,Level level,BlockPos pos){if(!NativeConfigurationAccess.canEditDialog(player,level,pos)||!(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity))return false;if(!level.isClientSide&&player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new BinaryControlMenu(id,inventory,pos),Component.literal("Redstone Channel")),b->b.writeBlockPos(pos));return true;}
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event){if(event.getHand()!=InteractionHand.MAIN_HAND||(!event.getEntity().isShiftKeyDown()&&!event.getEntity().getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))||!event.getLevel().hasChunkAt(event.getPos())||!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof NativeMountedSwitchBlock))return;if(open(event.getEntity(),event.getLevel(),event.getPos())){event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));}}
    private BinaryControlConfiguration(){}
}
