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
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.appliances.NativeApplianceEntity)return com.vandorlabs.neoforge.appliances.NativeApplianceBindings.open(player,level,pos);
        if(com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.ownerAt(level,pos)!=null)return com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.vehicle.NativeVehicleControllerEntity)return com.vandorlabs.neoforge.vehicle.NativeVehicleBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.copier.NativeCopyControllerEntity)return com.vandorlabs.neoforge.copier.NativeCopyBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.ramp.NativeRampEntity)return com.vandorlabs.neoforge.ramp.NativeRampBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.gear.NativeGearEntity)return com.vandorlabs.neoforge.gear.NativeGearBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.surface.NativeSurfaceEntity)return com.vandorlabs.neoforge.surface.NativeSurfaceBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof com.vandorlabs.neoforge.legacylanding.NativeLegacyLandingBlock)return com.vandorlabs.neoforge.legacylanding.NativeLegacyLandingBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.seating.NativeChairEntity)return com.vandorlabs.neoforge.seating.NativeSeatBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.plate.NativePlateEntity)return com.vandorlabs.neoforge.plate.NativePlateBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.reservoir.NativeBufferEntity)return com.vandorlabs.neoforge.reservoir.NativeBufferBindings.open(player,level,pos);
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.coupler.NativeCouplerEntity)
            return com.vandorlabs.neoforge.coupler.NativeCouplerBindings.open(player,level,pos);
        if(!NativeConfigurationAccess.canEdit(player,level,pos)||!(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity))return false;
        if(level.getBlockEntity(pos) instanceof NativeTriggerBlockEntity){
            if(!level.isClientSide&&player instanceof ServerPlayer serverPlayer)
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->new TriggerMenu(id,inventory,pos),net.minecraft.network.chat.Component.literal("Programmable Trigger")),buffer->buffer.writeBlockPos(pos));
            return true;
        }
        if(!level.isClientSide&&player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->new ProgrammableMenu(id,inventory,pos),level.getBlockState(pos).getBlock().getName()),buffer->buffer.writeBlockPos(pos));
        return true;
    }
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||(!event.getEntity().isShiftKeyDown()&&!event.getEntity().getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))||!event.getLevel().hasChunkAt(event.getPos())||!(event.getLevel().getBlockState(event.getPos()).is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())||event.getLevel().getBlockState(event.getPos()).is(VandorLabsBootstrap.TRIGGER_BLOCK.get())||event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.vandorlabs.neoforge.shape.NativeProgrammableSlab||event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.vandorlabs.neoforge.shape.NativeProgrammableStairs||event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.vandorlabs.neoforge.shape.NativeFlatWallBlock||event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.vandorlabs.neoforge.shape.NativeCornerWallBlock))return;
        if(open(event.getEntity(),event.getLevel(),event.getPos())){event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));}
    }
    private CubeConfiguration(){}
}
