package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.*;

/** Real settings packets must retain each placed lever's identity and unequal channel bank. */
public final class PowerLeverGuiServerChecks {
    public static final BlockPos POS=new BlockPos(1000,80,1000),TWIN=POS.east(2);
    private static ServerPlayer actor;private static NativeBinaryControlBlockEntity single,twin;private static int opens;private static boolean bankReady,sceneReady;
    static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("power-ui");}
    static void require(boolean value,String detail){if(!value)throw new IllegalStateException("Native power lever dialog: "+detail);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player;var world=player.serverLevel();
        for(int x=997;x<=1004;x++)for(int z=996;z<=1003;z++)world.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        for(var pos:new BlockPos[]{POS,TWIN})world.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);
        world.setBlock(POS,VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get().defaultBlockState(),3);world.setBlock(TWIN,VandorLabsBootstrap.SMALL_POWER_LEVER.get().defaultBlockState(),3);
        single=(NativeBinaryControlBlockEntity)world.getBlockEntity(POS);twin=(NativeBinaryControlBlockEntity)world.getBlockEntity(TWIN);
        for(var tile:new NativeBinaryControlBlockEntity[]{single,twin}){tile.configureTransmission(ChannelList.of(201,202),16,true);tile.onLoad();}NativeRedstoneChannels.flush(world);
        PowerLeverScene.setup(world);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(1000.5,80,997.5);player.setYRot(0);player.setXRot(8);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){if(enabled()&&event.getEntity()==actor&&event.getContainer() instanceof BinaryControlMenu){opens++;require(actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"wrong actual player mode");if(!bankReady){single.applyLinkedLevels(java.util.Map.of(201,4,202,11));twin.applyLinkedLevels(java.util.Map.of(201,4,202,11));bankReady=true;}}}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!enabled()||actor==null||!bankReady)return;var world=actor.serverLevel();require(world.getBlockEntity(POS)==single&&world.getBlockEntity(TWIN)==twin,"size settings replaced block entity");for(var tile:new NativeBinaryControlBlockEntity[]{single,twin})require(tile.latchedLevel(201)==4&&tile.latchedLevel(202)==11&&tile.getOutputLevel()==4,"size/mount settings lost unequal channel bank");if(!sceneReady&&opens>=7&&!(actor.containerMenu instanceof BinaryControlMenu)&&single.getPowerLeverSize()==0&&twin.getPowerLeverSize()==1){sceneReady=true;PowerLeverScene.finish(actor);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(!enabled()||event.getEntity()!=actor)return;require(opens>=6,"missing real dedicated menus opens="+opens);require(single.getPowerLeverSize()==0&&twin.getPowerLeverSize()==1&&single.mountSettings().height==1&&twin.mountSettings().height==1,"final Done/Enter size+mount");System.out.println("[vandorlabs][power-gui] server-client-actions PASS both families identity unequal-bank atomic size/mount Done Enter Escape opens="+opens);}
}
