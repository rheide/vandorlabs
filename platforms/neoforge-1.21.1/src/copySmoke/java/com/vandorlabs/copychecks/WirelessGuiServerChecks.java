package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.*;

/** Installed pair, real endpoint menus and physical receiver output; the UI supplies every setting packet. */
public final class WirelessGuiServerChecks {
    public static final BlockPos TX=new BlockPos(1000,80,1000),RX=TX.east(2),LAMP=RX.east();
    public static final double X=1000.5,Y=80,Z=997.5,VIEW_X=1001.5,VIEW_Z=996.5;
    private static ServerPlayer actor;private static NativeWirelessRedstoneBlockEntity tx,rx;private static int opens;private static boolean finished;
    static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("wireless-ui");}
    static void require(boolean value,String detail){if(!value)throw new IllegalStateException("Native wireless GUI: "+detail);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player;var world=player.serverLevel();for(int x=997;x<=1005;x++)for(int z=996;z<=1003;z++)world.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        var room=new BlockPos(1000,80,997);require(world.isEmptyBlock(room)&&world.isEmptyBlock(room.above()),"server actor room must be air");world.setBlock(TX,VandorLabsBootstrap.REDSTONE_TRANSMITTER_BLOCK.get().defaultBlockState(),3);world.setBlock(RX,VandorLabsBootstrap.REDSTONE_RECEIVER_BLOCK.get().defaultBlockState(),3);world.setBlock(LAMP,Blocks.REDSTONE_LAMP.defaultBlockState(),3);world.setBlock(TX.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        tx=(NativeWirelessRedstoneBlockEntity)world.getBlockEntity(TX);rx=(NativeWirelessRedstoneBlockEntity)world.getBlockEntity(RX);tx.configure(ChannelList.EMPTY,true,15,0,Direction.UP);tx.configureTransmission(ChannelList.of(71),16,false);rx.configure(ChannelList.of(71),true,15,0,Direction.DOWN);tx.onLoad();rx.onLoad();NativeRedstoneChannels.flush(world);
        player.setGameMode(GameType.SURVIVAL);player.connection.teleport(X,Y,Z,0,15);player.getInventory().clearContent();player.getInventory().selected=0;player.connection.send(new ClientboundSetCarriedItemPacket(0));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][wireless-gui] actor room server PASS air feet/head survival setup");
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){if(enabled()&&event.getEntity()==actor&&event.getContainer() instanceof WirelessMenu){opens++;require(actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"real menu must open in survival");if(opens==2)require(actor.isShiftKeyDown()&&actor.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()),"sneaking Configurizer route must open the same endpoint menu");}}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!enabled()||actor==null)return;var world=actor.serverLevel();require(world.getBlockEntity(TX)==tx&&world.getBlockEntity(RX)==rx,"settings replaced endpoint identity");if(!finished&&opens>=3&&!(actor.containerMenu instanceof WirelessMenu)&&rx.getRedstoneChannels().equals(ChannelList.of(75,76))&&!rx.analog()&&rx.onLevel()==9&&rx.offLevel()==4&&rx.port()==Direction.EAST){finished=true;actor.closeContainer();actor.setGameMode(GameType.CREATIVE);actor.getAbilities().mayfly=true;actor.getAbilities().flying=true;actor.onUpdateAbilities();actor.connection.teleport(VIEW_X,Y,VIEW_Z,0,15);actor.getInventory().clearContent();actor.getInventory().selected=8;actor.connection.send(new ClientboundSetCarriedItemPacket(8));actor.getInventory().setItem(0,new ItemStack(VandorLabsBootstrap.REDSTONE_TRANSMITTER_BLOCK_ITEM.get()));actor.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.REDSTONE_RECEIVER_BLOCK_ITEM.get()));actor.getInventory().setItem(2,preview(tx));actor.getInventory().setItem(3,preview(rx));actor.inventoryMenu.broadcastChanges();}}
    private static ItemStack preview(NativeWirelessRedstoneBlockEntity tile){var item=new ItemStack(tile.getBlockState().getBlock());var configuration=tile.configuration();require(configuration.getIntArray("RedstoneChannels").length==2&&!configuration.contains("WirelessInput")&&!configuration.contains("WirelessReceived"),"preview must use actual configuration, excluding live signal state");var root=new net.minecraft.nbt.CompoundTag();root.put("RedstoneChannelSettings",configuration);item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(root));return item;}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(!enabled()||event.getEntity()!=actor)return;require(opens>=3&&finished,"expected both Configurizer and ordinary endpoint menus opens="+opens);require(tx.transmissionChannels().equals(ChannelList.of(75,76))&&tx.transmissionRange()==64&&tx.localRedstone()&&!tx.analog()&&tx.port()==Direction.UP,"final immediate transmitter settings");require(rx.getRedstoneChannels().equals(ChannelList.of(75,76))&&!rx.analog()&&rx.onLevel()==9&&rx.offLevel()==4&&rx.port()==Direction.EAST&&rx.getOutputLevel()==9,"final immediate receiver settings and output");require(actor.serverLevel().getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),"real configured receiver did not light adjacent lamp");System.out.println("[vandorlabs][wireless-gui] server-client-actions PASS both endpoint identities real menus immediate port/analog/levels/channels and physical output opens="+opens);}
}
