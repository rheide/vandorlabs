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

/** Ordinary main-hand interaction and Configurizer use the same dedicated endpoint dialog. */
@EventBusSubscriber(modid="vandorlabs")
public final class WirelessConfiguration {
    public static boolean open(Player player,Level level,BlockPos pos){if(level instanceof net.minecraft.server.level.ServerLevel serverLevel&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(serverLevel))return false;if(!NativeConfigurationAccess.canEditDialog(player,level,pos)||!(level.getBlockEntity(pos) instanceof NativeWirelessRedstoneBlockEntity tile))return false;if(!level.isClientSide&&player instanceof ServerPlayer server){server.connection.send(tile.getUpdatePacket());server.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new WirelessMenu(id,inventory,pos),Component.literal(tile.transmitter()?"Redstone Transmitter Block":"Redstone Receiver Block")),buffer->buffer.writeBlockPos(pos));}return true;}
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event){if(event.getHand()!=InteractionHand.MAIN_HAND||!event.getEntity().getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel server&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))return;if(!event.getLevel().hasChunkAt(event.getPos())||!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof NativeWirelessRedstoneBlock))return;if(open(event.getEntity(),event.getLevel(),event.getPos())){event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));}}
    private WirelessConfiguration(){}
}
