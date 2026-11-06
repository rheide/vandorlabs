package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.client.NativeScreenScreen;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.client.*;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** Real held-item/mode/menu transport; block placement and world rendering remain separate focuses. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ScreenEditorPlayerChecks {
 private static int role,phase,ticks,lastWindow,initialLayout;private static NativeScreenScreen editor;
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)public static final class Binding {@SubscribeEvent public static void setup(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){if(ScreenEditorClientFixture.enabled())event.<NativeScreenMenu,NativeScreenScreen>register(ScreenEditorClientFixture.MENU,NativeScreenScreen::new);}}
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Screen editor client role="+role+" phase="+phase+": "+message);}
 private static void use(Minecraft mc,boolean sneak){mc.player.setShiftKeyDown(sneak);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,sneak?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ScreenEditorClientFixture.ROOT),Direction.NORTH,ScreenEditorClientFixture.ROOT,false));}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ScreenEditorClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gameMode==null)return;if(++ticks%200==0)System.out.println("[vandorlabs][screen-editor-client] wait role="+role+" phase="+phase+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" screen="+mc.screen+" pos="+mc.player.position()+" tile="+mc.level.getBlockEntity(ScreenEditorClientFixture.ROOT)+" data="+(mc.level.getBlockEntity(ScreenEditorClientFixture.ROOT) instanceof NativeScreenEntity observed?"fit="+observed.visual().frames().displayFit+" rows="+observed.controls(0).rows().size()+" channels="+observed.screenSettings().channels()+" layout="+observed.screenSettings().surfaceLayout():"absent"));need(ticks<1800,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" screen="+mc.screen+" pos="+mc.player.position());if(role==7){if(mc.level.getBlockState(ScreenEditorClientFixture.ACK).is(net.minecraft.world.level.block.Blocks.STONE)){System.out.println("[vandorlabs][screen-editor-client] client PASS roles=7 real vanilla tool/creative-sneak menus and command roundtrips");mc.stop();}return;}if(!(mc.level.getBlockEntity(ScreenEditorClientFixture.ROOT) instanceof NativeScreenEntity tile)||tile.family()!=ScreenFamily.values()[role])return;
 switch(phase){case 0->{if(mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.distanceToSqr(.5,94.88,-3)>.05)return;use(mc,false);phase=1;}
 case 1->{if(!(mc.screen instanceof NativeScreenScreen screen))return;initialLayout=tile.screenSettings().surfaceLayout();need(screen.wideArtwork()==(role==0||role==4),"actual authored wide picker availability for role "+role);editor=screen;lastWindow=mc.player.containerMenu.containerId;screen.action(111,1);screen.action(112,1);screen.action(590,1);screen.rowLabels().get(0).setValue("Toggle");screen.rowChannelFields().get(0).setValue("83101");screen.action(580,1);screen.action(93,1);screen.action(3006,1);screen.action(94,1);screen.channelField().setValue("83102");need(screen.applyDisplay(),"valid actual receiver draft");screen.action(0,1);screen.transmission().localControl().onPress();phase=2;}
 case 2->{if(tile.screenSettings().activation().data().mode!=1||!tile.transmission().local()||!tile.visual().frames().displayFit||tile.controls(0).rows().size()!=1||!tile.screenSettings().channels().equals(com.vandorlabs.redstone.ChannelList.of(83102)))return;PacketDistributor.sendToServer(new ScreenEditorClientFixture.Ack(role*2));phase=3;}
 case 3->{if(mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||!mc.player.getMainHandItem().isEmpty()||mc.player.getInventory().selected!=0)return;use(mc,true);phase=4;}
 case 4->{if(!(mc.screen instanceof NativeScreenScreen screen))return;need(mc.player.containerMenu.containerId!=lastWindow,"ordinary interaction creates fresh exact menu");editor=screen;PacketDistributor.sendToServer(new ScreenSettingsPayload(ScreenEditorClientFixture.ROOT,lastWindow,ScreenSettingsPayload.LAYOUT,0,new NativeScreenLayoutEdit((initialLayout+2)%3)));PacketDistributor.sendToServer(new ScreenEditorClientFixture.Ack(14+role));phase=6;}
 case 6->{if(!mc.level.getBlockState(ScreenEditorClientFixture.ACK).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK))return;editor.action(32,1);phase=5;}
 case 5->{if(tile.screenSettings().surfaceLayout()!=(initialLayout+1)%3)return;PacketDistributor.sendToServer(new ScreenEditorClientFixture.Ack(role*2+1));role++;phase=0;}
 }
 }
}
