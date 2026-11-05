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

/** Real menus distinguish canceled type/limits drafts from the existing Done detent normalization. */
public final class SignalGuiServerChecks {
    public static final BlockPos POS=new BlockPos(1000,80,1000),SLIDER=POS.east(2);
    private static ServerPlayer actor;private static NativeSignalControlBlockEntity throttle,slider;private static int opens;private static boolean ready,sceneReady;
    static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("signal-ui");}
    static void require(boolean value,String detail){if(!value)throw new IllegalStateException("Native signal dialog: "+detail);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player;var world=player.serverLevel();for(int x=997;x<=1004;x++)for(int z=996;z<=1003;z++)world.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        for(var pos:new BlockPos[]{POS,SLIDER})world.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);world.setBlock(POS,VandorLabsBootstrap.THRUSTER_LEVER.get().defaultBlockState(),3);world.setBlock(SLIDER,VandorLabsBootstrap.WALL_SLIDER.get().defaultBlockState(),3);
        throttle=(NativeSignalControlBlockEntity)world.getBlockEntity(POS);slider=(NativeSignalControlBlockEntity)world.getBlockEntity(SLIDER);throttle.configureTransmission(ChannelList.of(201,202),16,true);slider.configureTransmission(ChannelList.of(211,212),16,true);throttle.onLoad();slider.onLoad();NativeRedstoneChannels.flush(world);
        SignalControlScene.setup(world);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(1000.5,80,997.5);player.setYRot(0);player.setXRot(8);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){if(enabled()&&event.getEntity()==actor&&event.getContainer() instanceof BinaryControlMenu){opens++;require(actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"wrong mode");if(!ready){throttle.applyLinkedLevels(java.util.Map.of(201,4,202,11));slider.applyLinkedLevels(java.util.Map.of(211,4,212,11));ready=true;}}}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!enabled()||actor==null||!ready)return;var world=actor.serverLevel();require(world.getBlockEntity(POS)==throttle&&world.getBlockEntity(SLIDER)==slider,"type/limit draft replaced tile identity");int type=throttle.getControlType(),level=type==0?4:type==1?5:6;require(throttle.latchedLevel(201)==level&&throttle.latchedLevel(202)==(type==0?11:level),"General Done must reapply current visible detent even with unchanged limits");int sliderLevel=slider.getLowLimit()==5?4:3;require(slider.latchedLevel(211)==sliderLevel&&slider.latchedLevel(212)==(sliderLevel==4?11:3),"slider Done bank normalization");if(!sceneReady&&opens>=6&&!(actor.containerMenu instanceof BinaryControlMenu)&&throttle.getControlType()==2&&slider.getLowLimit()==3){sceneReady=true;SignalControlScene.finish(actor);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(!enabled()||event.getEntity()!=actor)return;require(opens>=6,"missing real menus opens="+opens);require(throttle.getControlType()==2&&throttle.getLowLimit()==6&&throttle.getHighLimit()==14&&throttle.mountSettings().height==1,"final selected throttle settings");require(slider.getControlType()==0&&slider.getLowLimit()==3&&slider.getHighLimit()==11&&slider.mountSettings().height==2,"fixed wall slider settings");System.out.println("[vandorlabs][signal-gui] server-client-actions PASS actual menus all three throttle types and wall slider identity Done normalization and canceled drafts opens="+opens);}
}
