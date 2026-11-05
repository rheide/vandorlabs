package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Creative shift-click and the main-hand Configurizer share one dedicated configuration path. */
@EventBusSubscriber(modid="vandorlabs")
public final class CubeConfiguration {
    public static boolean open(Player player,Level level,BlockPos pos){
        if(!NativeConfigurationAccess.canEdit(player,level,pos)||!(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity))return false;
        if(level.getBlockEntity(pos) instanceof NativeTriggerBlockEntity){
            if(!level.isClientSide&&player instanceof ServerPlayer serverPlayer)
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->new TriggerMenu(id,inventory,pos),net.minecraft.network.chat.Component.literal("Programmable Trigger")),buffer->buffer.writeBlockPos(pos));
            return true;
        }
        if(!level.isClientSide&&player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->new ProgrammableMenu(id,inventory,pos),net.minecraft.network.chat.Component.literal("Programmable Block")),buffer->buffer.writeBlockPos(pos));
        return true;
    }
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||(!event.getEntity().isShiftKeyDown()&&!event.getEntity().getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))||!event.getLevel().hasChunkAt(event.getPos())||!(event.getLevel().getBlockState(event.getPos()).is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())||event.getLevel().getBlockState(event.getPos()).is(VandorLabsBootstrap.TRIGGER_BLOCK.get())))return;
        if(open(event.getEntity(),event.getLevel(),event.getPos())){event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));}
    }
    private CubeConfiguration(){}
}
