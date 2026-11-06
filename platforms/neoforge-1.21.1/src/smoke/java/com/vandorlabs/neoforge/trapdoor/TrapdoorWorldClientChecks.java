package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.client.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual canonical input, dedicated menu payloads, moved-leaf use/pick/mining and a BER-disabled GPU control. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class TrapdoorWorldClientChecks {
 private static int stage,ticks,wait,frames,checks;private static String capture;private static volatile boolean captured;private static java.lang.reflect.Field rendererField;private static Object saved;
 private static void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Canonical Trapdoor client stage="+stage+": "+why);}
 private static void call(Minecraft mc,String name){try{var method=Minecraft.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(mc);}catch(ReflectiveOperationException error){throw new IllegalStateException("Actual input "+name,error);}}
 private static void mode(int op){PacketDistributor.sendToServer(new TrapdoorWorldClientFixture.Mode(op));wait=ticks;}
 private static boolean actor(Minecraft mc,GameType type,Item item){return mc.gameMode.getPlayerMode()==type&&mc.player.getInventory().selected==0&&(item==Items.AIR?mc.player.getMainHandItem().isEmpty():mc.player.getMainHandItem().is(item))&&mc.player.getOffhandItem().isEmpty()&&Math.abs(mc.player.getZ()+2.8)<.04;}
 private static void click(NativeTrapdoorScreen screen,int id,int mouse){var b=screen.button(id);need(b!=null&&b.active&&screen.mouseClicked(b.getX()+b.getWidth()/2D,b.getY()+b.getHeight()/2D,mouse),"actual dedicated dialog button "+id);}
 private static void capture(String name){capture=name;captured=false;frames=0;}
 @SuppressWarnings("unchecked") private static void renderer(Minecraft mc,boolean enable){try{if(rendererField==null){rendererField=net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class.getDeclaredField("renderers");rendererField.setAccessible(true);}var dispatcher=mc.getBlockEntityRenderDispatcher();if(!enable){saved=rendererField.get(dispatcher);var copy=new HashMap<>((Map<Object,Object>)saved);copy.remove(NativeTrapdoorRegistrations.ENTITY.get());copy.remove(NativeTrapdoorRegistrations.DIAGONAL_ENTITY.get());rendererField.set(dispatcher,copy);}else{rendererField.set(dispatcher,saved);saved=null;}}catch(ReflectiveOperationException error){throw new IllegalStateException("Isolated renderer negative",error);}}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!TrapdoorWorldClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(stage==18&&mc.level==null){System.out.println("[vandorlabs][trapdoor-world-client] client PASS checks="+checks+" diagonal="+TrapdoorWorldClientFixture.diagonal()+" actual public survival placement/dedicated options+channel payload/moved use/configured pick/wood harvest/GPU disabled negative");mc.stop();stage=19;return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;
 need(++ticks<1800,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" hit="+mc.hitResult+" screen="+mc.screen);var tile=mc.level.getBlockEntity(TrapdoorWorldClientFixture.POS) instanceof NativeTrapdoorEntity t?t:null;
 switch(stage){
 case 0->{if(ticks<80||!actor(mc,GameType.SURVIVAL,TrapdoorWorldClientFixture.item())||mc.player.getMainHandItem().getCount()!=2)return;if(!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(TrapdoorWorldClientFixture.POS.below())||hit.getDirection()!=Direction.UP)return;call(mc,"startUseItem");stage=1;wait=ticks;}
 case 1->{if(tile==null||mc.player.getMainHandItem().getCount()!=1||ticks<wait+20)return;need(tile.diagonal()==TrapdoorWorldClientFixture.diagonal(),"canonical family");need(mc.getItemRenderer().getModel(mc.player.getMainHandItem(),mc.level,mc.player,0).getClass().getEnclosingClass()==com.vandorlabs.neoforge.NativeTrapdoorItemModel.class,"canonical authored item model wrapper");need(mc.getBlockEntityRenderDispatcher().getRenderer(tile) instanceof NativeTrapdoorBlockRenderer,"public renderer binding");mode(0);stage=2;}
 case 2->{if(!actor(mc,GameType.SURVIVAL,VandorLabsBootstrap.CONFIGURIZER.get())||ticks<wait+20||NativeDoorSelection.update(1)==null)return;call(mc,"startUseItem");stage=3;}
 case 3->{if(!(mc.screen instanceof NativeTrapdoorScreen s))return;need(s.getMenu().getType()==NativeTrapdoorRegistrations.MENU.get(),"public dedicated menu");click(s,6,0);need(s.settings().choices().layout()==2,"real layout option");click(s,101,0);click(s,3,1);need(s.settings().choices().trigger==0,"right-click previous trigger");var field=s.channelField();need(s.mouseClicked(field.getX()+8,field.getY()+8,0),"actual channel field focus");need(s.keyPressed(269,0,0)&&s.keyPressed(259,0,0),"actual End/Backspace clears initial zero");for(char c:"77731".toCharArray())need(s.charTyped(c,0),"actual channel keystroke");stage=4;wait=ticks;}
 case 4->{if(tile.trigger()!=0||tile.settings().choices().layout()!=2||!tile.settings().channels().contains(77731)||ticks<wait+20)return;click((NativeTrapdoorScreen)mc.screen,4,0);mode(1);stage=5;}
 case 5->{if(mc.screen!=null||!actor(mc,GameType.SURVIVAL,Items.AIR)||ticks<wait+20||NativeDoorSelection.update(1)==null)return;call(mc,"startUseItem");stage=6;wait=ticks;}
 case 6->{if(!tile.open()||ticks<wait+20)return;mode(2);stage=7;mc.options.fov().set(40);mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.hideGui=true;}
 case 7->{if(ticks<wait+40||NativeDoorSelection.update(1)==null)return;capture("canonical-trapdoor-positive.png");stage=8;}
 case 8->{if(!captured)return;renderer(mc,false);need(mc.getBlockEntityRenderDispatcher().getRenderer(tile)==null,"disabled renderer control");capture("canonical-trapdoor-negative.png");stage=9;}
 case 9->{if(!captured)return;renderer(mc,true);mc.options.hideGui=false;mc.options.fov().set(70);mode(3);stage=10;}
 case 10->{if(!actor(mc,GameType.CREATIVE,Items.AIR)||ticks<wait+25||NativeDoorSelection.update(1)==null)return;call(mc,"pickBlock");stage=11;wait=ticks;}
 case 11->{if(!actor(mc,GameType.CREATIVE,TrapdoorWorldClientFixture.item())||ticks<wait+20)return;var tag=NativeTrapdoorItem.configuration(mc.player.getMainHandItem());need(tag!=null&&tag.getInt("TrapdoorTextureLayout")==2&&tag.getIntArray("RedstoneChannels").length==1&&tag.getIntArray("RedstoneChannels")[0]==77731&&!tag.contains("NativeTrapdoorLinks")&&!tag.contains("NativeOpeningFluids"),"configured pick sanitized choices");capture("canonical-trapdoor-pick.png");stage=12;}
 case 12->{if(!captured)return;mode(4);stage=13;}
 case 13->{if(!actor(mc,GameType.SURVIVAL,Items.WOODEN_PICKAXE)||ticks<wait+25||NativeDoorSelection.update(1)==null)return;mc.options.keyAttack.setDown(true);call(mc,"startAttack");stage=14;wait=ticks;}
 case 14->{if(!mc.level.getBlockState(TrapdoorWorldClientFixture.POS).isAir())return;mc.options.keyAttack.setDown(false);wait=ticks;stage=15;}
 case 15->{if(ticks<wait+25)return;mode(5);stage=16;}
 case 16->{if(ticks<wait+30)return;mc.level.disconnect();mc.disconnect();stage=18;}
 }}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!TrapdoorWorldClientFixture.enabled()||capture==null||++frames<10)return;var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);}
}
