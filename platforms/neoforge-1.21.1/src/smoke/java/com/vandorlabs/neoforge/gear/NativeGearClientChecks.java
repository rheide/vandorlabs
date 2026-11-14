package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.gear.client.*;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearClientChecks {
    private static int stage,ticks,checks,wait;private static int staleWindow;private static boolean shot;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native gear actual client stage="+stage+": "+why);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativeGearClientFixture.Mode(operation));}
    private static void use(Minecraft mc,BlockPos pos,Direction side){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5)),side,pos,false));}
    private static void click(NativeGearScreen screen,int id,int button){var control=screen.button(id);need(control!=null&&control.active&&screen.mouseClicked(control.getX()+control.getWidth()/2D,control.getY()+8,button),"actual widget="+id);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeGearClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==12&&mc.level==null){System.out.println("[vandorlabs][gear-client] client PASS checks="+checks+" actualSurvivalPlacement/consumption/Configurator/rootMenu/rightClick/boundedRoundtrip/manualReversal/stale/meshes");stage=13;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1400,"timeout ticks="+ticks+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" position="+mc.player.position()+" screen="+mc.screen);var tile=NativeGearFormation.at(mc.level,NativeGearClientFixture.ROOT);
        switch(stage){case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id))||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;use(mc,NativeGearClientFixture.SUPPORT,Direction.DOWN);stage=1;}case 1->{if(tile==null||!tile.controller()||mc.player.getMainHandItem().getCount()!=1)return;need(!tile.inert()&&NativeGearResources.bake()!=null,"authored atlas and synchronized controller");need(NativeGearRenderer.visible(tile),"one root renderer");mode(0);stage=2;}case 2->{if(!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativeGearClientFixture.ROOT,Direction.NORTH);stage=3;}case 3->{if(!(mc.screen instanceof NativeGearScreen screen))return;need(mc.getWindow().getGuiScaledHeight()>=218,"full control page fits");click(screen,1,1);need(screen.editingSettings().direction()==3,"right click chooses previous fold");click(screen,18,0);stage=4;}case 4->{if(tile.settings().direction()!=3||!tile.settings().middleHinge())return;click((NativeGearScreen)mc.screen,2,0);mc.player.closeContainer();stage=5;}case 5->{if(tile.progress()<=.4||tile.progress()>=.85||tile.target())return;mode(1);stage=6;}case 6->{if(!mc.player.getMainHandItem().isEmpty()||!mc.level.getBlockState(NativeGearClientFixture.ACK.east(1)).is(Blocks.STONE))return;use(mc,NativeGearClientFixture.ROOT,Direction.NORTH);stage=7;}case 7->{if(!tile.target()||tile.progress()!=1)return;mode(2);stage=8;}case 8->{if(!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativeGearClientFixture.ROOT,Direction.NORTH);stage=9;}case 9->{if(!(mc.screen instanceof NativeGearScreen screen))return;staleWindow=screen.getMenu().containerId;shot=true;wait=ticks;stage=10;}case 10->{if(ticks<wait+8||shot)return;mc.player.closeContainer();var choices=tile.settings().choices();choices.putInt("Direction",1);PacketDistributor.sendToServer(new NativeGearSettingsPayload(staleWindow,tile.getBlockPos(),NativeGearSettings.readChoices(choices),true,99));wait=ticks;stage=11;}case 11->{if(ticks<wait+10)return;need(tile.settings().direction()==3,"real stale packet rejected");mode(3);mode(4);mc.level.disconnect();mc.disconnect();stage=12;}default->{}}
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event)throws Exception{if(!NativeGearClientFixture.enabled()||!shot)return;var mc=Minecraft.getInstance();if(!(mc.screen instanceof NativeGearScreen))return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-gear-mechanism.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}shot=false;}
}
