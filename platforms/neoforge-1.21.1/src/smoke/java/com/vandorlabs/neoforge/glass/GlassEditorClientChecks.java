package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.client.NativeGlassScreen;
import com.vandorlabs.ui.GlassDialog;
import net.minecraft.client.*;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real vanilla use packets, dedicated screen widgets and authenticated edits. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class GlassEditorClientChecks {
 private static int stage,ticks,checks,window,wait;private static String capture;private static boolean captured;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new IllegalStateException("Glass editor client stage="+stage+" "+text);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Binding {@SubscribeEvent public static void screens(RegisterMenuScreensEvent e){if(GlassEditorClientFixture.enabled()&&!GlassEditorClientFixture.canonical())e.<NativeGlassMenu,NativeGlassScreen>register(GlassEditorClientFixture.MENU,NativeGlassScreen::new);}}
 private static void mode(int op){PacketDistributor.sendToServer(new GlassEditorClientFixture.Mode(op));}
 private static boolean ack(Minecraft mc,int index){return mc.level.getBlockState(GlassEditorClientFixture.ACK.east(index)).is(Blocks.STONE);}
 private static void use(Minecraft mc){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(GlassEditorClientFixture.ROOT),Direction.NORTH,GlassEditorClientFixture.ROOT,false));}
 private static void click(NativeGlassScreen s,int id,int button){var b=s.button(id);need(b!=null&&b.active,"active control="+id);need(s.mouseClicked(b.getX()+b.getWidth()/2D,b.getY()+b.getHeight()/2D,button),"real widget click="+id);}
 private static void attack(){PacketDistributor.sendToServer(new GlassSettingsPayload(GlassEditorClientFixture.ROOT,window,0,0,3,0,1,true,true,1,1));}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(!GlassEditorClientFixture.enabled()||stage==99)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}
  if(stage==90&&mc.level==null){System.out.println("[vandorlabs][glass-editor] client PASS checks="+checks+" realSurvivalTool/fourTabs/reverseOptions/frameTiling/pickerWheelDrag/typedPayload/removedTool/creativeSneak/staleWindow");stage=99;mc.stop();return;}
  if(mc.level==null||mc.player==null||mc.gameMode==null||mc.getOverlay()!=null)return;need(++ticks<1800,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen+" shift="+mc.player.isShiftKeyDown()+" key="+mc.options.keyShift.isDown());
  switch(stage){
   case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.position().distanceTo(new Vec3(.5,80,-3))>.15||!(mc.level.getBlockEntity(GlassEditorClientFixture.ROOT) instanceof NativeGlassEntity))return;if(GlassEditorClientFixture.canonical()){mc.options.keyShift.setDown(true);if(!mc.player.isShiftKeyDown())return;}use(mc);stage=1;}
   case 1->{if(!(mc.screen instanceof NativeGlassScreen s))return;window=s.getMenu().containerId;mc.options.keyShift.setDown(false);need(s.state().page==0&&s.button(GlassDialog.FRAMED)==null,"Source hidden primary bare control");click(s,GlassDialog.TINT,1);need(s.state().tint==0,"right-click previous tint");click(s,GlassDialog.TINT,0);click(s,GlassDialog.TINT,0);need(s.state().tint==2,"left cycles tint");click(s,GlassDialog.JOIN,1);need(!s.state().join,"reverse boolean join");s.choose(64);click(s,GlassDialog.TEXTURE_LAYOUT,1);need(s.state().layout==0,"reverse texture layout");click(s,GlassDialog.FIRST_PAGE+1,0);need(s.state().page==1&&s.button(3003)!=null&&s.button(3008)!=null,"dedicated Front frame controls");click(s,3003,1);need(s.frames().tile,"right-click frame tiling");click(s,GlassDialog.FIRST_PAGE+2,0);need(s.state().page==2,"Interior tab");click(s,GlassDialog.FIRST_PAGE+3,0);need(s.state().page==3,"Exterior tab");click(s,GlassDialog.FIRST_PAGE,0);
    var p=s.activePicker();p.searchField().setValue("a");int before=p.scroll();need(s.mouseScrolled(s.panel().listX+5,p.top()+5,0,-1)&&p.scroll()>before,"picker wheel");int bottom=p.top()+p.visible()*22-1;need(s.mouseClicked(s.panel().listX+GlassDialog.listWidth(s.panel())+2,bottom,0),"scrollbar press");int end=p.scroll();need(s.mouseDragged(s.panel().listX+GlassDialog.listWidth(s.panel())+2,p.top(),0,0,-100)&&p.scroll()<end,"scrollbar drag");s.mouseReleased(0,0,0);capture="glass-editor.png";captured=false;wait=0;stage=2;}
   case 2->{var owner=(NativeGlassEntity)mc.level.getBlockEntity(GlassEditorClientFixture.ROOT);if(!captured||owner.settings().glass().material!=64||owner.settings().glass().tint!=2||owner.settings().glass().join||owner.settings().glass().layout!=0||!owner.settings().frames().tile)return;mode(0);stage=3;}
   case 3->{if(!ack(mc,0)||!mc.player.getMainHandItem().isEmpty())return;attack();mode(1);stage=4;}
   case 4->{if(!ack(mc,1))return;need(((NativeGlassEntity)mc.level.getBlockEntity(GlassEditorClientFixture.ROOT)).settings().glass().tint==2,"removed tool refuses edit");mode(2);stage=5;}
   case 5->{if(!ack(mc,2)||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.screen!=null||!mc.player.getMainHandItem().isEmpty())return;mc.options.keyShift.setDown(true);if(!mc.player.isShiftKeyDown())return;use(mc);stage=6;}
   case 6->{if(!(mc.screen instanceof NativeGlassScreen))return;need(mc.player.containerMenu instanceof NativeGlassMenu,"ordinary click same dedicated menu");window=mc.player.containerMenu.containerId;mode(3);mc.options.keyShift.setDown(false);stage=7;}
   case 7->{if(!ack(mc,3)||mc.screen!=null)return;attack();mode(4);stage=8;}
   case 8->{if(!ack(mc,4))return;mode(5);wait=0;stage=9;}
   case 9->{if(++wait<3)return;mc.disconnect();stage=90;}
  }
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(!GlassEditorClientFixture.enabled()||capture==null||Minecraft.getInstance().getOverlay()!=null||++wait<12)return;var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);}
 private GlassEditorClientChecks(){}
}
