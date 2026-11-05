package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.*;

/** Disposable actual mounted-control scene for real menu and settings packets. */
public final class BinaryGuiServerChecks {
    public static final BlockPos POS=new BlockPos(1000,80,1000),PUSH=POS.east(2),LAMP=POS.west();
    private static ServerPlayer actor;private static int opens;private static boolean sceneReady;
    static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("binary-ui");}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native binary dialog: "+message);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player;var world=player.serverLevel();
        for(int x=997;x<=1004;x++)for(int z=996;z<=1003;z++)world.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        world.setBlock(POS.south(),Blocks.STONE.defaultBlockState(),3);world.setBlock(PUSH.south(),Blocks.STONE.defaultBlockState(),3);world.setBlock(LAMP,Blocks.REDSTONE_LAMP.defaultBlockState(),3);
        world.setBlock(POS,VandorLabsBootstrap.ROCKER_SWITCH.get().defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,Direction.NORTH),3);world.setBlock(PUSH,VandorLabsBootstrap.PUSH_BUTTON.get().defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,Direction.NORTH),3);
        var tile=(NativeBinaryControlBlockEntity)world.getBlockEntity(POS);tile.configureTransmission(ChannelList.of(81),16,true);tile.configureMount(ControlMountSettings.of(0,0,0));tile.onLoad();((NativeBinaryControlBlockEntity)world.getBlockEntity(PUSH)).onLoad();NativeRedstoneChannels.flush(world);
        BinaryControlScene.setup(world);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(1000.5,80,997.5);player.setYRot(0);player.setXRot(8);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.inventoryMenu.broadcastChanges();
        VandorLabsBootstrap.ROCKER_SWITCH.get().activate(world.getBlockState(POS),world,POS,player);
        System.out.println("[vandorlabs][binary-gui] client fixture READY survival selected0 Configurizer initialON local lamp");
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){if(enabled()&&event.getEntity()==actor&&event.getContainer() instanceof BinaryControlMenu){opens++;require(actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"actual menu opened in wrong mode");}}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!enabled()||actor==null||sceneReady||actor.containerMenu instanceof BinaryControlMenu)return;var push=(NativeBinaryControlBlockEntity)actor.serverLevel().getBlockEntity(PUSH);if(push!=null&&push.mountSettings().height==3){sceneReady=true;BinaryControlScene.finish(actor);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!enabled()||event.getEntity()!=actor)return;var world=actor.serverLevel();NativeBinaryControlBlockEntity tile=(NativeBinaryControlBlockEntity)world.getBlockEntity(POS),push=(NativeBinaryControlBlockEntity)world.getBlockEntity(PUSH);
        require(opens>=7,"expected actual repeated Configurizer/shift dialogs opens="+opens);require(tile.mountSettings().height==1&&tile.mountSettings().tilt==1&&tile.mountSettings().direction==1,"Done/Escape mount commit separation");
        require(tile.transmissionChannels().equals(ChannelList.of(90,91))&&tile.transmissionRange()==128&&tile.localRedstone(),"inline final settings or invalid pending mutation leaked");require(push.mountSettings().height==3&&push.mountSettings().tilt==0&&push.mountSettings().direction==0,"actual shift-open push-button Done mount");
        System.out.println("[vandorlabs][binary-gui] server-client-actions PASS opens="+opens+" authenticated real menus mount Done/Escape inline channels/range/local invalid rejection");
    }
}
